package in.craves.order.refund;

import in.craves.order.refund.RefundStatusEventValidator.RefundStatusValidationException;
import in.craves.order.refund.RefundStatusModels.EventEnvelope;
import in.craves.order.refund.RefundStatusModels.RefundStatusChangedData;
import in.craves.order.refund.RefundStatusTransitionPolicy.Decision;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefundStatusUpdateService {
    private static final Set<String> REFUND_ELIGIBLE_ORDER_STATUSES = Set.of(
        "CHEF_REJECTED",
        "REFUND_PENDING",
        "REFUNDED",
        "REFUND_FAILED"
    );

    private final JdbcTemplate jdbcTemplate;
    private final RefundStatusEventValidator validator;
    private final RefundStatusTransitionPolicy transitionPolicy;
    private final RefundStatusCustomerNotificationService customerNotificationService;

    public RefundStatusUpdateService(
        JdbcTemplate jdbcTemplate,
        RefundStatusEventValidator validator,
        RefundStatusTransitionPolicy transitionPolicy,
        RefundStatusCustomerNotificationService customerNotificationService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.validator = validator;
        this.transitionPolicy = transitionPolicy;
        this.customerNotificationService = customerNotificationService;
    }

    @Transactional
    public boolean accept(EventEnvelope<RefundStatusChangedData> event, String rawPayload) {
        validator.validate(event);

        int inboxInserted = jdbcTemplate.update(
            """
                INSERT INTO order_schema.refund_status_inbox (
                    event_id, event_type, event_version, correlation_id,
                    causation_id, subject, refund_id, normalized_status,
                    provider_status, payload, processing_status, received_at
                ) VALUES (
                    ?, ?, ?, ?,
                    ?, ?, ?, ?,
                    ?, CAST(? AS jsonb), 'RECEIVED', now()
                )
                ON CONFLICT (event_id) DO NOTHING
                """,
            event.eventId(),
            event.eventType(),
            event.eventVersion(),
            event.correlationId(),
            event.causationId(),
            UUID.fromString(event.subject()),
            event.data().refundId(),
            event.data().status(),
            event.data().providerStatus(),
            rawPayload
        );
        if (inboxInserted == 0) {
            return false;
        }

        RefundStatusChangedData data = event.data();
        LockedOrder order = lockOrder(data.chefSubOrderId());
        validateOrder(order, data);

        Decision decision = transitionPolicy.decide(
            order.status(),
            order.refundStatusUpdatedAt(),
            data.status(),
            data.updatedAt()
        );
        if (decision == Decision.STALE) {
            markInbox(event.eventId(), "STALE");
            return false;
        }

        int updated = jdbcTemplate.update(
            """
                UPDATE order_schema.customer_order
                SET status = ?,
                    refund_id = ?,
                    refund_reference = ?,
                    refund_provider = ?,
                    refund_provider_status = ?,
                    provider_refund_id = ?,
                    cf_refund_id = ?,
                    refund_status_event_id = ?,
                    refund_status_updated_at = ?,
                    refund_completed_at = CASE
                        WHEN ? = 'REFUNDED' THEN ?
                        ELSE refund_completed_at
                    END,
                    refund_failed_at = CASE
                        WHEN ? = 'REFUND_FAILED' THEN ?
                        ELSE refund_failed_at
                    END,
                    updated_at = now()
                WHERE id = ?
                """,
            data.status(),
            data.refundId(),
            data.refundReference(),
            data.provider(),
            data.providerStatus(),
            data.providerRefundId(),
            data.cfRefundId(),
            event.eventId(),
            Timestamp.from(data.updatedAt()),
            data.status(),
            Timestamp.from(data.updatedAt()),
            data.status(),
            Timestamp.from(data.updatedAt()),
            data.chefSubOrderId()
        );
        if (updated != 1) {
            throw new RefundStatusRetryableException("Order update was not applied");
        }

        boolean statusChanged = !data.status().equals(order.status());
        if (statusChanged) {
            jdbcTemplate.update(
                """
                    INSERT INTO order_schema.order_status_history (
                        id, order_id, old_status, new_status,
                        actor_identity_id, reason, created_at
                    ) VALUES (?, ?, ?, ?, NULL, ?, now())
                    """,
                UUID.randomUUID(),
                data.chefSubOrderId(),
                order.status(),
                data.status(),
                "Refund provider status: " + data.providerStatus()
            );

            customerNotificationService.record(
                event.eventId(),
                order.checkoutId(),
                order.customerIdentityId(),
                data
            );
        }

        markInbox(event.eventId(), "PROCESSED");
        return true;
    }

    private LockedOrder lockOrder(UUID orderId) {
        return jdbcTemplate.query(
            """
                SELECT id, checkout_id, customer_identity_id, status, currency,
                       chef_rejection_code, refund_requested_at, refund_requested_amount,
                       refund_id, refund_reference, refund_status_updated_at
                FROM order_schema.customer_order
                WHERE id = ?
                FOR UPDATE
                """,
            this::mapLockedOrder,
            orderId
        ).stream().findFirst().orElseThrow(() -> new RefundStatusRetryableException(
            "Chef sub-order is not available yet"
        ));
    }

    private void validateOrder(LockedOrder order, RefundStatusChangedData data) {
        if (!order.checkoutId().equals(data.checkoutId())) {
            throw new RefundStatusNonRetryableException("Refund checkout does not match the order");
        }
        if (!order.customerIdentityId().equals(data.customerIdentityId())) {
            throw new RefundStatusNonRetryableException("Refund customer does not match the order");
        }
        if (!REFUND_ELIGIBLE_ORDER_STATUSES.contains(order.status())) {
            throw new RefundStatusNonRetryableException("Order is not in a refund-eligible state");
        }
        if (order.refundRequestedAt() == null || order.refundRequestedAmount() == null) {
            throw new RefundStatusRetryableException("Order refund request metadata is not available yet");
        }
        if (!data.currency().equals(order.currency())) {
            throw new RefundStatusNonRetryableException("Refund currency does not match the order");
        }
        if (!data.reason().equals(order.rejectionCode())) {
            throw new RefundStatusNonRetryableException("Refund reason does not match the order rejection");
        }

        BigDecimal expectedAmount = order.refundRequestedAmount().setScale(2, RoundingMode.HALF_UP);
        BigDecimal incomingAmount = data.refundAmount().setScale(2, RoundingMode.HALF_UP);
        if (expectedAmount.compareTo(incomingAmount) != 0) {
            throw new RefundStatusNonRetryableException("Refund amount does not match the requested amount");
        }
        if (order.refundId() != null && !order.refundId().equals(data.refundId())) {
            throw new RefundStatusNonRetryableException("Refund identifier changed for the order");
        }
        if (order.refundReference() != null && !order.refundReference().equals(data.refundReference())) {
            throw new RefundStatusNonRetryableException("Refund reference changed for the order");
        }
    }

    private void markInbox(UUID eventId, String status) {
        jdbcTemplate.update(
            """
                UPDATE order_schema.refund_status_inbox
                SET processing_status = ?, processed_at = now()
                WHERE event_id = ?
                """,
            status,
            eventId
        );
    }

    private LockedOrder mapLockedOrder(ResultSet resultSet, int rowNumber) throws SQLException {
        return new LockedOrder(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("checkout_id", UUID.class),
            resultSet.getObject("customer_identity_id", UUID.class),
            resultSet.getString("status"),
            resultSet.getString("currency"),
            resultSet.getString("chef_rejection_code"),
            instant(resultSet, "refund_requested_at"),
            resultSet.getBigDecimal("refund_requested_amount"),
            resultSet.getObject("refund_id", UUID.class),
            resultSet.getString("refund_reference"),
            instant(resultSet, "refund_status_updated_at")
        );
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record LockedOrder(
        UUID id,
        UUID checkoutId,
        UUID customerIdentityId,
        String status,
        String currency,
        String rejectionCode,
        Instant refundRequestedAt,
        BigDecimal refundRequestedAmount,
        UUID refundId,
        String refundReference,
        Instant refundStatusUpdatedAt
    ) {
    }

    public static class RefundStatusRetryableException extends RuntimeException {
        public RefundStatusRetryableException(String message) {
            super(message);
        }
    }

    public static class RefundStatusNonRetryableException extends RefundStatusValidationException {
        public RefundStatusNonRetryableException(String message) {
            super(message);
        }
    }
}
