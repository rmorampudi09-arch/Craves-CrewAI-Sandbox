package in.craves.subscription.occurrence;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "craves.subscription.occurrence-generator")
public class OccurrenceGeneratorProperties {
    private boolean enabled = false;
    private int batchSize = 50;
    private int horizonDays = 7;
    private int staleLockMinutes = 10;
    private long fixedDelayMs = 60000;

    @PostConstruct
    void validate() {
        if (batchSize < 1 || batchSize > 500) {
            throw new IllegalStateException("Occurrence batchSize must be between 1 and 500");
        }
        if (horizonDays < 0 || horizonDays > 31) {
            throw new IllegalStateException("Occurrence horizonDays must be between 0 and 31");
        }
        if (staleLockMinutes < 1 || staleLockMinutes > 120) {
            throw new IllegalStateException("Occurrence staleLockMinutes must be between 1 and 120");
        }
        if (fixedDelayMs < 1000) {
            throw new IllegalStateException("Occurrence fixedDelayMs must be at least 1000");
        }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getBatchSize() { return batchSize; }
    public void setBatchSize(int batchSize) { this.batchSize = batchSize; }
    public int getHorizonDays() { return horizonDays; }
    public void setHorizonDays(int horizonDays) { this.horizonDays = horizonDays; }
    public int getStaleLockMinutes() { return staleLockMinutes; }
    public void setStaleLockMinutes(int staleLockMinutes) { this.staleLockMinutes = staleLockMinutes; }
    public long getFixedDelayMs() { return fixedDelayMs; }
    public void setFixedDelayMs(long fixedDelayMs) { this.fixedDelayMs = fixedDelayMs; }
}
