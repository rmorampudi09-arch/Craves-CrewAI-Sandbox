package in.craves.integration.refund;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import in.craves.integration.refund.RefundModels.ProviderRefundResult;
import in.craves.integration.refund.RefundModels.RefundWorkItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefundStatusEventFactoryTest {
    @Test
    void serializesIsoTimestampAndExpectedEnvelope() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        RefundStatusEventFactory factory = new RefundStatusEventFactory(objectMapper);
        UUID checkoutId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID requestEventId = UUID.randomUUID();
        RefundWorkItem workItem = new RefundWorkItem(
            UUID.randomUUID(),
            UUID.randomUUID(),
            checkoutId,
            orderId,
            UUID.randomUUID(),
            requestEventId,
            "CRV_ORDER_1",
            "CRV" + orderId.toString().replace("-", ""),
            UUID.randomUUID(),
            new BigDecimal("220.00"),
            "INR",
            "CHEF_ACCEPTANCE_TIMEOUT",
            "PROCESSING",
            null,
            null,
            1,
            UUID.randomUUID()
        );
        Instant occurredAt = Instant.parse("2026-07-16T13:45:00Z");

        var event = factory.create(
            workItem,
            "REFUND_PENDING",
            new ProviderRefundResult("PENDING", "1553338", "{}"),
            occurredAt
        );

        JsonNode json = objectMapper.readTree(event.payloadJson());
        assertThat(json.path("eventType").asText()).isEqualTo("REFUND_STATUS_CHANGED");
        assertThat(json.path("occurredAt").asText()).isEqualTo("2026-07-16T13:45:00Z");
        assertThat(json.path("causationId").asText()).isEqualTo(requestEventId.toString());
        assertThat(json.path("data").path("status").asText()).isEqualTo("REFUND_PENDING");
        assertThat(json.path("data").path("refundAmount").decimalValue())
            .isEqualByComparingTo("220.00");
    }
}
