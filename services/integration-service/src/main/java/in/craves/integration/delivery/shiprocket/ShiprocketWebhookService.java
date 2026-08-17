package in.craves.integration.delivery.shiprocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.craves.integration.config.ShiprocketProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ShiprocketWebhookService {
    private final ObjectMapper objectMapper;
    private final ShiprocketProperties properties;
    private final ShiprocketWebhookInboxRepository inboxRepository;

    public ShiprocketWebhookService(ObjectMapper objectMapper,
                                    ShiprocketProperties properties,
                                    ShiprocketWebhookInboxRepository inboxRepository) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.inboxRepository = inboxRepository;
    }

    @Transactional
    public WebhookReceipt accept(String rawBody, String suppliedApiKey) {
        JsonNode payload = parseObjectOrNull(rawBody);

        /*
         * Shiprocket validates a webhook URL while it is being saved and requires the callback
         * URL to answer HTTP 200. That validation request is not guaranteed to contain a real
         * tracking event. Acknowledge non-event probes without persisting or mutating anything.
         * Real tracking events still require the configured x-api-key before persistence.
         */
        if (!isTrackingEvent(payload)) {
            return WebhookReceipt.forValidationProbe();
        }

        if (!StringUtils.hasText(properties.getWebhookToken())) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Delivery callback verification is not configured"
            );
        }
        if (!constantTimeEquals(properties.getWebhookToken(), suppliedApiKey)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid delivery callback credential");
        }

        String awb = payload.path("awb").asText().trim();
        ObjectNode storedPayload = payload.deepCopy();
        storedPayload.put("_craves_received_at", Instant.now().toString());
        String providerEventId = deriveEventId(payload, rawBody);
        boolean inserted = inboxRepository.store(
            providerEventId,
            sha256Hex(suppliedApiKey),
            storedPayload
        );
        return WebhookReceipt.forTrackingEvent(providerEventId, awb, !inserted);
    }

    private JsonNode parseObjectOrNull(String rawBody) {
        if (!StringUtils.hasText(rawBody)) {
            return null;
        }
        try {
            JsonNode payload = objectMapper.readTree(rawBody);
            return payload != null && payload.isObject() ? payload : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isTrackingEvent(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            return false;
        }
        String awb = payload.path("awb").asText(null);
        boolean hasShipmentStatus = StringUtils.hasText(payload.path("shipment_status").asText(null))
            || payload.path("shipment_status_id").canConvertToInt();
        return StringUtils.hasText(awb) && hasShipmentStatus;
    }

    private static String deriveEventId(JsonNode payload, String rawBody) {
        String canonical = String.join(
            "|",
            payload.path("awb").asText(""),
            payload.path("shipment_status_id").asText(""),
            payload.path("shipment_status").asText(""),
            payload.path("current_timestamp").asText(""),
            payload.path("order_id").asText("")
        );
        if (canonical.replace("|", "").isBlank()) {
            canonical = rawBody;
        }
        return sha256Hex(canonical);
    }

    private static boolean constantTimeEquals(String expected, String supplied) {
        if (!StringUtils.hasText(expected) || !StringUtils.hasText(supplied)) {
            return false;
        }
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            supplied.getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(
                digest.digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception ex) {
            throw new IllegalStateException("Could not calculate delivery callback fingerprint", ex);
        }
    }

    public record WebhookReceipt(
        String providerEventId,
        String awb,
        boolean duplicate,
        boolean validationProbe
    ) {
        static WebhookReceipt forValidationProbe() {
            return new WebhookReceipt("", "", false, true);
        }

        static WebhookReceipt forTrackingEvent(String providerEventId, String awb, boolean duplicate) {
            return new WebhookReceipt(providerEventId, awb, duplicate, false);
        }
    }
}
