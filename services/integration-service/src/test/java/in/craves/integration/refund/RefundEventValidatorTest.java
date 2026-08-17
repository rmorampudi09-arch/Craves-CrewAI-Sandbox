package in.craves.integration.refund;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.craves.integration.refund.RefundEventValidator.RefundMessageValidationException;
import in.craves.integration.refund.RefundModels.EventEnvelope;
import in.craves.integration.refund.RefundModels.RefundRequestedData;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefundEventValidatorTest {
    private final RefundEventValidator validator = new RefundEventValidator();

    @Test
    void acceptsValidRefundRequest() {
        EventEnvelope<RefundRequestedData> event = validEvent();
        assertThatCode(() -> validator.validate(event)).doesNotThrowAnyException();
    }

    @Test
    void rejectsSubjectMismatch() {
        EventEnvelope<RefundRequestedData> valid = validEvent();
        EventEnvelope<RefundRequestedData> invalid = new EventEnvelope<>(
            valid.eventId(),
            valid.eventType(),
            valid.eventVersion(),
            valid.occurredAt(),
            valid.correlationId(),
            valid.causationId(),
            valid.source(),
            UUID.randomUUID().toString(),
            valid.data()
        );

        assertThatThrownBy(() -> validator.validate(invalid))
            .isInstanceOf(RefundMessageValidationException.class)
            .hasMessageContaining("subject");
    }

    @Test
    void rejectsAmountAboveZeroRequirement() {
        EventEnvelope<RefundRequestedData> valid = validEvent();
        RefundRequestedData invalidData = new RefundRequestedData(
            valid.data().checkoutId(),
            valid.data().chefSubOrderId(),
            valid.data().customerIdentityId(),
            BigDecimal.ZERO,
            "INR",
            "CHEF_DECLINED",
            Instant.now()
        );
        EventEnvelope<RefundRequestedData> invalid = new EventEnvelope<>(
            valid.eventId(), valid.eventType(), valid.eventVersion(), valid.occurredAt(),
            valid.correlationId(), valid.causationId(), valid.source(), valid.subject(), invalidData
        );

        assertThatThrownBy(() -> validator.validate(invalid))
            .isInstanceOf(RefundMessageValidationException.class)
            .hasMessageContaining("positive");
    }

    private static EventEnvelope<RefundRequestedData> validEvent() {
        UUID checkoutId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        RefundRequestedData data = new RefundRequestedData(
            checkoutId,
            orderId,
            UUID.randomUUID(),
            new BigDecimal("220.00"),
            "INR",
            "CHEF_ACCEPTANCE_TIMEOUT",
            Instant.parse("2026-07-16T13:30:00Z")
        );
        return new EventEnvelope<>(
            UUID.randomUUID(),
            "REFUND_REQUESTED",
            "1.0",
            Instant.parse("2026-07-16T13:30:00Z"),
            checkoutId,
            UUID.randomUUID(),
            "order-service",
            orderId.toString(),
            data
        );
    }
}
