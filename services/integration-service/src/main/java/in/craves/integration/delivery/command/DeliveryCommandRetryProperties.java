package in.craves.integration.delivery.command;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "craves.delivery-command.retry")
public class DeliveryCommandRetryProperties {
    private int baseSeconds = 30;
    private int maxSeconds = 600;
    private int claimContentionSeconds = 10;

    @PostConstruct
    void validate() {
        if (baseSeconds < 1 || baseSeconds > 3600) {
            throw new IllegalStateException("Delivery retry baseSeconds must be between 1 and 3600");
        }
        if (maxSeconds < baseSeconds || maxSeconds > 3600) {
            throw new IllegalStateException(
                "Delivery retry maxSeconds must be between baseSeconds and 3600"
            );
        }
        if (claimContentionSeconds < 1 || claimContentionSeconds > 300) {
            throw new IllegalStateException(
                "Delivery retry claimContentionSeconds must be between 1 and 300"
            );
        }
    }

    public Duration delay(int attemptNumber) {
        int normalizedAttempt = Math.max(1, attemptNumber);
        int exponent = Math.min(normalizedAttempt - 1, 20);
        long candidate = (long) baseSeconds * (1L << exponent);
        return Duration.ofSeconds(Math.min(candidate, maxSeconds));
    }

    public Duration claimContentionDelay() {
        return Duration.ofSeconds(claimContentionSeconds);
    }

    public int getBaseSeconds() {
        return baseSeconds;
    }

    public void setBaseSeconds(int baseSeconds) {
        this.baseSeconds = baseSeconds;
    }

    public int getMaxSeconds() {
        return maxSeconds;
    }

    public void setMaxSeconds(int maxSeconds) {
        this.maxSeconds = maxSeconds;
    }

    public int getClaimContentionSeconds() {
        return claimContentionSeconds;
    }

    public void setClaimContentionSeconds(int claimContentionSeconds) {
        this.claimContentionSeconds = claimContentionSeconds;
    }
}
