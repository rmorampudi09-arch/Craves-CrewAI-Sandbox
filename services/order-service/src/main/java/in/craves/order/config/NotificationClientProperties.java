package in.craves.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "craves.notification")
public class NotificationClientProperties {
    private String baseUrl;
    private String accessValue;
    private boolean directDispatchEnabled = false;

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getAccessValue() {
        return accessValue;
    }

    public void setAccessValue(String accessValue) {
        this.accessValue = accessValue;
    }

    public boolean isDirectDispatchEnabled() {
        return directDispatchEnabled;
    }

    public void setDirectDispatchEnabled(boolean directDispatchEnabled) {
        this.directDispatchEnabled = directDispatchEnabled;
    }
}
