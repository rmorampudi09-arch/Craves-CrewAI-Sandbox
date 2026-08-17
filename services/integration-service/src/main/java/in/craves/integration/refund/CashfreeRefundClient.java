package in.craves.integration.refund;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.integration.config.PaymentProviderProperties;
import in.craves.integration.refund.RefundModels.ProviderRefundResult;
import in.craves.integration.refund.RefundModels.RefundWorkItem;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class CashfreeRefundClient {
    private static final Set<String> SUPPORTED_SANDBOX_SIMULATION_STATUSES = Set.of(
        "SUCCESS",
        "FAILED",
        "PENDING"
    );

    private final PaymentProviderProperties provider;
    private final ObjectMapper objectMapper;
    private final RestClient client;

    public CashfreeRefundClient(
        PaymentProviderProperties provider,
        ObjectMapper objectMapper,
        RestClient.Builder restClientBuilder
    ) {
        this.provider = provider;
        this.objectMapper = objectMapper;
        this.client = restClientBuilder.clone().baseUrl(provider.baseUrl()).build();
    }

    public ProviderRefundResult createRefund(RefundWorkItem workItem) {
        validateProviderConfiguration();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("refund_amount", workItem.amount());
        request.put("refund_id", workItem.refundReference());
        request.put("refund_note", refundNote(workItem.reason()));
        request.put("refund_speed", "STANDARD");

        try {
            JsonNode response = client.post()
                .uri("/pg/orders/{orderId}/refunds", workItem.cashfreeOrderId())
                .header("x-client-id", provider.clientId())
                .header("x-client-" + "secret", provider.clientKey())
                .header("x-api-version", provider.apiVersion())
                .header("x-idempotency-key", workItem.idempotencyKey().toString())
                .body(request)
                .retrieve()
                .body(JsonNode.class);
            return map(response);
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 409 || exception.getStatusCode().value() == 422) {
                return getRefund(workItem);
            }
            throw providerException("Cashfree refund creation failed", exception);
        }
    }

    public ProviderRefundResult getRefund(RefundWorkItem workItem) {
        validateProviderConfiguration();
        try {
            JsonNode response = client.get()
                .uri(
                    "/pg/orders/{orderId}/refunds/{refundId}",
                    workItem.cashfreeOrderId(),
                    workItem.refundReference()
                )
                .header("x-client-id", provider.clientId())
                .header("x-client-" + "secret", provider.clientKey())
                .header("x-api-version", provider.apiVersion())
                .header("x-idempotency-key", workItem.idempotencyKey().toString())
                .retrieve()
                .body(JsonNode.class);
            return map(response);
        } catch (RestClientResponseException exception) {
            throw providerException("Cashfree refund reconciliation failed", exception);
        }
    }

    private ProviderRefundResult map(JsonNode response) {
        JsonNode entity = response;
        if (response != null && response.isArray()) {
            entity = response.isEmpty() ? null : response.get(0);
        }
        if (entity == null || !StringUtils.hasText(text(entity, "refund_status"))) {
            throw new RefundProviderTransientException("Cashfree returned an incomplete refund response");
        }
        return new ProviderRefundResult(
            text(entity, "refund_status").toUpperCase(Locale.ROOT),
            text(entity, "cf_refund_id"),
            json(entity)
        );
    }

    private RuntimeException providerException(String prefix, RestClientResponseException exception) {
        HttpStatusCode status = exception.getStatusCode();
        String message = prefix + " with HTTP " + status.value();
        if (status.value() == 408 || status.value() == 429 || status.is5xxServerError()) {
            return new RefundProviderTransientException(message, exception);
        }
        return new RefundProviderNonRetryableException(message, exception);
    }

    private void validateProviderConfiguration() {
        if (!StringUtils.hasText(provider.clientId()) || !StringUtils.hasText(provider.clientKey())) {
            throw new RefundProviderConfigurationException("Cashfree credentials are not configured");
        }
        if (!StringUtils.hasText(provider.apiVersion())) {
            throw new RefundProviderConfigurationException("Cashfree API version is not configured");
        }
    }

    private String refundNote(String reason) {
        if (provider.sandbox() && StringUtils.hasText(provider.sandboxRefundSimulationStatus())) {
            String simulationStatus = provider.sandboxRefundSimulationStatus()
                .trim()
                .toUpperCase(Locale.ROOT);
            if (!SUPPORTED_SANDBOX_SIMULATION_STATUSES.contains(simulationStatus)) {
                throw new RefundProviderConfigurationException(
                    "Unsupported Cashfree sandbox refund simulation status " + simulationStatus
                );
            }
            return simulationStatus;
        }

        String value = "Craves refund: " + reason;
        return value.length() <= 100 ? value : value.substring(0, 100);
    }

    private String json(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception exception) {
            throw new RefundProviderTransientException("Cashfree response could not be serialized", exception);
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    public static class RefundProviderTransientException extends RuntimeException {
        public RefundProviderTransientException(String message) { super(message); }
        public RefundProviderTransientException(String message, Throwable cause) { super(message, cause); }
    }

    public static class RefundProviderNonRetryableException extends RuntimeException {
        public RefundProviderNonRetryableException(String message, Throwable cause) { super(message, cause); }
    }

    public static class RefundProviderConfigurationException extends RuntimeException {
        public RefundProviderConfigurationException(String message) { super(message); }
    }
}
