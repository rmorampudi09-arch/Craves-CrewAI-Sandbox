package in.craves.order.refund;

import java.time.Instant;
import org.springframework.stereotype.Component;

@Component
public class RefundStatusTransitionPolicy {
    public Decision decide(
        String currentStatus,
        Instant currentUpdatedAt,
        String incomingStatus,
        Instant incomingUpdatedAt
    ) {
        if (currentUpdatedAt != null && !incomingUpdatedAt.isAfter(currentUpdatedAt)) {
            return Decision.STALE;
        }
        if ("REFUNDED".equals(currentStatus) && !"REFUNDED".equals(incomingStatus)) {
            return Decision.STALE;
        }
        return Decision.APPLY;
    }

    public enum Decision {
        APPLY,
        STALE
    }
}
