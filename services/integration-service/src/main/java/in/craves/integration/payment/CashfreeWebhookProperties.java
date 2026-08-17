package in.craves.integration.payment;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "craves.cashfree.webhook")
public class CashfreeWebhookProperties {
    private boolean workerEnabled = false;
    private int batchSize = 20;
    private long fixedDelayMs = 2000;
    private int maxAttempts = 10;
    private int staleMinutes = 5;
    private int retryBaseSeconds = 5;

    @PostConstruct
    void validate() {
        if (batchSize < 1 || batchSize > 200) {
            throw new IllegalStateException("Cashfree webhook batchSize must be between 1 and 200");
        }
        if (fixedDelayMs < 500) {
            throw new IllegalStateException("Cashfree webhook fixedDelayMs must be at least 500");
        }
        if (maxAttempts < 1 || maxAttempts > 50) {
            throw new IllegalStateException("Cashfree webhook maxAttempts must be between 1 and 50");
        }
        if (staleMinutes < 1 || staleMinutes > 60) {
            throw new IllegalStateException("Cashfree webhook staleMinutes must be between 1 and 60");
        }
        if (retryBaseSeconds < 1 || retryBaseSeconds > 600) {
            throw new IllegalStateException("Cashfree webhook retryBaseSeconds must be between 1 and 600");
        }
    }

    public boolean isWorkerEnabled() { return workerEnabled; }
    public void setWorkerEnabled(boolean workerEnabled) { this.workerEnabled = workerEnabled; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public long getFixedDelayMs() { return fixedDelayMs; }
    public void setFixedDelayMs(long fixedDelayMs) { this.fixedDelayMs = fixedDelayMs; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public int getStaleMinutes() { return staleMinutes; }
    public void setStaleMinutes(int staleMinutes) { this.staleMinutes = staleMinutes; }
    public int getRetryBaseSeconds() { return retryBaseSeconds; }
    public void setRetryBaseSeconds(int retryBaseSeconds) { this.retryBaseSeconds = retryBaseSeconds; }
}
