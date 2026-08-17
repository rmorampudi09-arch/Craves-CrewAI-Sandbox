package in.craves.integration.refund;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.integration.config.RazorpayProviderProperties;
import in.craves.integration.payment.RazorpayRequestSafety;
import in.craves.integration.refund.CashfreeRefundClient.RefundProviderConfigurationException;
import in.craves.integration.refund.CashfreeRefundClient.RefundProviderNonRetryableException;
import in.craves.integration.refund.CashfreeRefundClient.RefundProviderTransientException;
import in.craves.integration.refund.RefundModels.ProviderRefundResult;
import in.craves.integration.refund.RefundModels.RefundWorkItem;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class RazorpayRefundClient {
    private final RazorpayProviderProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient client;

    public RazorpayRefundClient(
        RazorpayProviderProperties properties,
        ObjectMapper objectMapper,
        RestClient.Builder builder
    ) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.client = builder.clone().baseUrl(properties.baseUrl()).build();
    }

    public ProviderRefundResult createRefund(RefundWorkItem workItem) {
        requireConfiguration();
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("amount", RazorpayRequestSafety.toSubunits(workItem.amount()));
        request.put("receipt", workItem.refundReference());
        request.put("notes", Map.of(
            "craves_refund_id", workItem.refundId().toString(),
            "reason", safeNote(workItem.reason())
        ));
        try {
            JsonNode response = client.post()
                .uri("/v1/payments/{paymentId}/refund", workItem.providerPaymentId())
                .headers(this::basicAuth)
                .header("X-Razorpay-Idempotency-Key", workItem.idempotencyKey().toString())
                .body(request).retrieve().body(JsonNode.class);
            return map(response);
        } catch (RestClientResponseException exception) {
            throw providerException("Razorpay refund creation failed", exception);
        }
    }

    public ProviderRefundResult getRefund(RefundWorkItem workItem) {
        requireConfiguration();
        try {
            return map(client.get().uri("/v1/refunds/{refundId}", workItem.providerRefundId())
                .headers(this::basicAuth).retrieve().body(JsonNode.class));
        } catch (RestClientResponseException exception) {
            throw providerException("Razorpay refund reconciliation failed", exception);
        }
    }

    private ProviderRefundResult map(JsonNode response) {
        String id = text(response, "id");
        String rawStatus = text(response, "status");
        if (!StringUtils.hasText(id) || !id.startsWith("rfnd_") || !StringUtils.hasText(rawStatus)) {
            throw new RefundProviderTransientException("Razorpay returned an incomplete refund response");
        }
        String status = switch (rawStatus.toLowerCase(Locale.ROOT)) {
            case "processed" -> "SUCCESS";
            case "pending" -> "PENDING";
            case "failed" -> "FAILED";
            default -> rawStatus.toUpperCase(Locale.ROOT);
        };
        return new ProviderRefundResult(status, id, json(response));
    }

    private void requireConfiguration() {
        if (!properties.paymentExecutionAllowed() || !StringUtils.hasText(properties.keyId())
            || !StringUtils.hasText(properties.keySecret())) {
            throw new RefundProviderConfigurationException("Razorpay refund execution is not configured");
        }
    }

    private void basicAuth(HttpHeaders headers) {
        headers.setBasicAuth(properties.keyId(), properties.keySecret(), StandardCharsets.UTF_8);
    }

    private RuntimeException providerException(String prefix, RestClientResponseException exception) {
        HttpStatusCode status = exception.getStatusCode();
        String message = prefix + " with HTTP " + status.value();
        if (status.value() == 408 || status.value() == 409 || status.value() == 429 || status.is5xxServerError()) {
            return new RefundProviderTransientException(message, exception);
        }
        return new RefundProviderNonRetryableException(message, exception);
    }

    private String json(JsonNode value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (Exception exception) { throw new RefundProviderTransientException("Razorpay response could not be serialized", exception); }
    }

    private static String safeNote(String value) {
        String result = StringUtils.hasText(value) ? value.trim() : "Craves refund";
        return result.length() <= 250 ? result : result.substring(0, 250);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }
}
