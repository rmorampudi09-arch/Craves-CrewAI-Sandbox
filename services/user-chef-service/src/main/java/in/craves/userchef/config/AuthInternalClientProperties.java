package in.craves.userchef.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "craves.internal")
public class AuthInternalClientProperties {
    private String authServiceBaseUrl;
    private String serviceSecret;
    private String notificationServiceBaseUrl;
    private String notificationServiceKey;
    private boolean notificationDirectDispatchEnabled = true;

    public String getAuthServiceBaseUrl() {
        return authServiceBaseUrl;
    }

    public void setAuthServiceBaseUrl(String authServiceBaseUrl) {
        this.authServiceBaseUrl = authServiceBaseUrl;
    }

    public String getServiceSecret() {
        return serviceSecret;
    }

    public void setServiceSecret(String serviceSecret) {
        this.serviceSecret = serviceSecret;
    }

    public String getNotificationServiceBaseUrl() {
        return notificationServiceBaseUrl;
    }

    public void setNotificationServiceBaseUrl(String notificationServiceBaseUrl) {
        this.notificationServiceBaseUrl = notificationServiceBaseUrl;
    }

    public String getNotificationServiceKey() {
        return notificationServiceKey;
    }

    public void setNotificationServiceKey(String notificationServiceKey) {
        this.notificationServiceKey = notificationServiceKey;
    }

    public boolean isNotificationDirectDispatchEnabled() {
        return notificationDirectDispatchEnabled;
    }

    public void setNotificationDirectDispatchEnabled(boolean notificationDirectDispatchEnabled) {
        this.notificationDirectDispatchEnabled = notificationDirectDispatchEnabled;
    }
}
