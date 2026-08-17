package in.craves.subscription.billing;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "craves.subscription.billing")
public class SubscriptionBillingProperties {
    private boolean generatorEnabled = false;
    private boolean publisherEnabled = false;
    private int batchSize = 50;
    private int horizonDays = 7;
    private int staleLockMinutes = 10;
    private long generatorFixedDelayMs = 60000;
    private long publisherFixedDelayMs = 5000;
    private int maxPublishAttempts = 10;
    private String fullyQualifiedNamespace = "";
    private String connectionString = "";
    private String topicName = "craves-domain-events";

    @PostConstruct
    void validate() {
        if (batchSize < 1 || batchSize > 500) {
            throw new IllegalStateException("Subscription billing batchSize must be between 1 and 500");
        }
        if (horizonDays < 0 || horizonDays > 31) {
            throw new IllegalStateException("Subscription billing horizonDays must be between 0 and 31");
        }
        if (staleLockMinutes < 1 || staleLockMinutes > 120) {
            throw new IllegalStateException("Subscription billing staleLockMinutes must be between 1 and 120");
        }
        if (generatorFixedDelayMs < 1000 || publisherFixedDelayMs < 1000) {
            throw new IllegalStateException("Subscription billing delays must be at least 1000 ms");
        }
        if (maxPublishAttempts < 1 || maxPublishAttempts > 100) {
            throw new IllegalStateException("Subscription billing maxPublishAttempts must be between 1 and 100");
        }
        if (publisherEnabled && connectionString.isBlank() && fullyQualifiedNamespace.isBlank()) {
            throw new IllegalStateException("Service Bus configuration is required when billing publisher is enabled");
        }
    }

    public boolean isGeneratorEnabled() { return generatorEnabled; }
    public void setGeneratorEnabled(boolean generatorEnabled) { this.generatorEnabled = generatorEnabled; }
    public boolean isPublisherEnabled() { return publisherEnabled; }
    public void setPublisherEnabled(boolean publisherEnabled) { this.publisherEnabled = publisherEnabled; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getHorizonDays() { return horizonDays; }
    public void setHorizonDays(int horizonDays) { this.horizonDays = horizonDays; }
    public int getStaleLockMinutes() { return staleLockMinutes; }
    public void setStaleLockMinutes(int staleLockMinutes) { this.staleLockMinutes = staleLockMinutes; }
    public long getGeneratorFixedDelayMs() { return generatorFixedDelayMs; }
    public void setGeneratorFixedDelayMs(long generatorFixedDelayMs) { this.generatorFixedDelayMs = generatorFixedDelayMs; }
    public long getPublisherFixedDelayMs() { return publisherFixedDelayMs; }
    public void setPublisherFixedDelayMs(long publisherFixedDelayMs) { this.publisherFixedDelayMs = publisherFixedDelayMs; }
    public int getMaxPublishAttempts() { return maxPublishAttempts; }
    public void setMaxPublishAttempts(int maxPublishAttempts) { this.maxPublishAttempts = maxPublishAttempts; }
    public String getFullyQualifiedNamespace() { return fullyQualifiedNamespace; }
    public void setFullyQualifiedNamespace(String fullyQualifiedNamespace) { this.fullyQualifiedNamespace = fullyQualifiedNamespace; }
    public String getConnectionString() { return connectionString; }
    public void setConnectionString(String connectionString) { this.connectionString = connectionString; }
    public String getTopicName() { return topicName; }
    public void setTopicName(String topicName) { this.topicName = topicName; }
}
