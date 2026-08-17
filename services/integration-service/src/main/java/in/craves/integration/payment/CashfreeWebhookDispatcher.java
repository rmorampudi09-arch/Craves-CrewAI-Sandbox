package in.craves.integration.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.integration.service.PaymentService;
import in.craves.integration.subscription.SubscriptionPaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CashfreeWebhookDispatcher {
    private final ObjectMapper objectMapper;
    private final CashfreeWebhookProviderVerifier providerVerifier;
    private final PaymentService checkoutPayments;
    private final SubscriptionPaymentService subscriptionPayments;

    public CashfreeWebhookDispatcher(
        ObjectMapper objectMapper,
        CashfreeWebhookProviderVerifier providerVerifier,
        PaymentService checkoutPayments,
        SubscriptionPaymentService subscriptionPayments
    ) {
        this.objectMapper = objectMapper;
        this.providerVerifier = providerVerifier;
        this.checkoutPayments = checkoutPayments;
        this.subscriptionPayments = subscriptionPayments;
    }

    public void dispatch(String timestamp, String signature, String rawPayload) {
        try {
            JsonNode payload = objectMapper.readTree(rawPayload);
            providerVerifier.verifySuccessfulPayment(payload);
            if (subscriptionPayments.handlesWebhook(payload)) {
                subscriptionPayments.applyWebhook(payload);
                return;
            }
            checkoutPayments.handleWebhook(timestamp, signature, rawPayload);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cashfree webhook payload is invalid", exception);
        }
    }
}
