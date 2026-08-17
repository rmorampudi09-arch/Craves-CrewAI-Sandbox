package in.craves.integration.config;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "craves.providers.borzo")
public class BorzoProperties {
    private static final Set<String> ENVIRONMENTS = Set.of("SANDBOX", "PRODUCTION");

    private boolean enabled = false;
    private String environment = "SANDBOX";
    private boolean productionActivationApproved = false;
    private String baseUrl = "https://robotapitest-in.borzodelivery.com/api/business/1.8";
    private String authToken = "";
    private String callbackSecret = "";
    private String callbackUrl = "";
    private int connectTimeoutSeconds = 5;
    private int readTimeoutSeconds = 20;
    private int reconciliationPageSize = 50;
    private int reconciliationMaxPages = 5;
    private int reconciliationLookbackSeconds = 120;

    @PostConstruct
    void validate() {
        URI uri = parseHttps(baseUrl, "Borzo baseUrl");
        String normalizedEnvironment = normalizedEnvironment();
        if (!ENVIRONMENTS.contains(normalizedEnvironment)) {
            throw new IllegalStateException("BORZO_API_ENVIRONMENT must be SANDBOX or PRODUCTION");
        }
        if (connectTimeoutSeconds < 1) {
            throw new IllegalStateException("Borzo connectTimeoutSeconds must be at least 1");
        }
        if (readTimeoutSeconds < 1) {
            throw new IllegalStateException("Borzo readTimeoutSeconds must be at least 1");
        }
        if (reconciliationPageSize < 1 || reconciliationPageSize > 50) {
            throw new IllegalStateException("Borzo reconciliationPageSize must be between 1 and 50");
        }
        if (reconciliationMaxPages < 1 || reconciliationMaxPages > 20) {
            throw new IllegalStateException("Borzo reconciliationMaxPages must be between 1 and 20");
        }
        if (reconciliationLookbackSeconds < 0 || reconciliationLookbackSeconds > 3600) {
            throw new IllegalStateException("Borzo reconciliationLookbackSeconds must be between 0 and 3600");
        }
        if (enabled && !StringUtils.hasText(authToken)) {
            throw new IllegalStateException("BORZO_API_AUTH_TOKEN is required when Borzo API is enabled");
        }
        if (enabled && !StringUtils.hasText(callbackSecret)) {
            throw new IllegalStateException("BORZO_CALLBACK_TOKEN is required when Borzo API is enabled");
        }
        if (StringUtils.hasText(callbackUrl)) {
            parseHttps(callbackUrl, "Borzo callbackUrl");
        }
        if ("PRODUCTION".equals(normalizedEnvironment)) {
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (host.contains("test") || host.contains("sandbox")) {
                throw new IllegalStateException("Borzo production environment cannot use a test or sandbox host");
            }
            if (enabled && !productionActivationApproved) {
                throw new IllegalStateException(
                    "BORZO_PRODUCTION_ACTIVATION_APPROVED must be true before production Borzo is enabled"
                );
            }
            if (enabled && !StringUtils.hasText(callbackUrl)) {
                throw new IllegalStateException("BORZO_CALLBACK_URL is required for production activation");
            }
        }
    }

    public String normalizedBaseUrl() {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public String normalizedEnvironment() {
        return environment == null ? "" : environment.trim().toUpperCase(Locale.ROOT);
    }

    public boolean productionReady() {
        return "PRODUCTION".equals(normalizedEnvironment())
            && productionActivationApproved
            && StringUtils.hasText(authToken)
            && StringUtils.hasText(callbackSecret)
            && StringUtils.hasText(callbackUrl)
            && !normalizedBaseUrl().toLowerCase(Locale.ROOT).contains("test")
            && !normalizedBaseUrl().toLowerCase(Locale.ROOT).contains("sandbox");
    }

    private static URI parseHttps(String value, String name) {
        URI uri;
        try {
            uri = URI.create(value);
        } catch (Exception ex) {
            throw new IllegalStateException(name + " must be a valid HTTPS URL", ex);
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || !StringUtils.hasText(uri.getHost())) {
            throw new IllegalStateException(name + " must be an HTTPS URL");
        }
        return uri;
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public boolean isProductionActivationApproved() { return productionActivationApproved; }
    public void setProductionActivationApproved(boolean productionActivationApproved) {
        this.productionActivationApproved = productionActivationApproved;
    }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getAuthToken() { return authToken; }
    public void setAuthToken(String authToken) { this.authToken = authToken; }
    public String getCallbackSecret() { return callbackSecret; }
    public void setCallbackSecret(String callbackSecret) { this.callbackSecret = callbackSecret; }
    public String getCallbackUrl() { return callbackUrl; }
    public void setCallbackUrl(String callbackUrl) { this.callbackUrl = callbackUrl; }
    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) { this.connectTimeoutSeconds = connectTimeoutSeconds; }
    public int getReadTimeoutSeconds() { return readTimeoutSeconds; }
    public void setReadTimeoutSeconds(int readTimeoutSeconds) { this.readTimeoutSeconds = readTimeoutSeconds; }
    public int getReconciliationPageSize() { return reconciliationPageSize; }
    public void setReconciliationPageSize(int reconciliationPageSize) { this.reconciliationPageSize = reconciliationPageSize; }
    public int getReconciliationMaxPages() { return reconciliationMaxPages; }
    public void setReconciliationMaxPages(int reconciliationMaxPages) { this.reconciliationMaxPages = reconciliationMaxPages; }
    public int getReconciliationLookbackSeconds() { return reconciliationLookbackSeconds; }
    public void setReconciliationLookbackSeconds(int reconciliationLookbackSeconds) {
        this.reconciliationLookbackSeconds = reconciliationLookbackSeconds;
    }
}
