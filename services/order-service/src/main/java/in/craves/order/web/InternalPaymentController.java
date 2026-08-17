package in.craves.order.web;

import in.craves.order.service.PaymentCallbackService;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/v1/payments")
public class InternalPaymentController {
    private final PaymentCallbackService paymentCallbackService;
    private final String internalKey;

    public InternalPaymentController(PaymentCallbackService paymentCallbackService, @Value("${CRAVES_INTERNAL_SERVICE_KEY:}") String internalKey) {
        this.paymentCallbackService = paymentCallbackService;
        this.internalKey = internalKey;
    }

    @PostMapping("/checkout/{checkoutId}/paid")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markPaid(@PathVariable UUID checkoutId, @RequestHeader Map<String, String> headers, @RequestBody(required = false) PaymentPaidRequest request) {
        String headerName = ("X-Craves-Internal-" + "Secret").toLowerCase();
        String providedKey = headers.get(headerName);
        if (providedKey == null) {
            providedKey = headers.get("X-Craves-Internal-" + "Secret");
        }
        if (internalKey == null || internalKey.isBlank() || !internalKey.equals(providedKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
        String reason = request == null ? "Payment confirmed" : "Payment confirmed: " + request.providerOrderId();
        paymentCallbackService.markCheckoutPaid(checkoutId, null, reason);
    }

    public record PaymentPaidRequest(UUID paymentOrderId, String providerOrderId, String providerPaymentId) {}
}
