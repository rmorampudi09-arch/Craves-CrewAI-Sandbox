package in.craves.auth.admin;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "craves.admin-account-intervention")
public class AdminAccountInterventionProperties {
    private boolean apiEnabled = false;
    private boolean firebaseWorkerEnabled = false;
    private int batchSize = 20;
    private int maxAttempts = 8;
    private int staleLockMinutes = 5;
    private int retryBaseSeconds = 30;
    private long workerFixedDelayMs = 5000;

    @PostConstruct
    void validate() {
        if (batchSize < 1 || batchSize > 200) {
            throw new IllegalStateException("Admin account intervention batchSize must be between 1 and 200");
        }
        if (maxAttempts < 1 || maxAttempts > 100) {
            throw new IllegalStateException("Admin account intervention maxAttempts must be between 1 and 100");
        }
        if (staleLockMinutes < 1 || staleLockMinutes > 120) {
            throw new IllegalStateException("Admin account intervention staleLockMinutes must be between 1 and 120");
        }
        if (retryBaseSeconds < 1 || retryBaseSeconds > 3600 || workerFixedDelayMs < 1000) {
            throw new IllegalStateException("Admin account intervention retry or worker delay is invalid");
        }
        if (firebaseWorkerEnabled && !apiEnabled) {
            throw new IllegalStateException("Firebase intervention worker cannot run while account intervention API is disabled");
        }
    }

    public boolean isApiEnabled() { return apiEnabled; }
    public void setApiEnabled(boolean apiEnabled) { this.apiEnabled = apiEnabled; }
    public boolean isFirebaseWorkerEnabled() { return firebaseWorkerEnabled; }
    public void setFirebaseWorkerEnabled(boolean firebaseWorkerEnabled) { this.firebaseWorkerEnabled = firebaseWorkerEnabled; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int maxAttempts) { this.maxAttempts = maxAttempts; }
    public int getStaleLockMinutes() { return staleLockMinutes; }
    public void setStaleLockMinutes(int staleLockMinutes) { this.staleLockMinutes = staleLockMinutes; }
    public int getRetryBaseSeconds() { return retryBaseSeconds; }
    public void setRetryBaseSeconds(int retryBaseSeconds) { this.retryBaseSeconds = retryBaseSeconds; }
    public long getWorkerFixedDelayMs() { return workerFixedDelayMs; }
    public void setWorkerFixedDelayMs(long workerFixedDelayMs) { this.workerFixedDelayMs = workerFixedDelayMs; }
}
