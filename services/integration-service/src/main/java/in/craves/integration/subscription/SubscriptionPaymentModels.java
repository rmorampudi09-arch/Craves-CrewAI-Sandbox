package in.craves.integration.subscription;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class SubscriptionPaymentModels {
    public static final String PAYMENT_REQUESTED = "SUBSCRIPTION_PAYMENT_REQUESTED";
    public static final String PAYMENT_STATUS_CHANGED = "SUBSCRIPTION_PAYMENT_STATUS_CHANGED";

    private SubscriptionPaymentModels() {
    }

    public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        String eventVersion,
        Instant occurredAt,
        UUID correlationId,
        UUID causationId,
        UUID subject,
        T data
    ) {
    }

    public record PaymentRequestedData(
        UUID invoiceId,
        UUID subscriptionId,
        UUID planId,
        UUID customerIdentityId,
        UUID chefIdentityId,
        LocalDate cycleStart,
        LocalDate cycleEnd,
        BigDecimal amount,
        String currency
    ) {
    }

    public record CreateSubscriptionPaymentOrderRequest(
        @NotBlank @Size(max = 120) String customerName,
        @NotBlank @Size(max = 20) String customerPhone,
        @Email @Size(max = 254) String customerEmail,
        @Size(max = 500) String returnUrl
    ) {
    }

    public record VerifySubscriptionPaymentRequest(
        String providerOrderId,
        String providerPaymentId,
        String providerSignature
    ) {
    }

    public record SubscriptionPaymentResponse(
        UUID id,
        UUID invoiceId,
        UUID subscriptionId,
        LocalDate cycleStart,
        LocalDate cycleEnd,
        BigDecimal amount,
        String currency,
        String status,
        String paymentSessionId,
        String providerStatus,
        Instant createdAt,
        Instant updatedAt,
        Instant paidAt,
        String provider,
        String providerOrderId,
        String providerPaymentId,
        String checkoutKeyId
    ) {
        public SubscriptionPaymentResponse(
            UUID id, UUID invoiceId, UUID subscriptionId, LocalDate cycleStart, LocalDate cycleEnd,
            BigDecimal amount, String currency, String status, String paymentSessionId,
            String providerStatus, Instant createdAt, Instant updatedAt, Instant paidAt
        ) {
            this(id, invoiceId, subscriptionId, cycleStart, cycleEnd, amount, currency, status,
                paymentSessionId, providerStatus, createdAt, updatedAt, paidAt,
                "CASHFREE", null, null, null);
        }
    }

    public record StatusChangedData(
        UUID paymentIntentId,
        UUID invoiceId,
        UUID subscriptionId,
        String status,
        String providerStatus,
        String providerPaymentId,
        BigDecimal amount,
        String currency,
        Instant changedAt
    ) {
    }
}
