package in.craves.integration.refund;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "craves.refund")
public class RefundWorkflowProperties {
    private boolean consumerEnabled;
    private boolean providerExecutionEnabled;
    private boolean reconciliationEnabled;
    private boolean statusPublisherEnabled;
    private boolean productionProviderExecutionApproved;
    private boolean productionReconciliationApproved;
    private String fullyQualifiedNamespace = "";
    private String connectionString = "";
    private String topicName = "craves-domain-events";
    private String subscriptionName = "integration-service-refund-requested";
    private int maxConcurrentMessages = 2;
    private int prefetchCount = 4;
    private int maxDeliveryAttempts = 5;
    private int workerBatchSize = 20;
    private long workerFixedDelayMs = 30000;
    private int maxProviderAttempts = 8;
    private int retryBaseDelaySeconds = 60;
    private int staleLockSeconds = 300;
    private int statusOutboxBatchSize = 20;
    private long statusOutboxFixedDelayMs = 5000;

    public boolean isConsumerEnabled() { return consumerEnabled; }
    public void setConsumerEnabled(boolean consumerEnabled) { this.consumerEnabled = consumerEnabled; }
    public boolean isProviderExecutionEnabled() { return providerExecutionEnabled; }
    public void setProviderExecutionEnabled(boolean providerExecutionEnabled) { this.providerExecutionEnabled = providerExecutionEnabled; }
    public boolean isReconciliationEnabled() { return reconciliationEnabled; }
    public void setReconciliationEnabled(boolean reconciliationEnabled) { this.reconciliationEnabled = reconciliationEnabled; }
    public boolean isStatusPublisherEnabled() { return statusPublisherEnabled; }
    public void setStatusPublisherEnabled(boolean statusPublisherEnabled) { this.statusPublisherEnabled = statusPublisherEnabled; }
    public boolean isProductionProviderExecutionApproved() { return productionProviderExecutionApproved; }
    public void setProductionProviderExecutionApproved(boolean productionProviderExecutionApproved) {
        this.productionProviderExecutionApproved = productionProviderExecutionApproved;
    }
    public boolean isProductionReconciliationApproved() { return productionReconciliationApproved; }
    public void setProductionReconciliationApproved(boolean productionReconciliationApproved) {
        this.productionReconciliationApproved = productionReconciliationApproved;
    }
    public String getFullyQualifiedNamespace() { return fullyQualifiedNamespace; }
    public void setFullyQualifiedNamespace(String fullyQualifiedNamespace) { this.fullyQualifiedNamespace = fullyQualifiedNamespace; }
    public String getConnectionString() { return connectionString; }
    public void setConnectionString(String connectionString) { this.connectionString = connectionString; }
    public String getTopicName() { return topicName; }
    public void setTopicName(String topicName) { this.topicName = topicName; }
    public String getSubscriptionName() { return subscriptionName; }
    public void setSubscriptionName(String subscriptionName) { this.subscriptionName = subscriptionName; }
    public int getMaxConcurrentMessages() { return maxConcurrentMessages; }
    public void setMaxConcurrentMessages(int maxConcurrentMessages) { this.maxConcurrentMessages = maxConcurrentMessages; }
    public int getPrefetchCount() { return prefetchCount; }
    public void setPrefetchCount(int prefetchCount) { this.prefetchCount = prefetchCount; }
    public int getMaxDeliveryAttempts() { return maxDeliveryAttempts; }
    public void setMaxDeliveryAttempts(int maxDeliveryAttempts) { this.maxDeliveryAttempts = maxDeliveryAttempts; }
    public int getWorkerBatchSize() { return workerBatchSize; }
    public void setWorkerBatchSize(int workerBatchSize) { this.workerBatchSize = workerBatchSize; }
    public long getWorkerFixedDelayMs() { return workerFixedDelayMs; }
    public void setWorkerFixedDelayMs(long workerFixedDelayMs) { this.workerFixedDelayMs = workerFixedDelayMs; }
    public int getMaxProviderAttempts() { return maxProviderAttempts; }
    public void setMaxProviderAttempts(int maxProviderAttempts) { this.maxProviderAttempts = maxProviderAttempts; }
    public int getRetryBaseDelaySeconds() { return retryBaseDelaySeconds; }
    public void setRetryBaseDelaySeconds(int retryBaseDelaySeconds) { this.retryBaseDelaySeconds = retryBaseDelaySeconds; }
    public int getStaleLockSeconds() { return staleLockSeconds; }
    public void setStaleLockSeconds(int staleLockSeconds) { this.staleLockSeconds = staleLockSeconds; }
    public int getStatusOutboxBatchSize() { return statusOutboxBatchSize; }
    public void setStatusOutboxBatchSize(int statusOutboxBatchSize) { this.statusOutboxBatchSize = statusOutboxBatchSize; }
    public long getStatusOutboxFixedDelayMs() { return statusOutboxFixedDelayMs; }
    public void setStatusOutboxFixedDelayMs(long statusOutboxFixedDelayMs) { this.statusOutboxFixedDelayMs = statusOutboxFixedDelayMs; }

    public int validatedMaxConcurrentMessages() { return positive(maxConcurrentMessages, 2); }
    public int validatedPrefetchCount() { return Math.max(0, prefetchCount); }
    public int validatedMaxDeliveryAttempts() { return positive(maxDeliveryAttempts, 5); }
    public int validatedWorkerBatchSize() { return positive(workerBatchSize, 20); }
    public int validatedMaxProviderAttempts() { return positive(maxProviderAttempts, 8); }
    public int validatedRetryBaseDelaySeconds() { return positive(retryBaseDelaySeconds, 60); }
    public int validatedStaleLockSeconds() { return positive(staleLockSeconds, 300); }
    public int validatedStatusOutboxBatchSize() { return positive(statusOutboxBatchSize, 20); }
    public Duration maxAutoLockRenewDuration() { return Duration.ofMinutes(5); }

    private static int positive(int value, int fallback) {
        return value > 0 ? value : fallback;
    }
}
