package in.craves.order.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChefAcceptedOrderEventFactoryTest {
    private final ObjectMapper objectMapper = JsonMapper.builder().findAndAddModules().build();
    private final ChefAcceptedOrderEventFactory factory = new ChefAcceptedOrderEventFactory(objectMapper);

    @Test
    void createsIntegrationCompatibleChefAcceptedOrderEvent() throws Exception {
        UUID chefSubOrderId = UUID.randomUUID();
        UUID checkoutId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();
        Instant acceptedAt = Instant.parse("2026-07-16T10:00:00Z");
        Instant readyAt = Instant.parse("2026-07-16T10:35:00Z");

        ChefAcceptedOrderEventSource source = new ChefAcceptedOrderEventSource(
            chefSubOrderId,
            checkoutId,
            acceptedAt,
            readyAt,
            600,
            false,
            "Craves Test Kitchen",
            "+919999999999",
            "Pickup line 1",
            "Pickup line 2",
            "Pickup landmark",
            "KPHB",
            "Hyderabad",
            "Telangana",
            "500001",
            new BigDecimal("17.3850000"),
            new BigDecimal("78.4867000"),
            "Test Customer",
            "+918888888888",
            "Dropoff line 1",
            "Dropoff line 2",
            "Dropoff landmark",
            "Madhapur",
            "Hyderabad",
            "Telangana",
            "500081",
            new BigDecimal("17.4483000"),
            new BigDecimal("78.3915000")
        );

        SerializedDomainEvent event = factory.create(source, correlationId, "accept-request-001");
        JsonNode json = objectMapper.readTree(event.payloadJson());

        assertThat(event.eventType()).isEqualTo("CHEF_ACCEPTED_ORDER");
        assertThat(event.eventVersion()).isEqualTo("1.0");
        assertThat(event.correlationId()).isEqualTo(correlationId);
        assertThat(event.eventKey()).isEqualTo("CHEF_ACCEPTED_ORDER:" + chefSubOrderId);
        assertThat(json.path("occurredAt").asText()).isEqualTo("2026-07-16T10:00:00Z");
        assertThat(json.path("source").asText()).isEqualTo("order-service");
        assertThat(json.path("subject").asText()).isEqualTo(chefSubOrderId.toString());
        assertThat(json.path("data").path("orderId").asText()).isEqualTo(checkoutId.toString());
        assertThat(json.path("data").path("chefSubOrderId").asText()).isEqualTo(chefSubOrderId.toString());
        assertThat(json.path("data").path("readyAt").asText()).isEqualTo("2026-07-16T10:35:00Z");
        assertThat(json.path("data").path("distanceKm").isNull()).isTrue();
        assertThat(json.path("data").path("area").asText()).isEqualTo("KPHB");
        assertThat(json.path("data").path("deliveryRequest").path("totalWeightGrams").asInt()).isEqualTo(600);
        assertThat(json.path("data").path("deliveryRequest").path("thermoboxRequired").asBoolean()).isFalse();
        assertThat(json.path("data").path("deliveryRequest").path("pickup").path("contactName").asText())
            .isEqualTo("Craves Test Kitchen");
        assertThat(json.path("data").path("deliveryRequest").path("dropoff").path("contactName").asText())
            .isEqualTo("Test Customer");
    }

    @Test
    void usesStableCausationIdForTheSameIdempotencyKey() {
        ChefAcceptedOrderEventSource source = source();

        SerializedDomainEvent first = factory.create(source, null, "same-key");
        SerializedDomainEvent second = factory.create(source, null, "same-key");

        assertThat(first.eventId()).isNotEqualTo(second.eventId());
        assertThat(first.causationId()).isEqualTo(second.causationId());
        assertThat(first.correlationId()).isEqualTo(first.eventId());
    }

    private static ChefAcceptedOrderEventSource source() {
        return new ChefAcceptedOrderEventSource(
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.parse("2026-07-16T10:00:00Z"),
            Instant.parse("2026-07-16T10:35:00Z"),
            500,
            true,
            "Kitchen",
            "+919999999999",
            "Pickup",
            null,
            null,
            "Area",
            "Hyderabad",
            "Telangana",
            "500001",
            new BigDecimal("17.38"),
            new BigDecimal("78.48"),
            "Customer",
            "+918888888888",
            "Dropoff",
            null,
            null,
            "Area",
            "Hyderabad",
            "Telangana",
            "500002",
            new BigDecimal("17.39"),
            new BigDecimal("78.49")
        );
    }
}
