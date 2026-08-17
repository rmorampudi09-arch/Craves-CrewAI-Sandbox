package in.craves.integration.delivery.shiprocket;

import static in.craves.integration.delivery.provider.DeliveryProviderAdapter.DeliveryStatus.IN_TRANSIT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ShiprocketWebhookNormalizerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ShiprocketWebhookNormalizer normalizer = new ShiprocketWebhookNormalizer();

    @Test
    void usesShipmentStatusIdInsteadOfCurrentStatusId() throws Exception {
        var payload = objectMapper.readTree("""
            {
              "awb": "14326480716236",
              "current_status": "IN TRANSIT",
              "current_status_id": 20,
              "shipment_status": "IN TRANSIT",
              "shipment_status_id": 18,
              "current_timestamp": "2026-08-16 04:00:00",
              "_craves_received_at": "2026-08-15T22:30:00Z"
            }
            """);

        var result = normalizer.normalize(payload);

        assertThat(result.providerId()).isEqualTo("shiprocket");
        assertThat(result.providerOrderId()).isEqualTo("14326480716236");
        assertThat(result.providerDeliveryId()).isEqualTo("14326480716236");
        assertThat(result.status()).isEqualTo(IN_TRANSIT);
        assertThat(result.providerStatus()).isEqualTo("IN TRANSIT");
        assertThat(result.observedAt()).isEqualTo(Instant.parse("2026-08-15T22:30:00Z"));
    }

    @Test
    void acceptsShipmentStatusIdAsText() throws Exception {
        var payload = objectMapper.readTree("""
            {
              "awb": "AWB-1",
              "shipment_status_id": "18",
              "shipment_status": "IN TRANSIT",
              "_craves_received_at": "2026-08-15T22:30:00Z"
            }
            """);

        assertThat(normalizer.normalize(payload).status()).isEqualTo(IN_TRANSIT);
    }

    @Test
    void refusesPayloadWithoutCravesReceiptTimestamp() throws Exception {
        var payload = objectMapper.readTree("""
            {
              "awb": "AWB-1",
              "shipment_status_id": 18,
              "shipment_status": "IN TRANSIT"
            }
            """);

        assertThatThrownBy(() -> normalizer.normalize(payload))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("_craves_received_at");
    }
}
