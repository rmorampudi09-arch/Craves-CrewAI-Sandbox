package in.craves.subscription.capacity;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "craves.subscription.capacity")
public class CapacityProperties {
    private int holdMinutes = 15;
    private int projectionHorizonDays = 180;
    private boolean projectionSchedulerEnabled = false;
    private int projectionBatchSize = 50;
    private long projectionFixedDelayMs = 60_000L;

    public int getHoldMinutes() {
        return holdMinutes;
    }

    public void setHoldMinutes(int holdMinutes) {
        this.holdMinutes = bounded(holdMinutes, 1, 120, "holdMinutes");
    }

    public int getProjectionHorizonDays() {
        return projectionHorizonDays;
    }

    public void setProjectionHorizonDays(int projectionHorizonDays) {
        this.projectionHorizonDays = bounded(projectionHorizonDays, 30, 730, "projectionHorizonDays");
    }

    public boolean isProjectionSchedulerEnabled() {
        return projectionSchedulerEnabled;
    }

    public void setProjectionSchedulerEnabled(boolean projectionSchedulerEnabled) {
        this.projectionSchedulerEnabled = projectionSchedulerEnabled;
    }

    public int getProjectionBatchSize() {
        return projectionBatchSize;
    }

    public void setProjectionBatchSize(int projectionBatchSize) {
        this.projectionBatchSize = bounded(projectionBatchSize, 1, 500, "projectionBatchSize");
    }

    public long getProjectionFixedDelayMs() {
        return projectionFixedDelayMs;
    }

    public void setProjectionFixedDelayMs(long projectionFixedDelayMs) {
        if (projectionFixedDelayMs < 10_000L || projectionFixedDelayMs > 3_600_000L) {
            throw new IllegalArgumentException("projectionFixedDelayMs must be between 10000 and 3600000");
        }
        this.projectionFixedDelayMs = projectionFixedDelayMs;
    }

    private static int bounded(int value, int minimum, int maximum, String name) {
        if (value < minimum || value > maximum) {
            throw new IllegalArgumentException(name + " must be between " + minimum + " and " + maximum);
        }
        return value;
    }
}
