package in.craves.userchef.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "craves.internal.notice-dispatcher")
public class ChefNoticeDispatcherProperties {
    private boolean enabled = false;
    private boolean directDispatchEnabled = false;
    private long fixedDelayMs = 30000;
    private int batchSize = 25;
    private int maxAttempts = 5;
    private int retryBaseDelaySeconds = 60;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isDirectDispatchEnabled() {
        return directDispatchEnabled;
    }

    public void setDirectDispatchEnabled(boolean directDispatchEnabled) {
        this.directDispatchEnabled = directDispatchEnabled;
    }

    public long getFixedDelayMs() {
        return fixedDelayMs;
    }

    public void setFixedDelayMs(long fixedDelayMs) {
        this.fixedDelayMs = fixedDelayMs;
    }

    public int getBatchSize() {
        return batchSize;
    }

    public void setBatchSize(int batchSize) {
        this.batchSize = batchSize;
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getRetryBaseDelaySeconds() {
        return retryBaseDelaySeconds;
    }

    public void setRetryBaseDelaySeconds(int retryBaseDelaySeconds) {
        this.retryBaseDelaySeconds = retryBaseDelaySeconds;
    }
}
