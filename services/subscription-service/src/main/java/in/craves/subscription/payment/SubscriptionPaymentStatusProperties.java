package in.craves.subscription.payment;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "craves.subscription.payment-status-consumer")
public class SubscriptionPaymentStatusProperties {
    private boolean enabled = false;
    private String fullyQualifiedNamespace = "";
    private String connectionString = "";
    private String topicName = "craves-domain-events";
    private String subscriptionName = "subscription-service-payment-status-changed";
    private int maxConcurrentMessages = 2;
    private int prefetchCount = 4;
    private int maxDeliveryAttempts = 5;

    @PostConstruct
    void validate() {
        if (maxConcurrentMessages < 1 || maxConcurrentMessages > 32) {
            throw new IllegalStateException("Payment status maxConcurrentMessages must be between 1 and 32");
        }
        if (prefetchCount < 0 || prefetchCount > 500 || maxDeliveryAttempts < 1 || maxDeliveryAttempts > 20) {
            throw new IllegalStateException("Payment status consumer limits are invalid");
        }
        if (enabled && !StringUtils.hasText(connectionString) && !StringUtils.hasText(fullyQualifiedNamespace)) {
            throw new IllegalStateException("Service Bus configuration is required when payment status consumer is enabled");
        }
        if (enabled && (!StringUtils.hasText(topicName) || !StringUtils.hasText(subscriptionName))) {
            throw new IllegalStateException("Payment status topic and subscription are required");
        }
    }

    public Duration maxAutoLockRenewDuration() { return Duration.ofMinutes(5); }
    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public String getFullyQualifiedNamespace() { return fullyQualifiedNamespace; }
    public void setFullyQualifiedNamespace(String value) { this.fullyQualifiedNamespace = value; }
    public String getConnectionString() { return connectionString; }
    public void setConnectionString(String value) { this.connectionString = value; }
    public String getTopicName() { return topicName; }
    public void setTopicName(String value) { this.topicName = value; }
    public String getSubscriptionName() { return subscriptionName; }
    public void setSubscriptionName(String value) { this.subscriptionName = value; }
    public int getMaxConcurrentMessages() { return maxConcurrentMessages; }
    public void setMaxConcurrentMessages(int value) { this.maxConcurrentMessages = value; }
    public int getPrefetchCount() { return prefetchCount; }
    public void setPrefetchCount(int value) { this.prefetchCount = value; }
    public int getMaxDeliveryAttempts() { return maxDeliveryAttempts; }
    public void setMaxDeliveryAttempts(int value) { this.maxDeliveryAttempts = value; }
}
