package in.craves.integration.subscription;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "craves.subscription-payments")
public class SubscriptionPaymentProperties {
    private boolean consumerEnabled = false;
    private boolean statusPublisherEnabled = false;
    private String fullyQualifiedNamespace = "";
    private String connectionString = "";
    private String topicName = "craves-domain-events";
    private String requestSubscriptionName = "integration-service-subscription-payment-requested";
    private String subscriptionServiceBaseUrl = "";
    private int maxConcurrentMessages = 2;
    private int prefetchCount = 4;
    private int maxDeliveryAttempts = 5;
    private int outboxBatchSize = 20;
    private int maxPublishAttempts = 10;
    private int staleLockMinutes = 5;
    private long outboxFixedDelayMs = 5000;

    @PostConstruct
    void validate() {
        if (maxConcurrentMessages < 1 || maxConcurrentMessages > 32) {
            throw new IllegalStateException("Subscription payment maxConcurrentMessages must be between 1 and 32");
        }
        if (prefetchCount < 0 || prefetchCount > 500) {
            throw new IllegalStateException("Subscription payment prefetchCount must be between 0 and 500");
        }
        if (maxDeliveryAttempts < 1 || maxDeliveryAttempts > 20 || maxPublishAttempts < 1 || maxPublishAttempts > 100) {
            throw new IllegalStateException("Subscription payment retry limits are invalid");
        }
        if (outboxBatchSize < 1 || outboxBatchSize > 500 || staleLockMinutes < 1 || staleLockMinutes > 120) {
            throw new IllegalStateException("Subscription payment outbox limits are invalid");
        }
        if (outboxFixedDelayMs < 1000) {
            throw new IllegalStateException("Subscription payment outbox delay must be at least 1000 ms");
        }
        if ((consumerEnabled || statusPublisherEnabled)
            && !StringUtils.hasText(connectionString)
            && !StringUtils.hasText(fullyQualifiedNamespace)) {
            throw new IllegalStateException("Service Bus configuration is required for subscription payments");
        }
        if (consumerEnabled && !StringUtils.hasText(requestSubscriptionName)) {
            throw new IllegalStateException("Subscription payment request subscription is required");
        }
        if (!subscriptionServiceBaseUrl.isBlank() && !subscriptionServiceBaseUrl.startsWith("https://")
            && !subscriptionServiceBaseUrl.startsWith("http://localhost")) {
            throw new IllegalStateException("Subscription Service base URL must use HTTPS");
        }
    }

    public Duration maxAutoLockRenewDuration() { return Duration.ofMinutes(5); }
    public boolean isConsumerEnabled() { return consumerEnabled; }
    public void setConsumerEnabled(boolean consumerEnabled) { this.consumerEnabled = consumerEnabled; }
    public boolean isStatusPublisherEnabled() { return statusPublisherEnabled; }
    public void setStatusPublisherEnabled(boolean statusPublisherEnabled) { this.statusPublisherEnabled = statusPublisherEnabled; }
    public String getFullyQualifiedNamespace() { return fullyQualifiedNamespace; }
    public void setFullyQualifiedNamespace(String value) { this.fullyQualifiedNamespace = value; }
    public String getConnectionString() { return connectionString; }
    public void setConnectionString(String value) { this.connectionString = value; }
    public String getTopicName() { return topicName; }
    public void setTopicName(String topicName) { this.topicName = topicName; }
    public String getRequestSubscriptionName() { return requestSubscriptionName; }
    public void setRequestSubscriptionName(String value) { this.requestSubscriptionName = value; }
    public String getSubscriptionServiceBaseUrl() { return subscriptionServiceBaseUrl; }
    public void setSubscriptionServiceBaseUrl(String value) { this.subscriptionServiceBaseUrl = value; }
    public int getMaxConcurrentMessages() { return maxConcurrentMessages; }
    public void setMaxConcurrentMessages(int value) { this.maxConcurrentMessages = value; }
    public int getPrefetchCount() { return prefetchCount; }
    public void setPrefetchCount(int value) { this.prefetchCount = value; }
    public int getMaxDeliveryAttempts() { return maxDeliveryAttempts; }
    public void setMaxDeliveryAttempts(int value) { this.maxDeliveryAttempts = value; }
    public int getOutboxBatchSize() { return outboxBatchSize; }
    public void setOutboxBatchSize(int value) { this.outboxBatchSize = value; }
    public int getMaxPublishAttempts() { return maxPublishAttempts; }
    public void setMaxPublishAttempts(int value) { this.maxPublishAttempts = value; }
    public int getStaleLockMinutes() { return staleLockMinutes; }
    public void setStaleLockMinutes(int value) { this.staleLockMinutes = value; }
    public long getOutboxFixedDelayMs() { return outboxFixedDelayMs; }
    public void setOutboxFixedDelayMs(long value) { this.outboxFixedDelayMs = value; }
}
