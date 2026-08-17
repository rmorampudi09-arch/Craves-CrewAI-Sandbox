package in.craves.integration.delivery.borzo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.DeliveryStatus;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class BorzoWebhookService {
    private final ObjectMapper objectMapper;
    private final BorzoSignatureVerifier signatureVerifier;
    private final BorzoWebhookInboxRepository inboxRepository;
    private final BorzoStatusMapper statusMapper;

    public BorzoWebhookService(ObjectMapper objectMapper,
                               BorzoSignatureVerifier signatureVerifier,
                               BorzoWebhookInboxRepository inboxRepository,
                               BorzoStatusMapper statusMapper) {
        this.objectMapper = objectMapper;
        this.signatureVerifier = signatureVerifier;
        this.inboxRepository = inboxRepository;
        this.statusMapper = statusMapper;
    }

    @Transactional
    public WebhookReceipt accept(String rawBody, String suppliedSignature) {
        if (!signatureVerifier.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Borzo callback verification is not configured");
        }
        if (!signatureVerifier.isValid(rawBody, suppliedSignature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Borzo callback signature");
        }

        JsonNode payload = parsePayload(rawBody);
        String eventType = requiredText(payload, "event_type");
        JsonNode order = payload.path("order");
        JsonNode delivery = payload.path("delivery");
        if (!order.isObject() && !delivery.isObject()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Borzo callback must contain order or delivery data");
        }

        DeliveryStatus normalizedStatus = delivery.isObject()
            ? statusMapper.fromDeliveryStatus(delivery.path("status").asText(null))
            : statusMapper.fromOrder(order);

        String providerEventId = deriveEventId(payload, rawBody);
        boolean inserted = inboxRepository.store(
            providerEventId,
            signatureVerifier.signatureFingerprint(suppliedSignature),
            payload
        );

        return new WebhookReceipt(providerEventId, eventType, normalizedStatus, !inserted);
    }

    private JsonNode parsePayload(String rawBody) {
        if (!StringUtils.hasText(rawBody)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Borzo callback body is empty");
        }
        try {
            JsonNode payload = objectMapper.readTree(rawBody);
            if (!payload.isObject()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Borzo callback body must be a JSON object");
            }
            return payload;
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Borzo callback body is not valid JSON", ex);
        }
    }

    private static String requiredText(JsonNode payload, String field) {
        String value = payload.path(field).asText(null);
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Borzo callback is missing " + field);
        }
        return value;
    }

    private static String deriveEventId(JsonNode payload, String rawBody) {
        String eventType = payload.path("event_type").asText("");
        String eventDatetime = payload.path("event_datetime").asText("");
        String orderId = payload.path("order").path("order_id").asText(
            payload.path("delivery").path("order_id").asText("")
        );
        String deliveryId = payload.path("delivery").path("delivery_id").asText("");
        String status = payload.path("delivery").path("status").asText(
            payload.path("order").path("status").asText("")
        );
        String canonical = String.join("|", eventType, eventDatetime, orderId, deliveryId, status);
        if (canonical.replace("|", "").isBlank()) {
            canonical = rawBody;
        }
        return sha256Hex(canonical);
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Could not calculate Borzo event identity", ex);
        }
    }

    public record WebhookReceipt(
        String providerEventId,
        String eventType,
        DeliveryStatus normalizedStatus,
        boolean duplicate
    ) {}
}
