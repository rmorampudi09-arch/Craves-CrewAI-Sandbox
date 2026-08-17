package in.craves.integration.config;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class PaymentProviderProperties {
    private final String environment;
    private final boolean productionActivationApproved;
    private final boolean productionPaymentExecutionEnabled;
    private final String apiVersion;
    private final String clientId;
    private final String clientKey;
    private final String sandboxBaseUrl;
    private final String productionBaseUrl;
    private final String defaultReturnUrl;
    private final String webhookUrl;
    private final String sandboxRefundSimulationStatus;
    private final long webhookMaxSkewSeconds;
    private final Set<String> allowedWebhookVersions;

    public PaymentProviderProperties(
        @Value("${PAYMENT_PROVIDER_ENVIRONMENT:sandbox}") String environment,
        @Value("${CRAVES_CASHFREE_PRODUCTION_ACTIVATION_APPROVED:false}") boolean productionActivationApproved,
        @Value("${CRAVES_CASHFREE_PRODUCTION_PAYMENT_EXECUTION_ENABLED:false}") boolean productionPaymentExecutionEnabled,
        @Value("${PAYMENT_PROVIDER_API_VERSION:2025-01-01}") String apiVersion,
        @Value("${PAYMENT_PROVIDER_CLIENT_ID:}") String clientId,
        @Value("${PAYMENT_PROVIDER_CLIENT_KEY:}") String clientKey,
        @Value("${PAYMENT_PROVIDER_SANDBOX_BASE_URL:https://sandbox.cashfree.com}") String sandboxBaseUrl,
        @Value("${PAYMENT_PROVIDER_PRODUCTION_BASE_URL:https://api.cashfree.com}") String productionBaseUrl,
        @Value("${PAYMENT_PROVIDER_DEFAULT_RETURN_URL:https://craves.in/payment/return}") String defaultReturnUrl,
        @Value("${PAYMENT_PROVIDER_WEBHOOK_URL:https://api.craves.in/api/v1/payments/webhooks/cashfree}") String webhookUrl,
        @Value("${CRAVES_CASHFREE_SANDBOX_REFUND_SIMULATION_STATUS:}") String sandboxRefundSimulationStatus,
        @Value("${CRAVES_CASHFREE_WEBHOOK_MAX_SKEW_SECONDS:300}") long webhookMaxSkewSeconds,
        @Value("${CRAVES_CASHFREE_ALLOWED_WEBHOOK_VERSIONS:2025-01-01,2023-08-01}") String allowedWebhookVersions
    ) {
        this.environment = environment;
        this.productionActivationApproved = productionActivationApproved;
        this.productionPaymentExecutionEnabled = productionPaymentExecutionEnabled;
        this.apiVersion = apiVersion;
        this.clientId = clientId;
        this.clientKey = clientKey;
        this.sandboxBaseUrl = sandboxBaseUrl;
        this.productionBaseUrl = productionBaseUrl;
        this.defaultReturnUrl = defaultReturnUrl;
        this.webhookUrl = webhookUrl;
        this.sandboxRefundSimulationStatus = sandboxRefundSimulationStatus;
        this.webhookMaxSkewSeconds = webhookMaxSkewSeconds;
        this.allowedWebhookVersions = Arrays.stream(allowedWebhookVersions.split(","))
            .map(String::trim)
            .filter(StringUtils::hasText)
            .collect(Collectors.toUnmodifiableSet());
    }

    @PostConstruct
    void validate() {
        String normalized = normalizedEnvironment();
        if (!Set.of("SANDBOX", "PRODUCTION").contains(normalized)) {
            throw new IllegalStateException("PAYMENT_PROVIDER_ENVIRONMENT must be sandbox or production");
        }
        requireHttps(sandboxBaseUrl, "PAYMENT_PROVIDER_SANDBOX_BASE_URL");
        requireHttps(productionBaseUrl, "PAYMENT_PROVIDER_PRODUCTION_BASE_URL");
        requireHttps(defaultReturnUrl, "PAYMENT_PROVIDER_DEFAULT_RETURN_URL");
        requireHttps(webhookUrl, "PAYMENT_PROVIDER_WEBHOOK_URL");
        if (webhookMaxSkewSeconds < 30 || webhookMaxSkewSeconds > 900) {
            throw new IllegalStateException("CRAVES_CASHFREE_WEBHOOK_MAX_SKEW_SECONDS must be between 30 and 900");
        }
        if (allowedWebhookVersions.isEmpty()) {
            throw new IllegalStateException("At least one Cashfree webhook version must be allowed");
        }
        if ("PRODUCTION".equals(normalized)) {
            if (!productionActivationApproved) {
                throw new IllegalStateException(
                    "CRAVES_CASHFREE_PRODUCTION_ACTIVATION_APPROVED must be true for production"
                );
            }
            if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientKey)) {
                throw new IllegalStateException("Cashfree production credentials are required");
            }
            String host = URI.create(productionBaseUrl).getHost().toLowerCase(Locale.ROOT);
            if (host.contains("sandbox") || host.contains("test")) {
                throw new IllegalStateException("Cashfree production cannot use a sandbox or test host");
            }
            if (StringUtils.hasText(sandboxRefundSimulationStatus)) {
                throw new IllegalStateException("Cashfree refund simulation must be empty in production");
            }
        } else if (productionPaymentExecutionEnabled) {
            throw new IllegalStateException(
                "Production payment execution cannot be enabled while Cashfree is in sandbox"
            );
        }
    }

    public String environment() { return environment; }
    public String normalizedEnvironment() {
        return environment == null ? "" : environment.trim().toUpperCase(Locale.ROOT);
    }
    public boolean productionActivationApproved() { return productionActivationApproved; }
    public boolean productionPaymentExecutionEnabled() { return productionPaymentExecutionEnabled; }
    public String apiVersion() { return apiVersion; }
    public String clientId() { return clientId; }
    public String clientKey() { return clientKey; }
    public String defaultReturnUrl() { return defaultReturnUrl; }
    public String webhookUrl() { return webhookUrl; }
    public String sandboxRefundSimulationStatus() { return sandboxRefundSimulationStatus; }
    public long webhookMaxSkewSeconds() { return webhookMaxSkewSeconds; }
    public Set<String> allowedWebhookVersions() { return allowedWebhookVersions; }
    public boolean sandbox() { return !"PRODUCTION".equals(normalizedEnvironment()); }
    public String baseUrl() { return sandbox() ? sandboxBaseUrl : productionBaseUrl; }
    public boolean paymentExecutionAllowed() { return sandbox() || productionPaymentExecutionEnabled; }
    public boolean productionReady() {
        return !sandbox()
            && productionActivationApproved
            && StringUtils.hasText(clientId)
            && StringUtils.hasText(clientKey)
            && StringUtils.hasText(apiVersion)
            && StringUtils.hasText(webhookUrl);
    }

    private static void requireHttps(String value, String name) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (Exception ex) {
            throw new IllegalStateException(name + " must be a valid HTTPS URL", ex);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
            throw new IllegalStateException(name + " must be an HTTPS URL");
        }
    }
}
