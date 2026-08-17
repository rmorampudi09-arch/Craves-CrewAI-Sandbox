package in.craves.integration.delivery.borzo;

import com.fasterxml.jackson.databind.JsonNode;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.ProviderStatusUpdate;
import in.craves.integration.delivery.provider.DeliveryWebhookNormalizer;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class BorzoWebhookNormalizer implements DeliveryWebhookNormalizer {
    private static final String PROVIDER_ID = "borzo";
    private final BorzoStatusMapper statusMapper;

    public BorzoWebhookNormalizer(BorzoStatusMapper statusMapper) {
        this.statusMapper = statusMapper;
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public ProviderStatusUpdate normalize(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw new IllegalArgumentException("Borzo webhook payload must be a JSON object");
        }

        JsonNode delivery = payload.path("delivery");
        JsonNode order = payload.path("order");
        Instant observedAt = requiredTimestamp(
            firstText(
                delivery.path("status_datetime").asText(null),
                payload.path("event_datetime").asText(null)
            )
        );

        if (delivery.isObject()) {
            String providerOrderId = requiredText(delivery, "order_id");
            String providerDeliveryId = textOrNull(delivery, "delivery_id");
            String providerStatus = requiredText(delivery, "status");
            return new ProviderStatusUpdate(
                PROVIDER_ID,
                providerOrderId,
                providerDeliveryId,
                statusMapper.fromDeliveryStatus(providerStatus),
                providerStatus,
                textOrNull(delivery, "tracking_url"),
                observedAt,
                payload.deepCopy()
            );
        }

        if (order.isObject()) {
            String providerOrderId = requiredText(order, "order_id");
            String providerStatus = orderProviderStatus(order);
            return new ProviderStatusUpdate(
                PROVIDER_ID,
                providerOrderId,
                lastDeliveryId(order),
                statusMapper.fromOrder(order),
                providerStatus,
                lastTrackingUrl(order),
                observedAt,
                payload.deepCopy()
            );
        }

        throw new IllegalArgumentException("Borzo webhook must contain delivery or order data");
    }

    private static String orderProviderStatus(JsonNode order) {
        JsonNode points = order.path("points");
        if (points.isArray()) {
            for (int index = points.size() - 1; index >= 0; index--) {
                String status = points.path(index).path("delivery").path("status").asText(null);
                if (StringUtils.hasText(status)) {
                    return status;
                }
            }
        }
        return requiredText(order, "status");
    }

    private static String lastDeliveryId(JsonNode order) {
        JsonNode points = order.path("points");
        if (points.isArray()) {
            for (int index = points.size() - 1; index >= 0; index--) {
                String value = points.path(index).path("delivery_id").asText(null);
                if (!StringUtils.hasText(value)) {
                    value = points.path(index).path("delivery").path("delivery_id").asText(null);
                }
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    private static String lastTrackingUrl(JsonNode order) {
        JsonNode points = order.path("points");
        if (points.isArray()) {
            for (int index = points.size() - 1; index >= 0; index--) {
                String value = points.path(index).path("tracking_url").asText(null);
                if (StringUtils.hasText(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    private static String requiredText(JsonNode object, String field) {
        String value = object.path(field).asText(null);
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Borzo webhook is missing " + field);
        }
        return value;
    }

    private static String textOrNull(JsonNode object, String field) {
        String value = object.path(field).asText(null);
        return StringUtils.hasText(value) ? value : null;
    }

    private static String firstText(String first, String second) {
        return StringUtils.hasText(first) ? first : second;
    }

    private static Instant requiredTimestamp(String value) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException("Borzo webhook is missing an observation timestamp");
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Borzo webhook timestamp is invalid", ex);
        }
    }
}
