package in.craves.order.config;

import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

@ConfigurationProperties(prefix = "craves.domain-events.outbox")
public class DomainEventOutboxProperties {
    private boolean enabled = false;
    private long fixedDelayMs = 5000;
    private int batchSize = 20;
    private int maxAttempts = 10;
    private int retryBaseDelaySeconds = 5;
    private int staleLockSeconds = 300;
    private Set<String> enabledEventTypes = new LinkedHashSet<>(Set.of("CHEF_ACCEPTED_ORDER"));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
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

    public int getStaleLockSeconds() {
        return staleLockSeconds;
    }

    public void setStaleLockSeconds(int staleLockSeconds) {
        this.staleLockSeconds = staleLockSeconds;
    }

    public Set<String> getEnabledEventTypes() {
        return enabledEventTypes;
    }

    public void setEnabledEventTypes(Set<String> enabledEventTypes) {
        this.enabledEventTypes = enabledEventTypes == null
            ? new LinkedHashSet<>()
            : new LinkedHashSet<>(enabledEventTypes);
    }

    public Set<String> normalizedEnabledEventTypes() {
        Set<String> normalized = new LinkedHashSet<>();
        for (String eventType : enabledEventTypes) {
            if (StringUtils.hasText(eventType)) {
                normalized.add(eventType.trim().toUpperCase());
            }
        }
        return Set.copyOf(normalized);
    }
}
