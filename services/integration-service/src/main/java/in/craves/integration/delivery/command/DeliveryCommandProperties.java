package in.craves.integration.delivery.command;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "craves.delivery-command")
public class DeliveryCommandProperties {
    private boolean enabled = false;
    private boolean reconciliationEnabled = false;
    private boolean webhookProcessingEnabled = false;
    private boolean trackingReconciliationEnabled = false;
    private boolean statusPublisherEnabled = false;
    private String fullyQualifiedNamespace = "";
    private String connectionString = "";
    private String topicName = "craves-domain-events";
    private String chefAcceptedSubscriptionName = "integration-service-chef-accepted";
    private String queueName = "delivery-command";
    private int leadTimeMinutes = 10;
    private int quoteTimeoutSeconds = 4;
    private int maxProviderAttempts = 3;
    private int maxDeliveryAttempts = 5;
    private int maxConcurrentMessages = 4;
    private int prefetchCount = 8;
    private int maxAutoLockRenewMinutes = 5;
    private int outboxBatchSize = 20;
    private long outboxPublishIntervalMs = 5000;
    private int reconciliationBatchSize = 20;
    private long reconciliationIntervalMs = 15000;
    private int maxReconciliationAttempts = 20;
    private int reconciliationRetryBaseSeconds = 30;
    private int reconciliationStaleMinutes = 10;
    private int webhookBatchSize = 20;
    private long webhookProcessingIntervalMs = 2000;
    private int maxWebhookAttempts = 10;
    private int webhookRetryBaseSeconds = 5;
    private int webhookStaleMinutes = 5;
    private int trackingBatchSize = 20;
    private long trackingReconciliationIntervalMs = 15000;
    private int trackingPollSeconds = 60;
    private int maxTrackingAttempts = 20;
    private int trackingRetryBaseSeconds = 30;
    private int trackingStaleMinutes = 5;

    @PostConstruct
    void validate() {
        if (leadTimeMinutes < 0 || leadTimeMinutes > 120) {
            throw new IllegalStateException("Delivery command leadTimeMinutes must be between 0 and 120");
        }
        if (quoteTimeoutSeconds < 1 || quoteTimeoutSeconds > 30) {
            throw new IllegalStateException("Delivery command quoteTimeoutSeconds must be between 1 and 30");
        }
        if (maxProviderAttempts < 1 || maxProviderAttempts > 10) {
            throw new IllegalStateException("Delivery command maxProviderAttempts must be between 1 and 10");
        }
        if (maxDeliveryAttempts < 1 || maxDeliveryAttempts > 20) {
            throw new IllegalStateException("Delivery command maxDeliveryAttempts must be between 1 and 20");
        }
        if (maxConcurrentMessages < 1 || maxConcurrentMessages > 64) {
            throw new IllegalStateException("Delivery command maxConcurrentMessages must be between 1 and 64");
        }
        if (prefetchCount < 0 || prefetchCount > 1000) {
            throw new IllegalStateException("Delivery command prefetchCount must be between 0 and 1000");
        }
        if (maxAutoLockRenewMinutes < 1 || maxAutoLockRenewMinutes > 60) {
            throw new IllegalStateException("Delivery command maxAutoLockRenewMinutes must be between 1 and 60");
        }
        if (outboxBatchSize < 1 || outboxBatchSize > 500) {
            throw new IllegalStateException("Delivery command outboxBatchSize must be between 1 and 500");
        }
        if (outboxPublishIntervalMs < 1000) {
            throw new IllegalStateException("Delivery command outboxPublishIntervalMs must be at least 1000");
        }
        if (reconciliationBatchSize < 1 || reconciliationBatchSize > 500) {
            throw new IllegalStateException("Delivery reconciliationBatchSize must be between 1 and 500");
        }
        if (reconciliationIntervalMs < 1000) {
            throw new IllegalStateException("Delivery reconciliationIntervalMs must be at least 1000");
        }
        if (maxReconciliationAttempts < 1 || maxReconciliationAttempts > 100) {
            throw new IllegalStateException("Delivery maxReconciliationAttempts must be between 1 and 100");
        }
        if (reconciliationRetryBaseSeconds < 1 || reconciliationRetryBaseSeconds > 3600) {
            throw new IllegalStateException(
                "Delivery reconciliationRetryBaseSeconds must be between 1 and 3600"
            );
        }
        if (reconciliationStaleMinutes < 1 || reconciliationStaleMinutes > 120) {
            throw new IllegalStateException("Delivery reconciliationStaleMinutes must be between 1 and 120");
        }
        validateBatch(webhookBatchSize, "webhookBatchSize");
        validateInterval(webhookProcessingIntervalMs, "webhookProcessingIntervalMs");
        validateAttempts(maxWebhookAttempts, "maxWebhookAttempts");
        validateRetry(webhookRetryBaseSeconds, "webhookRetryBaseSeconds");
        validateStale(webhookStaleMinutes, "webhookStaleMinutes");
        validateBatch(trackingBatchSize, "trackingBatchSize");
        validateInterval(trackingReconciliationIntervalMs, "trackingReconciliationIntervalMs");
        if (trackingPollSeconds < 10 || trackingPollSeconds > 3600) {
            throw new IllegalStateException("Delivery trackingPollSeconds must be between 10 and 3600");
        }
        validateAttempts(maxTrackingAttempts, "maxTrackingAttempts");
        validateRetry(trackingRetryBaseSeconds, "trackingRetryBaseSeconds");
        validateStale(trackingStaleMinutes, "trackingStaleMinutes");

        if (enabled || statusPublisherEnabled) {
            if (!StringUtils.hasText(connectionString) && !StringUtils.hasText(fullyQualifiedNamespace)) {
                throw new IllegalStateException(
                    "SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE or SERVICE_BUS_CONNECTION_STRING is required when delivery messaging is enabled"
                );
            }
            requireText(topicName, "topicName");
            requireText(chefAcceptedSubscriptionName, "chefAcceptedSubscriptionName");
            requireText(queueName, "queueName");
        }
    }

