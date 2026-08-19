package in.craves.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "craves.production")
public class ProductionReadinessProperties {
    private boolean requireRedis = true;
    private boolean failOnMissingSecrets = true;

    public boolean isRequireRedis() {
        return requireRedis;
    }

    public void setRequireRedis(boolean requireRedis) {
        this.requireRedis = requireRedis;
    }

    public boolean isFailOnMissingSecrets() {
        return failOnMissingSecrets;
    }

    public void setFailOnMissingSecrets(boolean failOnMissingSecrets) {
        this.failOnMissingSecrets = failOnMissingSecrets;
    }
}
