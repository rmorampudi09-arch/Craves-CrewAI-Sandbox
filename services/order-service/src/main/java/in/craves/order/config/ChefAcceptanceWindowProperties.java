package in.craves.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "craves.chef-acceptance")
public class ChefAcceptanceWindowProperties {
    private int timeoutMinutes = 30;
    private int firstReminderMinutes = 10;
    private int secondReminderMinutes = 20;
    private boolean workerEnabled = false;
    private long workerFixedDelayMs = 30000;
    private int workerBatchSize = 20;

    public int getTimeoutMinutes() {
        return timeoutMinutes;
    }

    public void setTimeoutMinutes(int timeoutMinutes) {
        this.timeoutMinutes = timeoutMinutes;
    }

    public int getFirstReminderMinutes() {
        return firstReminderMinutes;
    }

    public void setFirstReminderMinutes(int firstReminderMinutes) {
        this.firstReminderMinutes = firstReminderMinutes;
    }

    public int getSecondReminderMinutes() {
        return secondReminderMinutes;
    }

    public void setSecondReminderMinutes(int secondReminderMinutes) {
        this.secondReminderMinutes = secondReminderMinutes;
    }

    public boolean isWorkerEnabled() {
        return workerEnabled;
    }

    public void setWorkerEnabled(boolean workerEnabled) {
        this.workerEnabled = workerEnabled;
    }

    public long getWorkerFixedDelayMs() {
        return workerFixedDelayMs;
    }

    public void setWorkerFixedDelayMs(long workerFixedDelayMs) {
        this.workerFixedDelayMs = workerFixedDelayMs;
    }

    public int getWorkerBatchSize() {
        return workerBatchSize;
    }

    public void setWorkerBatchSize(int workerBatchSize) {
        this.workerBatchSize = workerBatchSize;
    }

    public int validatedTimeoutMinutes() {
        return timeoutMinutes > 0 ? timeoutMinutes : 30;
    }

    public int validatedFirstReminderMinutes() {
        int timeout = validatedTimeoutMinutes();
        return firstReminderMinutes > 0 && firstReminderMinutes < timeout
            ? firstReminderMinutes
            : Math.min(10, Math.max(1, timeout - 1));
    }

    public int validatedSecondReminderMinutes() {
        int timeout = validatedTimeoutMinutes();
        int first = validatedFirstReminderMinutes();
        return secondReminderMinutes > first && secondReminderMinutes < timeout
            ? secondReminderMinutes
            : Math.min(Math.max(first + 1, 20), Math.max(first + 1, timeout - 1));
    }

    public int validatedWorkerBatchSize() {
        return workerBatchSize > 0 ? Math.min(workerBatchSize, 100) : 20;
    }
}
