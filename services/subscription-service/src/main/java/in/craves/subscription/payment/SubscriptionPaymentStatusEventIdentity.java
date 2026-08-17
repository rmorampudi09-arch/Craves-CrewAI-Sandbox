package in.craves.subscription.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;

final class SubscriptionPaymentStatusEventIdentity {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SubscriptionPaymentStatusEventIdentity() {
    }

    static UUID subscriptionId(String rawPayload) {
        try {
            JsonNode root = MAPPER.readTree(rawPayload);
            String value = root.path("data").path("subscriptionId").asText(null);
            return UUID.fromString(value);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Payment event subscriptionId could not be parsed", exception);
        }
    }
}
