package in.craves.integration.delivery.borzo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.DeliveryStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class BorzoWebhookNormalizerTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BorzoWebhookNormalizer normalizer = new BorzoWebhookNormalizer(
        new BorzoStatusMapper()
    );

    @Test
    void normalizesDeliveryCallbackUsingProviderOrderIdentityAndStatusTime() throws Exception {
        var payload = objectMapper.readTree("""
            {
              "event_datetime":"2026-07-24T03:00:00+05:30",
              "event_type":"delivery_changed",
              "delivery":{
                "delivery_id":11712,
                "order_id":1250032,
                "status":"courier_at_pickup",
                "status_datetime":"2026-07-24T03:00:05+05:30",
                "tracking_url":"https://tracking.example/11712"
              }
            }
            """);

        var update = normalizer.normalize(payload);

        assertThat(update.providerId()).isEqualTo("borzo");
        assertThat(update.providerOrderId()).isEqualTo("1250032");
        assertThat(update.providerDeliveryId()).isEqualTo("11712");
        assertThat(update.status()).isEqualTo(DeliveryStatus.AT_PICKUP);
        assertThat(update.providerStatus()).isEqualTo("courier_at_pickup");
        assertThat(update.observedAt()).isEqualTo(Instant.parse("2026-07-23T21:30:05Z"));
        assertThat(update.trackingUrl()).isEqualTo("https://tracking.example/11712");
    }

    @Test
    void normalizesOrderCallbackFromLastDeliveryPoint() throws Exception {
        var payload = objectMapper.readTree("""
            {
              "event_datetime":"2026-07-24T03:15:00+05:30",
              "event_type":"order_changed",
              "order":{
                "order_id":1250032,
                "status":"active",
                "points":[
                  {},
                  {
                    "delivery_id":11712,
                    "tracking_url":"https://tracking.example/11712",
                    "delivery":{"status":"parcel_picked_up"}
                  }
                ]
              }
            }
            """);

        var update = normalizer.normalize(payload);

        assertThat(update.providerOrderId()).isEqualTo("1250032");
        assertThat(update.providerDeliveryId()).isEqualTo("11712");
        assertThat(update.status()).isEqualTo(DeliveryStatus.PICKED_UP);
        assertThat(update.providerStatus()).isEqualTo("parcel_picked_up");
        assertThat(update.observedAt()).isEqualTo(Instant.parse("2026-07-23T21:45:00Z"));
    }

    @Test
    void rejectsCallbacksWithoutProviderOrderId() throws Exception {
        var payload = objectMapper.readTree("""
            {
              "event_datetime":"2026-07-24T03:00:00+05:30",
              "event_type":"delivery_changed",
              "delivery":{"status":"active"}
            }
            """);

        assertThatThrownBy(() -> normalizer.normalize(payload))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("order_id");
    }
}
