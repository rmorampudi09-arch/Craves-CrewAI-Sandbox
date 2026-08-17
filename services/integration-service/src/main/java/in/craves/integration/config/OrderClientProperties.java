package in.craves.integration.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OrderClientProperties {
    private final String baseUrl;
    private final String internalBaseUrl;
    private final String internalKey;

    public OrderClientProperties(
        @Value("${CRAVES_ORDER_BASE_URL:https://api.craves.in/api/v1}") String baseUrl,
        @Value("${CRAVES_ORDER_INTERNAL_BASE_URL:https://ca-craves-order-service-prodlow.happysand-aedc7165.centralindia.azurecontainerapps.io/internal/v1}") String internalBaseUrl,
        @Value("${CRAVES_INTERNAL_SERVICE_KEY:}") String internalKey
    ) {
        this.baseUrl = baseUrl;
        this.internalBaseUrl = internalBaseUrl;
        this.internalKey = internalKey;
    }

    public String baseUrl() { return baseUrl; }
    public String internalBaseUrl() { return internalBaseUrl; }
    public String internalKey() { return internalKey; }
}