    private static void validateBatch(int value, String name) {
        if (value < 1 || value > 500) {
            throw new IllegalStateException("Delivery " + name + " must be between 1 and 500");
        }
    }

    private static void validateInterval(long value, String name) {
        if (value < 1000) {
            throw new IllegalStateException("Delivery " + name + " must be at least 1000");
        }
    }

    private static void validateAttempts(int value, String name) {
        if (value < 1 || value > 100) {
            throw new IllegalStateException("Delivery " + name + " must be between 1 and 100");
        }
    }

    private static void validateRetry(int value, String name) {
        if (value < 1 || value > 3600) {
            throw new IllegalStateException("Delivery " + name + " must be between 1 and 3600");
        }
    }

    private static void validateStale(int value, String name) {
        if (value < 1 || value > 120) {
            throw new IllegalStateException("Delivery " + name + " must be between 1 and 120");
        }
    }

    private static void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("Delivery command " + field + " is required");
        }
    }

    public Duration quoteTimeout() { return Duration.ofSeconds(quoteTimeoutSeconds); }
    public Duration maxAutoLockRenewDuration() { return Duration.ofMinutes(maxAutoLockRenewMinutes); }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public boolean isReconciliationEnabled() { return reconciliationEnabled; }
    public void setReconciliationEnabled(boolean reconciliationEnabled) { this.reconciliationEnabled = reconciliationEnabled; }
    public boolean isWebhookProcessingEnabled() { return webhookProcessingEnabled; }
    public void setWebhookProcessingEnabled(boolean webhookProcessingEnabled) { this.webhookProcessingEnabled = webhookProcessingEnabled; }
    public boolean isTrackingReconciliationEnabled() { return trackingReconciliationEnabled; }
    public void setTrackingReconciliationEnabled(boolean trackingReconciliationEnabled) { this.trackingReconciliationEnabled = trackingReconciliationEnabled; }
    public boolean isStatusPublisherEnabled() { return statusPublisherEnabled; }
    public void setStatusPublisherEnabled(boolean statusPublisherEnabled) { this.statusPublisherEnabled = statusPublisherEnabled; }
    public String getFullyQualifiedNamespace() { return fullyQualifiedNamespace; }
    public void setFullyQualifiedNamespace(String fullyQualifiedNamespace) { this.fullyQualifiedNamespace = fullyQualifiedNamespace; }
    public String getConnectionString() { return connectionString; }
    public void setConnectionString(String connectionString) { this.connectionString = connectionString; }
    public String getTopicName() { return topicName; }
    public void setTopicName(String topicName) { this.topicName = topicName; }
    public String getChefAcceptedSubscriptionName() { return chefAcceptedSubscriptionName; }
    public void setChefAcceptedSubscriptionName(String chefAcceptedSubscriptionName) { this.chefAcceptedSubscriptionName = chefAcceptedSubscriptionName; }
    public String getQueueName() { return queueName; }
    public void setQueueName(String queueName) { this.queueName = queueName; }
    public int getLeadTimeMinutes() { return leadTimeMinutes; }
    public void setLeadTimeMinutes(int leadTimeMinutes) { this.leadTimeMinutes = leadTimeMinutes; }
    public int getQuoteTimeoutSeconds() { return quoteTimeoutSeconds; }
    public void setQuoteTimeoutSeconds(int quoteTimeoutSeconds) { this.quoteTimeoutSeconds = quoteTimeoutSeconds; }
    public int getMaxProviderAttempts() { return maxProviderAttempts; }
    public void setMaxProviderAttempts(int maxProviderAttempts) { this.maxProviderAttempts = maxProviderAttempts; }
    public int getMaxDeliveryAttempts() { return maxDeliveryAttempts; }
    public void setMaxDeliveryAttempts(int maxDeliveryAttempts) { this.maxDeliveryAttempts = maxDeliveryAttempts; }
    public int getMaxConcurrentMessages() { return maxConcurrentMessages; }
    public void setMaxConcurrentMessages(int maxConcurrentMessages) { this.maxConcurrentMessages = maxConcurrentMessages; }
    public int getPrefetchCount() { return prefetchCount; }
    public void setPrefetchCount(int prefetchCount) { this.prefetchCount = prefetchCount; }
    public int getMaxAutoLockRenewMinutes() { return maxAutoLockRenewMinutes; }
    public void setMaxAutoLockRenewMinutes(int maxAutoLockRenewMinutes) { this.maxAutoLockRenewMinutes = maxAutoLockRenewMinutes; }
    public int getOutboxBatchSize() { return outboxBatchSize; }
    public void setOutboxBatchSize(int outboxBatchSize) { this.outboxBatchSize = outboxBatchSize; }
    public long getOutboxPublishIntervalMs() { return outboxPublishIntervalMs; }
    public void setOutboxPublishIntervalMs(long outboxPublishIntervalMs) { this.outboxPublishIntervalMs = outboxPublishIntervalMs; }
    public int getReconciliationBatchSize() { return reconciliationBatchSize; }
    public void setReconciliationBatchSize(int reconciliationBatchSize) { this.reconciliationBatchSize = reconciliationBatchSize; }
    public long getReconciliationIntervalMs() { return reconciliationIntervalMs; }
    public void setReconciliationIntervalMs(long reconciliationIntervalMs) { this.reconciliationIntervalMs = reconciliationIntervalMs; }
    public int getMaxReconciliationAttempts() { return maxReconciliationAttempts; }
    public void setMaxReconciliationAttempts(int maxReconciliationAttempts) { this.maxReconciliationAttempts = maxReconciliationAttempts; }
    public int getReconciliationRetryBaseSeconds() { return reconciliationRetryBaseSeconds; }
    public void setReconciliationRetryBaseSeconds(int reconciliationRetryBaseSeconds) { this.reconciliationRetryBaseSeconds = reconciliationRetryBaseSeconds; }
    public int getReconciliationStaleMinutes() { return reconciliationStaleMinutes; }
    public void setReconciliationStaleMinutes(int reconciliationStaleMinutes) { this.reconciliationStaleMinutes = reconciliationStaleMinutes; }
    public int getWebhookBatchSize() { return webhookBatchSize; }
    public void setWebhookBatchSize(int webhookBatchSize) { this.webhookBatchSize = webhookBatchSize; }
    public long getWebhookProcessingIntervalMs() { return webhookProcessingIntervalMs; }
    public void setWebhookProcessingIntervalMs(long webhookProcessingIntervalMs) { this.webhookProcessingIntervalMs = webhookProcessingIntervalMs; }
    public int getMaxWebhookAttempts() { return maxWebhookAttempts; }
    public void setMaxWebhookAttempts(int maxWebhookAttempts) { this.maxWebhookAttempts = maxWebhookAttempts; }
    public int getWebhookRetryBaseSeconds() { return webhookRetryBaseSeconds; }
    public void setWebhookRetryBaseSeconds(int webhookRetryBaseSeconds) { this.webhookRetryBaseSeconds = webhookRetryBaseSeconds; }
    public int getWebhookStaleMinutes() { return webhookStaleMinutes; }
    public void setWebhookStaleMinutes(int webhookStaleMinutes) { this.webhookStaleMinutes = webhookStaleMinutes; }
    public int getTrackingBatchSize() { return trackingBatchSize; }
    public void setTrackingBatchSize(int trackingBatchSize) { this.trackingBatchSize = trackingBatchSize; }
    public long getTrackingReconciliationIntervalMs() { return trackingReconciliationIntervalMs; }
    public void setTrackingReconciliationIntervalMs(long trackingReconciliationIntervalMs) { this.trackingReconciliationIntervalMs = trackingReconciliationIntervalMs; }
    public int getTrackingPollSeconds() { return trackingPollSeconds; }
    public void setTrackingPollSeconds(int trackingPollSeconds) { this.trackingPollSeconds = trackingPollSeconds; }
    public int getMaxTrackingAttempts() { return maxTrackingAttempts; }
    public void setMaxTrackingAttempts(int maxTrackingAttempts) { this.maxTrackingAttempts = maxTrackingAttempts; }
    public int getTrackingRetryBaseSeconds() { return trackingRetryBaseSeconds; }
    public void setTrackingRetryBaseSeconds(int trackingRetryBaseSeconds) { this.trackingRetryBaseSeconds = trackingRetryBaseSeconds; }
    public int getTrackingStaleMinutes() { return trackingStaleMinutes; }
    public void setTrackingStaleMinutes(int trackingStaleMinutes) { this.trackingStaleMinutes = trackingStaleMinutes; }
}
