package in.craves.subscription.capacity;

import in.craves.subscription.repository.SubscriptionRepository;
import in.craves.subscription.web.ApiDtos.SubscriptionResponse;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CapacityFailureReporter {
    private final CapacityRepository capacityRepository;
    private final SubscriptionRepository subscriptionRepository;

    public CapacityFailureReporter(
        CapacityRepository capacityRepository,
        SubscriptionRepository subscriptionRepository
    ) {
        this.capacityRepository = capacityRepository;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void reportPaidCapacityConflict(UUID subscriptionId, String errorCode, String detail) {
        SubscriptionResponse subscription = subscriptionRepository.findSubscriptionById(subscriptionId).orElse(null);
        if (subscription == null || subscription.chefIdentityId() == null) {
            return;
        }
        String reason = "A paid subscription could not reacquire capacity after its hold expired. " +
            "The subscription remains non-active and the payment-status message will retry/DLQ for support handling. " +
            "Error=" + safe(errorCode) + "; detail=" + safe(detail);
        capacityRepository.openOrUpdateIncident(
            subscription.chefIdentityId(),
            null,
            null,
            "PAYMENT",
            null,
            "PAID_CAPACITY_CONFLICT",
            "P2",
            1,
            0,
            reason
        );
        capacityRepository.audit(
            subscription.chefIdentityId(),
            subscription.customerIdentityId(),
            "PAID_CAPACITY_CONFLICT",
            "SUBSCRIPTION",
            subscription.id().toString(),
            reason,
            null,
            null
        );
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() > 300 ? normalized.substring(0, 300) : normalized;
    }
}
