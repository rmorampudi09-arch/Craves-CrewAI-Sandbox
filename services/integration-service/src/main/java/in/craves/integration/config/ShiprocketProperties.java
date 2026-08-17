package in.craves.integration.config;

import jakarta.annotation.PostConstruct;
import java.math.BigDecimal;
import java.net.URI;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "craves.providers.shiprocket")
public class ShiprocketProperties {
    /**
     * Shiprocket documents that authenticated API calls operate on real-time account data. There
     * is therefore no assumed isolated sandbox here. READ_ONLY is the safe non-mutating mode. The
     * legacy SANDBOX value is accepted only as an alias for READ_ONLY so existing Azure settings do
     * not break during migration.
     */
    private static final Set<String> ENVIRONMENTS = Set.of("READ_ONLY", "SANDBOX", "PRODUCTION");

    private boolean enabled = false;
    private boolean createEnabled = false;
    private String environment = "READ_ONLY";
    private boolean productionActivationApproved = false;
    private boolean attributionApproved = false;
    private String baseUrl = "https://apiv2.shiprocket.in/v1/external";
    private String email = "";
    private String password = "";
    private String webhookToken = "";
    private String orderEmail = "";
    private BigDecimal packageLengthCm;
    private BigDecimal packageBreadthCm;
    private BigDecimal packageHeightCm;
    private int connectTimeoutSeconds = 5;
    private int readTimeoutSeconds = 20;
    private int readRetryAttempts = 3;
    private int authRefreshSkewMinutes = 60;
    private int maximumAcceptedEtaMinutes = 60;

    @PostConstruct
    void validate() {
        URI uri = parseHttps(baseUrl, "Shiprocket baseUrl");
        String configuredEnvironment = configuredEnvironment();
        if (!ENVIRONMENTS.contains(configuredEnvironment)) {
            throw new IllegalStateException(
                "SHIPROCKET_API_ENVIRONMENT must be READ_ONLY, SANDBOX (legacy read-only alias), or PRODUCTION"
            );
        }
        if (connectTimeoutSeconds < 1 || readTimeoutSeconds < 1) {
            throw new IllegalStateException("Shiprocket HTTP timeouts must be at least 1 second");
        }
        if (readRetryAttempts < 1 || readRetryAttempts > 5) {
            throw new IllegalStateException("Shiprocket readRetryAttempts must be between 1 and 5");
        }
        if (authRefreshSkewMinutes < 5 || authRefreshSkewMinutes > 1440) {
            throw new IllegalStateException("Shiprocket authRefreshSkewMinutes must be between 5 and 1440");
        }
        if (maximumAcceptedEtaMinutes < 1 || maximumAcceptedEtaMinutes > 240) {
            throw new IllegalStateException("Shiprocket maximumAcceptedEtaMinutes must be between 1 and 240");
        }
        validateDimensionsAllOrNone();

        if (enabled) {
            requireText(email, "SHIPROCKET_API_EMAIL");
            requireText(password, "SHIPROCKET_API_PASSWORD");
        }

        if (createEnabled) {
            if (!enabled) {
                throw new IllegalStateException("SHIPROCKET_CREATE_ENABLED requires SHIPROCKET_API_ENABLED=true");
            }
            if (!"PRODUCTION".equals(executionMode())) {
                throw new IllegalStateException(
                    "Shiprocket create is allowed only in explicit PRODUCTION mode; READ_ONLY/SANDBOX never mutates the account"
                );
            }
            if (!productionActivationApproved) {
                throw new IllegalStateException(
                    "SHIPROCKET_PRODUCTION_ACTIVATION_APPROVED must be true before Shiprocket create is enabled"
                );
            }
            requireText(webhookToken, "SHIPROCKET_WEBHOOK_TOKEN");
            requireText(orderEmail, "SHIPROCKET_ORDER_EMAIL");
            requirePositive(packageLengthCm, "SHIPROCKET_PACKAGE_LENGTH_CM");
            requirePositive(packageBreadthCm, "SHIPROCKET_PACKAGE_BREADTH_CM");
            requirePositive(packageHeightCm, "SHIPROCKET_PACKAGE_HEIGHT_CM");
            if (!attributionApproved) {
                throw new IllegalStateException(
                    "SHIPROCKET_ATTRIBUTION_APPROVED must be true before provider create is enabled"
                );
            }
        }

        if ("PRODUCTION".equals(executionMode())) {
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            if (host.contains("test") || host.contains("sandbox") || host.contains("stage")) {
                throw new IllegalStateException("Shiprocket production environment cannot use a test/stage/sandbox host");
            }
        }
    }

