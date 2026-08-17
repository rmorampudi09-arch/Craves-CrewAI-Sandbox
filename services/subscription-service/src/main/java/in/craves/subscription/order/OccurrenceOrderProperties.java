package in.craves.subscription.order;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConfigurationProperties(prefix = "craves.subscription.order-dispatch")
public class OccurrenceOrderProperties {
    private boolean requestWorkerEnabled = false;
    private boolean publisherEnabled = false;
    private int leadHours = -1;
    private int batchSize = 50;
    private int staleLockMinutes = 10;
    private int maxPublishAttempts = 10;
    private long requestFixedDelayMs = 30000;
    private long publisherFixedDelayMs = 5000;
    private String fullyQualifiedNamespace = "";
    private String connectionString = "";
    private String topicName = "craves-domain-events";
    private String internalAccessValue = "";

    @PostConstruct
    void validate() {
        if (requestWorkerEnabled && (leadHours < 0 || leadHours > 168)) {
            throw new IllegalStateException("An approved order dispatch leadHours value from 0 to 168 is required");
        }
        if (batchSize < 1 || batchSize > 500 || staleLockMinutes < 1 || staleLockMinutes > 120) {
            throw new IllegalStateException("Occurrence order dispatch batch or lease settings are invalid");
        }
        if (maxPublishAttempts < 1 || maxPublishAttempts > 100) {
            throw new IllegalStateException("Occurrence order maxPublishAttempts must be between 1 and 100");
        }
        if (requestFixedDelayMs < 1000 || publisherFixedDelayMs < 1000) {
            throw new IllegalStateException("Occurrence order worker delays must be at least 1000 ms");
        }
        if (publisherEnabled && !StringUtils.hasText(connectionString) && !StringUtils.hasText(fullyQualifiedNamespace)) {
            throw new IllegalStateException("Service Bus configuration is required when occurrence order publisher is enabled");
        }
    }

    public boolean validInternalAccess(String supplied) {
        return StringUtils.hasText(internalAccessValue) && internalAccessValue.equals(supplied);
    }

    public boolean isRequestWorkerEnabled() { return requestWorkerEnabled; }
    public void setRequestWorkerEnabled(boolean value) { this.requestWorkerEnabled = value; }
    public boolean isPublisherEnabled() { return publisherEnabled; }
    public void setPublisherEnabled(boolean value) { this.publisherEnabled = value; }
    public int getLeadHours() { return leadHours; }
    public void setLeadHours(int value) { this.leadHours = value; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int value) { this.batchSize = value; }
    public int getStaleLockMinutes() { return staleLockMinutes; }
    public void setStaleLockMinutes(int value) { this.staleLockMinutes = value; }
    public int getMaxPublishAttempts() { return maxPublishAttempts; }
    public void setMaxPublishAttempts(int value) { this.maxPublishAttempts = value; }
    public long getRequestFixedDelayMs() { return requestFixedDelayMs; }
    public void setRequestFixedDelayMs(long value) { this.requestFixedDelayMs = value; }
    public long getPublisherFixedDelayMs() { return publisherFixedDelayMs; }
    public void setPublisherFixedDelayMs(long value) { this.publisherFixedDelayMs = value; }
    public String getFullyQualifiedNamespace() { return fullyQualifiedNamespace; }
    public void setFullyQualifiedNamespace(String value) { this.fullyQualifiedNamespace = value; }
    public String getConnectionString() { return connectionString; }
    public void setConnectionString(String value) { this.connectionString = value; }
    public String getTopicName() { return topicName; }
    public void setTopicName(String value) { this.topicName = value; }
    public String getInternalAccessValue() { return internalAccessValue; }
    public void setInternalAccessValue(String value) { this.internalAccessValue = value; }
}
