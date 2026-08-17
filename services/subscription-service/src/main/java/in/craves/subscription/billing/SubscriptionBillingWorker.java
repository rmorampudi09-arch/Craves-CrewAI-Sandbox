package in.craves.subscription.billing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "craves.subscription.billing",
    name = "generator-enabled",
    havingValue = "true"
)
public class SubscriptionBillingWorker {
    private final SubscriptionBillingService service;

    public SubscriptionBillingWorker(SubscriptionBillingService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${craves.subscription.billing.generator-fixed-delay-ms:60000}")
    public void run() {
        service.generateDueInvoices();
    }
}
