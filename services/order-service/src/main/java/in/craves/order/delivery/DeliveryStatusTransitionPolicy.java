package in.craves.order.delivery;

import java.time.Instant;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DeliveryStatusTransitionPolicy {
    private static final Set<String> TERMINAL = Set.of(
        "DELIVERED",
        "CANCELLED",
        "RETURNED",
        "FAILED"
    );

    public Decision decide(
        String currentStatus,
        String currentTrackingUrl,
        Instant currentObservedAt,
        String incomingStatus,
        String incomingTrackingUrl,
        Instant incomingObservedAt
    ) {
        if (currentObservedAt != null && !incomingObservedAt.isAfter(currentObservedAt)) {
            return Decision.STALE;
        }
        if (currentStatus != null && TERMINAL.contains(currentStatus)
            && !currentStatus.equals(incomingStatus)) {
            return Decision.TERMINAL_PROTECTED;
        }
        if (Objects.equals(currentStatus, incomingStatus)
            && Objects.equals(normalize(currentTrackingUrl), normalize(incomingTrackingUrl))) {
            return Decision.NO_CHANGE;
        }
        return Decision.APPLY;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public enum Decision {
        APPLY,
        STALE,
        TERMINAL_PROTECTED,
        NO_CHANGE
    }
}
