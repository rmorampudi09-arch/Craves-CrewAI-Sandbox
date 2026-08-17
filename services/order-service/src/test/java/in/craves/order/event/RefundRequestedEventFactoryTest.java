package in.craves.order.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefundRequestedEventFactoryTest {
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final RefundRequestedEventFactory factory = new RefundRequestedEventFactory(objectMapper);

    @Test
    void createsVersionedRefundRequestedEventWithIsoTimestamps() throws Exception {
        UUID checkoutId = UUID.randomUUID();
        UUID chefSubOrderId = UUID.randomUUID();
        UUID customerIdentityId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        Instant requestedAt = Instant.parse("2026-07-16T12:30:00Z");

        SerializedDomainEvent event = factory.create(
            new RefundRequestedEventSource(
                checkoutId,
                chefSubOrderId,
                customerIdentityId,
                new BigDecimal("220.00"),
                "inr",
                "CHEF_ACCEPTANCE_TIMEOUT",
                requestedAt
            ),
            correlationId,
            "timeout:" + chefSubOrderId
        );

        JsonNode json = objectMapper.readTree(event.payloadJson());
        assertThat(event.eventType()).isEqualTo("REFUND_REQUESTED");
        assertThat(event.eventVersion()).isEqualTo("1.0");
        assertThat(event.eventKey()).isEqualTo("REFUND_REQUESTED:" + chefSubOrderId);
        assertThat(event.correlationId()).isEqualTo(correlationId);
        assertThat(json.path("occurredAt").asText()).isEqualTo("2026-07-16T12:30:00Z");
        assertThat(json.path("subject").asText()).isEqualTo(chefSubOrderId.toString());
        assertThat(json.path("data").path("checkoutId").asText()).isEqualTo(checkoutId.toString());
        assertThat(json.path("data").path("chefSubOrderId").asText()).isEqualTo(chefSubOrderId.toString());
        assertThat(json.path("data").path("customerIdentityId").asText()).isEqualTo(customerIdentityId.toString());
        assertThat(json.path("data").path("refundAmount").decimalValue()).isEqualByComparingTo("220.00");
        assertThat(json.path("data").path("currency").asText()).isEqualTo("INR");
        assertThat(json.path("data").path("reason").asText()).isEqualTo("CHEF_ACCEPTANCE_TIMEOUT");
        assertThat(json.path("data").path("requestedAt").asText()).isEqualTo("2026-07-16T12:30:00Z");
    }

    @Test
    void usesStableCausationIdForIdempotentRetries() {
        RefundRequestedEventSource source = source("CHEF_DECLINED");

        SerializedDomainEvent first = factory.create(source, null, "reject-request-001");
        SerializedDomainEvent second = factory.create(source, null, "reject-request-001");

        assertThat(first.eventId()).isNotEqualTo(second.eventId());
        assertThat(first.causationId()).isEqualTo(second.causationId());
        assertThat(first.correlationId()).isEqualTo(source.checkoutId());
    }

    @Test
    void rejectsUnsupportedRefundReason() {
        assertThatThrownBy(() -> factory.create(source("CUSTOMER_CHANGED_MIND"), null, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Unsupported refund reason");
    }

    private static RefundRequestedEventSource source(String reason) {
        return new RefundRequestedEventSource(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            new BigDecimal("100.00"),
            "INR",
            reason,
            Instant.parse("2026-07-16T12:30:00Z")
        );
    }
}
