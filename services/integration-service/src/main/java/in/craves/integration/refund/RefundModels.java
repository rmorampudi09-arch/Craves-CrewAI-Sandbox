package in.craves.integration.refund;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class RefundModels {
    private RefundModels() {
    }

    public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        String eventVersion,
        Instant occurredAt,
        UUID correlationId,
        UUID causationId,
        String source,
        String subject,
        T data
    ) {
    }

    public record RefundRequestedData(
        UUID checkoutId,
        UUID chefSubOrderId,
        UUID customerIdentityId,
        BigDecimal refundAmount,
        String currency,
        String reason,
        Instant requestedAt
    ) {
    }

    public record RefundWorkItem(
        UUID refundId,
        UUID paymentOrderId,
        UUID checkoutId,
        UUID chefSubOrderId,
        UUID customerIdentityId,
        UUID requestEventId,
        String cashfreeOrderId,
        String refundReference,
        UUID idempotencyKey,
        BigDecimal amount,
        String currency,
        String reason,
        String status,
        String providerStatus,
        String cfRefundId,
        int attemptCount,
        UUID lockToken,
        String provider,
        String providerOrderId,
        String providerPaymentId,
        String providerRefundId
    ) {
        public RefundWorkItem(
            UUID refundId, UUID paymentOrderId, UUID checkoutId, UUID chefSubOrderId,
            UUID customerIdentityId, UUID requestEventId, String cashfreeOrderId,
            String refundReference, UUID idempotencyKey, BigDecimal amount, String currency,
            String reason, String status, String providerStatus, String cfRefundId,
            int attemptCount, UUID lockToken
        ) {
            this(refundId, paymentOrderId, checkoutId, chefSubOrderId, customerIdentityId,
                requestEventId, cashfreeOrderId, refundReference, idempotencyKey, amount, currency,
                reason, status, providerStatus, cfRefundId, attemptCount, lockToken,
                "CASHFREE", cashfreeOrderId, null, cfRefundId);
        }
    }

    public record ProviderRefundResult(
        String providerStatus,
        String cfRefundId,
        String providerPayload
    ) {
    }
}
