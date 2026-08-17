package in.craves.order.subscription;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "craves.subscription-orders")
public class SubscriptionOrderProperties {
    private boolean consumerEnabled = false;
    private boolean callbackWorkerEnabled = false;
    private String fullyQualifiedNamespace = "";
    private String connectionString = "";
    private String topicName = "craves-domain-events";
    private String subscriptionName = "order-service-subscription-order-requested";
    private String subscriptionServiceBaseUrl = "";
    private String internalAccessValue = "";
    private int maxConcurrentMessages = 2;
    private int prefetchCount = 4;
    private int maxDeliveryAttempts = 5;
    private int callbackBatchSize = 20;
    private int callbackMaxAttempts = 10;
    private int callbackStaleMinutes = 5;
    private long callbackFixedDelayMs = 5000;

    @PostConstruct
    void validate() {
        if (consumerEnabled && !StringUtils.hasText(connectionString) && !StringUtils.hasText(fullyQualifiedNamespace)) {
            throw new IllegalStateException("Service Bus configuration is required when subscription order consumer is enabled");
        }
        if (consumerEnabled && (!StringUtils.hasText(topicName) || !StringUtils.hasText(subscriptionName))) {
            throw new IllegalStateException("Subscription order topic and subscription are required");
        }
        if (callbackWorkerEnabled) {
            if (!StringUtils.hasText(subscriptionServiceBaseUrl) || !subscriptionServiceBaseUrl.startsWith("https://")) {
                throw new IllegalStateException("HTTPS Subscription Service URL is required for order callbacks");
            }
            if (!StringUtils.hasText(internalAccessValue)) {
                throw new IllegalStateException("Internal service secret is required for order callbacks");
            }
        }
        if (maxConcurrentMessages < 1 || maxConcurrentMessages > 32 || prefetchCount < 0 || prefetchCount > 500
            || maxDeliveryAttempts < 1 || maxDeliveryAttempts > 20) {
            throw new IllegalStateException("Subscription order consumer limits are invalid");
        }
        if (callbackBatchSize < 1 || callbackBatchSize > 500 || callbackMaxAttempts < 1 || callbackMaxAttempts > 100
            || callbackStaleMinutes < 1 || callbackStaleMinutes > 120 || callbackFixedDelayMs < 1000) {
            throw new IllegalStateException("Subscription order callback limits are invalid");
        }
    }

    public Duration maxAutoLockRenewDuration() { return Duration.ofMinutes(5); }
    public boolean isConsumerEnabled() { return consumerEnabled; }
    public void setConsumerEnabled(boolean value) { this.consumerEnabled = value; }
    public boolean isCallbackWorkerEnabled() { return callbackWorkerEnabled; }
    public void setCallbackWorkerEnabled(boolean value) { this.callbackWorkerEnabled = value; }
    public String getFullyQualifiedNamespace() { return fullyQualifiedNamespace; }
    public void setFullyQualifiedNamespace(String value) { this.fullyQualifiedNamespace = value; }
    public String getConnectionString() { return connectionString; }
    public void setConnectionString(String value) { this.connectionString = value; }
    public String getTopicName() { return topicName; }
    public void setTopicName(String value) { this.topicName = value; }
    public String getSubscriptionName() { return subscriptionName; }
    public void setSubscriptionName(String value) { this.subscriptionName = value; }
    public String getSubscriptionServiceBaseUrl() { return subscriptionServiceBaseUrl; }
    public void setSubscriptionServiceBaseUrl(String value) { this.subscriptionServiceBaseUrl = value; }
    public String getInternalAccessValue() { return internalAccessValue; }
    public void setInternalAccessValue(String value) { this.internalAccessValue = value; }
    public int getMaxConcurrentMessages() { return maxConcurrentMessages; }
    public void setMaxConcurrentMessages(int value) { this.maxConcurrentMessages = value; }
    public int getPrefetchCount() { return prefetchCount; }
    public void setPrefetchCount(int value) { this.prefetchCount = value; }
    public int getMaxDeliveryAttempts() { return maxDeliveryAttempts; }
    public void setMaxDeliveryAttempts(int value) { this.maxDeliveryAttempts = value; }
    public int getCallbackBatchSize() { return callbackBatchSize; }
    public void setCallbackBatchSize(int value) { this.callbackBatchSize = value; }
    public int getCallbackMaxAttempts() { return callbackMaxAttempts; }
    public void setCallbackMaxAttempts(int value) { this.callbackMaxAttempts = value; }
    public int getCallbackStaleMinutes() { return callbackStaleMinutes; }
    public void setCallbackStaleMinutes(int value) { this.callbackStaleMinutes = value; }
    public long getCallbackFixedDelayMs() { return callbackFixedDelayMs; }
    public void setCallbackFixedDelayMs(long value) { this.callbackFixedDelayMs = value; }
}
