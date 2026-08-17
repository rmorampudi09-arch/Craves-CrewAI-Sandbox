package in.craves.integration.delivery.shiprocket;

import com.fasterxml.jackson.databind.JsonNode;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.ProviderStatusUpdate;
import in.craves.integration.delivery.provider.DeliveryWebhookNormalizer;
import java.time.Instant;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ShiprocketWebhookNormalizer implements DeliveryWebhookNormalizer {
    private static final String PROVIDER_ID = "shiprocket";

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public ProviderStatusUpdate normalize(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw new IllegalArgumentException("Shiprocket webhook payload must be a JSON object");
        }

        String awb = requiredText(payload, "awb");
        Integer shipmentStatusId = integerOrNull(payload, "shipment_status_id");
        String shipmentStatus = firstText(
            payload.path("shipment_status").asText(null),
            payload.path("current_status").asText(null)
        );
        if (shipmentStatusId == null && !StringUtils.hasText(shipmentStatus)) {
            throw new IllegalArgumentException("Shiprocket webhook is missing shipment status");
        }

        Instant observedAt = requiredInstant(payload, "_craves_received_at");
        return new ProviderStatusUpdate(
            PROVIDER_ID,
            awb,
            awb,
            ShiprocketStatusMapper.map(shipmentStatusId, shipmentStatus),
            StringUtils.hasText(shipmentStatus) ? shipmentStatus.trim() : "SHIPMENT_STATUS_" + shipmentStatusId,
            null,
            observedAt,
            payload.deepCopy()
        );
    }

    private static String requiredText(JsonNode payload, String field) {
        String value = payload.path(field).asText(null);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Shiprocket webhook is missing " + field);
        }
        return value.trim();
    }

    private static Integer integerOrNull(JsonNode payload, String field) {
        JsonNode value = payload.path(field);
        if (value.isIntegralNumber()) {
            return value.intValue();
        }
        if (value.isTextual() && StringUtils.hasText(value.asText())) {
            try {
                return Integer.valueOf(value.asText().trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Shiprocket webhook " + field + " is not an integer", ex);
            }
        }
        return null;
    }

    private static String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private static Instant requiredInstant(JsonNode payload, String field) {
        String value = requiredText(payload, field);
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Shiprocket webhook receipt timestamp is invalid", ex);
        }
    }
}
