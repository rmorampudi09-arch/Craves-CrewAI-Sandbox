package in.craves.integration.web;

import in.craves.integration.delivery.borzo.BorzoWebhookService;
import in.craves.integration.delivery.borzo.BorzoWebhookService.WebhookReceipt;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/webhooks/delivery/borzo")
public class BorzoWebhookController {
    private static final String SIGNATURE_HEADER = "X-DV-Signature";
    private final BorzoWebhookService service;

    public BorzoWebhookController(BorzoWebhookService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public WebhookReceipt accept(
        @RequestHeader(value = SIGNATURE_HEADER, required = false) String signature,
        @RequestBody String rawBody
    ) {
        return service.accept(rawBody, signature);
    }
}
