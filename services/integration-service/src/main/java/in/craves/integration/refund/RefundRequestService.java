package in.craves.integration.refund;

import in.craves.integration.refund.RefundModels.EventEnvelope;
import in.craves.integration.refund.RefundModels.RefundRequestedData;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RefundRequestService {
    private final JdbcTemplate jdbcTemplate;
    private final RefundEventValidator validator;

    public RefundRequestService(JdbcTemplate jdbcTemplate, RefundEventValidator validator) {
        this.jdbcTemplate = jdbcTemplate;
        this.validator = validator;
    }

    @Transactional
    public boolean accept(EventEnvelope<RefundRequestedData> event, String rawPayload) {
        validator.validate(event);

        int inboxInserted = jdbcTemplate.update(
            """
                INSERT INTO payment_schema.refund_request_inbox (
                    event_id, event_type, event_version, correlation_id,
                    causation_id, subject, payload, processing_status,
                    received_at
                ) VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), 'RECEIVED', now())
                ON CONFLICT (event_id) DO NOTHING
                """,
            event.eventId(),
            event.eventType(),
            event.eventVersion(),
            event.correlationId(),
            event.causationId(),
            UUID.fromString(event.subject()),
            rawPayload
        );
        if (inboxInserted == 0) {
            return false;
        }

        RefundRequestedData data = event.data();
        PaymentOrder paymentOrder = lockPaidPaymentOrder(data.checkoutId());
        if (refundExists(data.chefSubOrderId())) {
            markInboxDuplicate(event.eventId());
            return false;
        }

        BigDecimal requestedAmount = data.refundAmount().setScale(2, RoundingMode.HALF_UP);
        validateFinancialBounds(paymentOrder, data, requestedAmount);

        String refundReference = refundReference(data.chefSubOrderId());
        UUID idempotencyKey = deterministicIdempotencyKey(data.chefSubOrderId());
        UUID refundId = UUID.randomUUID();

        int inserted = jdbcTemplate.update(
            """
                INSERT INTO payment_schema.refund (
                    id, payment_order_id, refund_ref, amount, currency,
                    status, reason, provider_payload, created_at, updated_at,
                    checkout_id, chef_sub_order_id, customer_identity_id,
                    provider, provider_order_id, provider_payment_id, cashfree_order_id, idempotency_key, request_event_id,
                    request_event_payload, requested_at, next_attempt_at
                ) VALUES (
                    ?, ?, ?, ?, ?,
                    'REQUESTED', ?, '{}'::jsonb, now(), now(),
                    ?, ?, ?,
                    ?, ?, ?, ?, ?, ?,
                    CAST(? AS jsonb), ?, now()
                )
                ON CONFLICT DO NOTHING
                """,
            refundId,
            paymentOrder.paymentOrderId(),
            refundReference,
            requestedAmount,
            data.currency(),
            data.reason(),
            data.checkoutId(),
            data.chefSubOrderId(),
            data.customerIdentityId(),
            paymentOrder.provider(),
            paymentOrder.providerOrderId(),
            paymentOrder.providerPaymentId(),
            "CASHFREE".equals(paymentOrder.provider()) ? paymentOrder.providerOrderId() : null,
            idempotencyKey,
            event.eventId(),
            rawPayload,
            Timestamp.from(data.requestedAt())
        );

        if (inserted == 0) {
            markInboxDuplicate(event.eventId());
            return false;
        }

        jdbcTemplate.update(
            "UPDATE payment_schema.refund_request_inbox SET processing_status = 'PROCESSED', processed_at = now() WHERE event_id = ?",
            event.eventId()
        );
        return true;
    }

    private boolean refundExists(UUID chefSubOrderId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM payment_schema.refund WHERE chef_sub_order_id = ?",
            Integer.class,
            chefSubOrderId
        );
        return count != null && count > 0;
    }

    private void markInboxDuplicate(UUID eventId) {
        jdbcTemplate.update(
            "UPDATE payment_schema.refund_request_inbox SET processing_status = 'DUPLICATE', processed_at = now() WHERE event_id = ?",
            eventId
        );
    }

    private PaymentOrder lockPaidPaymentOrder(UUID checkoutId) {
        return jdbcTemplate.query(
            """
                SELECT id, amount, currency, status, provider, provider_order_id, provider_payment_id
                FROM payment_schema.payment_order
                WHERE checkout_id = ?
                ORDER BY created_at DESC
                LIMIT 1
                FOR UPDATE
                """,
            (resultSet, rowNumber) -> new PaymentOrder(
                resultSet.getObject("id", UUID.class),
                resultSet.getBigDecimal("amount"),
                resultSet.getString("currency"),
                resultSet.getString("status"),
                resultSet.getString("provider"),
                resultSet.getString("provider_order_id"),
                resultSet.getString("provider_payment_id")
            ),
            checkoutId
        ).stream().findFirst().orElseThrow(() -> new RefundRetryableException(
            "Payment order is not available for the checkout"
        ));
    }

    private void validateFinancialBounds(
        PaymentOrder paymentOrder,
        RefundRequestedData data,
        BigDecimal requestedAmount
    ) {
        if (!"PAID".equals(paymentOrder.status())) {
            throw new RefundNonRetryableException("Only a paid checkout can be refunded");
        }
        if (!StringUtils.hasText(paymentOrder.providerOrderId())) {
            throw new RefundRetryableException("Payment provider order identifier is not available");
        }
        if ("RAZORPAY".equals(paymentOrder.provider()) && !StringUtils.hasText(paymentOrder.providerPaymentId())) {
            throw new RefundRetryableException("Razorpay captured payment identifier is not available");
        }
        if (!data.currency().equals(paymentOrder.currency())) {
            throw new RefundNonRetryableException("Refund currency does not match the payment currency");
        }
        if (requestedAmount.compareTo(paymentOrder.amount()) > 0) {
            throw new RefundNonRetryableException("Refund amount exceeds the captured payment amount");
        }

        BigDecimal alreadyReserved = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(amount), 0) FROM payment_schema.refund WHERE payment_order_id = ?",
            BigDecimal.class,
            paymentOrder.paymentOrderId()
        );
        BigDecimal reserved = alreadyReserved == null ? BigDecimal.ZERO : alreadyReserved;
        if (reserved.add(requestedAmount).compareTo(paymentOrder.amount()) > 0) {
            throw new RefundNonRetryableException("Cumulative refund amount exceeds the captured payment amount");
        }
    }

    static String refundReference(UUID chefSubOrderId) {
        return "CRV" + chefSubOrderId.toString().replace("-", "");
    }

    static UUID deterministicIdempotencyKey(UUID chefSubOrderId) {
        return UUID.nameUUIDFromBytes(
            ("craves-refund:" + chefSubOrderId).getBytes(StandardCharsets.UTF_8)
        );
    }

    private record PaymentOrder(
        UUID paymentOrderId,
        BigDecimal amount,
        String currency,
        String status,
        String provider,
        String providerOrderId,
        String providerPaymentId
    ) {
    }

    public static class RefundRetryableException extends RuntimeException {
        public RefundRetryableException(String message) {
            super(message);
        }
    }

    public static class RefundNonRetryableException extends RuntimeException {
        public RefundNonRetryableException(String message) {
            super(message);
        }
    }
}
