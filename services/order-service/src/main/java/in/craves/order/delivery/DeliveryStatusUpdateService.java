package in.craves.order.delivery;

import in.craves.order.delivery.DeliveryStatusEventValidator.DeliveryStatusValidationException;
import in.craves.order.delivery.DeliveryStatusModels.DeliveryStatusChangedData;
import in.craves.order.delivery.DeliveryStatusModels.EventEnvelope;
import in.craves.order.delivery.DeliveryStatusTransitionPolicy.Decision;
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
public class DeliveryStatusUpdateService {
    private static final Set<String> INELIGIBLE_ORDER_STATUSES = Set.of(
        "CHEF_REJECTED",
        "CANCELLED",
        "REFUND_PENDING",
        "REFUNDED",
        "REFUND_FAILED"
    );

    private final JdbcTemplate jdbcTemplate;
    private final DeliveryStatusEventValidator validator;
    private final DeliveryStatusTransitionPolicy transitionPolicy;
    private final DeliveryStatusCustomerNotificationService notificationService;

    public DeliveryStatusUpdateService(
        JdbcTemplate jdbcTemplate,
        DeliveryStatusEventValidator validator,
        DeliveryStatusTransitionPolicy transitionPolicy,
        DeliveryStatusCustomerNotificationService notificationService
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.validator = validator;
        this.transitionPolicy = transitionPolicy;
        this.notificationService = notificationService;
    }

    @Transactional
    public ProcessingResult accept(
        EventEnvelope<DeliveryStatusChangedData> event,
        String rawPayload
    ) {
        validator.validate(event);
        DeliveryStatusChangedData data = event.data();

        int inboxInserted = jdbcTemplate.update(
            """
                INSERT INTO order_schema.delivery_status_inbox (
                    event_id, event_type, event_version, correlation_id,
                    causation_id, subject, delivery_job_id, chef_sub_order_id,
                    provider_id, provider_delivery_id, normalized_status,
                    observed_at, payload, processing_status, received_at
                ) VALUES (
                    ?, ?, ?, ?,
                    ?, ?, ?, ?,
                    ?, ?, ?,
                    ?, CAST(? AS jsonb), 'RECEIVED', now()
                )
                ON CONFLICT (event_id) DO NOTHING
                """,
            event.eventId(),
            event.eventType(),
            event.eventVersion(),
            event.correlationId(),
            event.causationId(),
            event.subject(),
            data.deliveryJobId(),
            data.chefSubOrderId(),
            data.providerId(),
            data.providerDeliveryId(),
            data.status(),
            Timestamp.from(data.observedAt()),
            rawPayload
        );
        if (inboxInserted == 0) {
            return new ProcessingResult(false, true, "DUPLICATE_EVENT");
        }

        LockedOrder order = lockOrder(data.chefSubOrderId());
        validateOrder(order, data);

        Decision decision = transitionPolicy.decide(
            order.deliveryStatus(),
            order.deliveryTrackingUrl(),
            order.deliveryStatusObservedAt(),
            data.status(),
            data.trackingUrl(),
            data.observedAt()
        );

        if (decision != Decision.APPLY) {
            markInbox(event.eventId(), decision.name());
            return new ProcessingResult(false, false, decision.name());
        }

        int updated = jdbcTemplate.update(
            """
                UPDATE order_schema.customer_order
                SET delivery_job_id = ?,
                    delivery_provider_id = ?,
                    delivery_provider_delivery_id = ?,
                    delivery_status = ?,
                    delivery_tracking_url = ?,
                    delivery_status_observed_at = ?,
                    delivery_status_event_id = ?,
                    updated_at = now()
                WHERE id = ?
                """,
            data.deliveryJobId(),
            data.providerId(),
            data.providerDeliveryId(),
            data.status(),
            data.trackingUrl(),
            Timestamp.from(data.observedAt()),
            event.eventId(),
            data.chefSubOrderId()
        );
        if (updated != 1) {
            throw new DeliveryStatusRetryableException("Order delivery projection update was not applied");
        }

        jdbcTemplate.update(
            """
                INSERT INTO order_schema.order_delivery_status_history (
                    id, order_id, delivery_job_id, event_id,
                    old_status, new_status, provider_id, provider_delivery_id,
                    tracking_url, observed_at, source, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
                """,
            UUID.randomUUID(),
            data.chefSubOrderId(),
            data.deliveryJobId(),
            event.eventId(),
            order.deliveryStatus(),
            data.status(),
            data.providerId(),
            data.providerDeliveryId(),
            data.trackingUrl(),
            Timestamp.from(data.observedAt()),
            event.source()
        );

        boolean statusChanged = !data.status().equals(order.deliveryStatus());
        if (statusChanged) {
            notificationService.record(
                event.eventId(),
                order.checkoutId(),
                order.customerIdentityId(),
                data
            );
        }

        markInbox(event.eventId(), "PROCESSED");
        return new ProcessingResult(true, false, "PROCESSED");
    }

