package in.craves.order.refund;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.craves.order.refund.RefundStatusModels.EventEnvelope;
import in.craves.order.refund.RefundStatusModels.RefundStatusChangedData;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefundStatusEventValidatorTest {
    private final RefundStatusEventValidator validator = new RefundStatusEventValidator();

    @Test
    void acceptsValidRefundedEvent() {
        assertThatCode(() -> validator.validate(validEvent("REFUNDED", "SUCCESS")))
            .doesNotThrowAnyException();
    }

    @Test
    void rejectsProviderStatusThatDoesNotMatchNormalizedStatus() {
        assertThatThrownBy(() -> validator.validate(validEvent("REFUNDED", "PENDING")))
            .isInstanceOf(RefundStatusEventValidator.RefundStatusValidationException.class)
            .hasMessageContaining("Provider status");
    }

    @Test
    void rejectsSubjectThatDoesNotMatchChefSubOrder() {
        EventEnvelope<RefundStatusChangedData> event = validEvent("REFUND_PENDING", "PENDING");
        EventEnvelope<RefundStatusChangedData> invalid = new EventEnvelope<>(
            event.eventId(),
            event.eventType(),
            event.eventVersion(),
            event.occurredAt(),
            event.correlationId(),
            event.causationId(),
            event.source(),
            UUID.randomUUID().toString(),
            event.data()
        );

        assertThatThrownBy(() -> validator.validate(invalid))
            .isInstanceOf(RefundStatusEventValidator.RefundStatusValidationException.class)
            .hasMessageContaining("subject");
    }

    private static EventEnvelope<RefundStatusChangedData> validEvent(
        String status,
        String providerStatus
    ) {
        UUID checkoutId = UUID.randomUUID();
        UUID chefSubOrderId = UUID.randomUUID();
        Instant now = Instant.parse("2026-07-16T12:00:00Z");
        RefundStatusChangedData data = new RefundStatusChangedData(
            UUID.randomUUID(),
            checkoutId,
            chefSubOrderId,
            UUID.randomUUID(),
            "CRV12345678901234567890123456789012",
            new BigDecimal("220.00"),
            "INR",
            "CHEF_ACCEPTANCE_TIMEOUT",
            status,
            providerStatus,
            "REFUND-123",
            now
        );
        return new EventEnvelope<>(
            UUID.randomUUID(),
            "REFUND_STATUS_CHANGED",
            "1.0",
            now,
            checkoutId,
            UUID.randomUUID(),
            "integration-service",
            chefSubOrderId.toString(),
            data
        );
    }
}
