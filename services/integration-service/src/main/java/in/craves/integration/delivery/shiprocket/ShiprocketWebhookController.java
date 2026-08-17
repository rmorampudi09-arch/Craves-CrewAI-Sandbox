package in.craves.integration.delivery.shiprocket;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks/delivery")
public class ShiprocketWebhookController {
    private final ShiprocketWebhookService webhookService;

    public ShiprocketWebhookController(ShiprocketWebhookService webhookService) {
        this.webhookService = webhookService;
    }

    /**
     * Neutral public path is intentional. Shiprocket's webhook setup guidance rejects callback
     * URLs containing provider-identifying terms. Non-event URL-validation probes are acknowledged
     * with HTTP 200 without persistence; real tracking events are authenticated with x-api-key by
     * ShiprocketWebhookService before any payload is persisted.
     */
    @PostMapping("/p4")
    public ResponseEntity<Map<String, Object>> accept(
        @RequestBody(required = false) String rawBody,
        @RequestHeader(value = "x-api-key", required = false) String apiKey
    ) {
        ShiprocketWebhookService.WebhookReceipt receipt = webhookService.accept(rawBody, apiKey);
        if (receipt.validationProbe()) {
            return ResponseEntity.ok(Map.of(
                "accepted", true,
                "validationProbe", true
            ));
        }
        return ResponseEntity.ok(Map.of(
            "accepted", true,
            "duplicate", receipt.duplicate(),
            "eventId", receipt.providerEventId()
        ));
    }
}