    private void validateDimensionsAllOrNone() {
        int configured = 0;
        configured += packageLengthCm == null ? 0 : 1;
        configured += packageBreadthCm == null ? 0 : 1;
        configured += packageHeightCm == null ? 0 : 1;
        if (configured != 0 && configured != 3) {
            throw new IllegalStateException(
                "SHIPROCKET_PACKAGE_LENGTH_CM, SHIPROCKET_PACKAGE_BREADTH_CM and SHIPROCKET_PACKAGE_HEIGHT_CM must be configured together"
            );
        }
        if (configured == 3) {
            requirePositive(packageLengthCm, "SHIPROCKET_PACKAGE_LENGTH_CM");
            requirePositive(packageBreadthCm, "SHIPROCKET_PACKAGE_BREADTH_CM");
            requirePositive(packageHeightCm, "SHIPROCKET_PACKAGE_HEIGHT_CM");
        }
    }

    public boolean credentialReady() {
        return StringUtils.hasText(email) && StringUtils.hasText(password);
    }

    public boolean createPrerequisitesReady() {
        return credentialReady()
            && "PRODUCTION".equals(executionMode())
            && productionActivationApproved
            && StringUtils.hasText(webhookToken)
            && StringUtils.hasText(orderEmail)
            && packageDimensionsReady()
            && attributionApproved;
    }

    public boolean productionCreateReady() {
        return createEnabled && createPrerequisitesReady();
    }

    public boolean packageDimensionsReady() {
        return positive(packageLengthCm)
            && positive(packageBreadthCm)
            && positive(packageHeightCm);
    }

    public String normalizedBaseUrl() {
        return baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    public String configuredEnvironment() {
        return environment == null ? "" : environment.trim().toUpperCase(Locale.ROOT);
    }

    /** Legacy SANDBOX means READ_ONLY; it never authorizes account mutation. */
    public String executionMode() {
        return "SANDBOX".equals(configuredEnvironment()) ? "READ_ONLY" : configuredEnvironment();
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

    private static void requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(name + " is required");
        }
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (!positive(value)) {
            throw new IllegalStateException(name + " must be greater than zero");
        }
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isCreateEnabled() { return createEnabled; }
    public void setCreateEnabled(boolean createEnabled) { this.createEnabled = createEnabled; }
    public String getEnvironment() { return environment; }
    public void setEnvironment(String environment) { this.environment = environment; }
    public boolean isProductionActivationApproved() { return productionActivationApproved; }
    public void setProductionActivationApproved(boolean productionActivationApproved) { this.productionActivationApproved = productionActivationApproved; }
    public boolean isAttributionApproved() { return attributionApproved; }
    public void setAttributionApproved(boolean attributionApproved) { this.attributionApproved = attributionApproved; }
    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getWebhookToken() { return webhookToken; }
    public void setWebhookToken(String webhookToken) { this.webhookToken = webhookToken; }
    public String getOrderEmail() { return orderEmail; }
    public void setOrderEmail(String orderEmail) { this.orderEmail = orderEmail; }
    public BigDecimal getPackageLengthCm() { return packageLengthCm; }
    public void setPackageLengthCm(BigDecimal packageLengthCm) { this.packageLengthCm = packageLengthCm; }
    public BigDecimal getPackageBreadthCm() { return packageBreadthCm; }
    public void setPackageBreadthCm(BigDecimal packageBreadthCm) { this.packageBreadthCm = packageBreadthCm; }
    public BigDecimal getPackageHeightCm() { return packageHeightCm; }
    public void setPackageHeightCm(BigDecimal packageHeightCm) { this.packageHeightCm = packageHeightCm; }
    public int getConnectTimeoutSeconds() { return connectTimeoutSeconds; }
    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) { this.connectTimeoutSeconds = connectTimeoutSeconds; }
    public int getReadTimeoutSeconds() { return readTimeoutSeconds; }
    public void setReadTimeoutSeconds(int readTimeoutSeconds) { this.readTimeoutSeconds = readTimeoutSeconds; }
    public int getReadRetryAttempts() { return readRetryAttempts; }
    public void setReadRetryAttempts(int readRetryAttempts) { this.readRetryAttempts = readRetryAttempts; }
    public int getAuthRefreshSkewMinutes() { return authRefreshSkewMinutes; }
    public void setAuthRefreshSkewMinutes(int authRefreshSkewMinutes) { this.authRefreshSkewMinutes = authRefreshSkewMinutes; }
    public int getMaximumAcceptedEtaMinutes() { return maximumAcceptedEtaMinutes; }
    public void setMaximumAcceptedEtaMinutes(int maximumAcceptedEtaMinutes) { this.maximumAcceptedEtaMinutes = maximumAcceptedEtaMinutes; }
}