    private LockedOrder lockOrder(UUID orderId) {
        return jdbcTemplate.query(
            """
                SELECT id, checkout_id, customer_identity_id, status,
                       accepted_at, refund_requested_at,
                       delivery_job_id, delivery_provider_id,
                       delivery_provider_delivery_id, delivery_status,
                       delivery_tracking_url, delivery_status_observed_at
                FROM order_schema.customer_order
                WHERE id = ?
                FOR UPDATE
                """,
            this::mapLockedOrder,
            orderId
        ).stream().findFirst().orElseThrow(() -> new DeliveryStatusRetryableException(
            "Chef sub-order is not available yet"
        ));
    }

    private void validateOrder(LockedOrder order, DeliveryStatusChangedData data) {
        if (!order.checkoutId().equals(data.orderId())) {
            throw new DeliveryStatusNonRetryableException(
                "Delivery checkout does not match the chef sub-order"
            );
        }
        if (order.acceptedAt() == null) {
            throw new DeliveryStatusRetryableException(
                "Chef acceptance metadata is not available yet"
            );
        }
        if (order.refundRequestedAt() != null || INELIGIBLE_ORDER_STATUSES.contains(order.orderStatus())) {
            throw new DeliveryStatusNonRetryableException(
                "Order is not eligible for delivery status updates"
            );
        }
        if (order.deliveryJobId() != null && !order.deliveryJobId().equals(data.deliveryJobId())) {
            throw new DeliveryStatusNonRetryableException(
                "Delivery job identifier changed for the chef sub-order"
            );
        }
        if (order.deliveryProviderId() != null
            && !order.deliveryProviderId().equalsIgnoreCase(data.providerId())) {
            throw new DeliveryStatusNonRetryableException(
                "Delivery provider changed for the chef sub-order"
            );
        }
        if (order.deliveryProviderDeliveryId() != null
            && !order.deliveryProviderDeliveryId().equals(data.providerDeliveryId())) {
            throw new DeliveryStatusNonRetryableException(
                "Provider delivery identifier changed for the chef sub-order"
            );
        }
    }

    private void markInbox(UUID eventId, String status) {
        jdbcTemplate.update(
            """
                UPDATE order_schema.delivery_status_inbox
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
            instant(resultSet, "accepted_at"),
            instant(resultSet, "refund_requested_at"),
            resultSet.getObject("delivery_job_id", UUID.class),
            resultSet.getString("delivery_provider_id"),
            resultSet.getString("delivery_provider_delivery_id"),
            resultSet.getString("delivery_status"),
            resultSet.getString("delivery_tracking_url"),
            instant(resultSet, "delivery_status_observed_at")
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
        String orderStatus,
        Instant acceptedAt,
        Instant refundRequestedAt,
        UUID deliveryJobId,
        String deliveryProviderId,
        String deliveryProviderDeliveryId,
        String deliveryStatus,
        String deliveryTrackingUrl,
        Instant deliveryStatusObservedAt
    ) {
    }

    public record ProcessingResult(boolean applied, boolean duplicate, String result) {
    }

    public static class DeliveryStatusRetryableException extends RuntimeException {
        public DeliveryStatusRetryableException(String message) {
            super(message);
        }
    }

    public static class DeliveryStatusNonRetryableException extends DeliveryStatusValidationException {
        public DeliveryStatusNonRetryableException(String message) {
            super(message);
        }
    }
}
