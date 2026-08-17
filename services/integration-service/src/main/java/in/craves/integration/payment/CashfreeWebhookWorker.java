package in.craves.integration.payment;

import in.craves.integration.payment.CashfreeWebhookInboxService.WorkItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "craves.cashfree.webhook", name = "worker-enabled", havingValue = "true")
public class CashfreeWebhookWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(CashfreeWebhookWorker.class);

    private final CashfreeWebhookInboxService inbox;
    private final CashfreeWebhookDispatcher dispatcher;

    public CashfreeWebhookWorker(
        CashfreeWebhookInboxService inbox,
        CashfreeWebhookDispatcher dispatcher
    ) {
        this.inbox = inbox;
        this.dispatcher = dispatcher;
    }

    @Scheduled(fixedDelayString = "${craves.cashfree.webhook.fixed-delay-ms:2000}")
    public void process() {
        for (WorkItem item : inbox.claimBatch()) {
            try {
                dispatcher.dispatch(item.timestamp(), item.signature(), item.rawPayload());
                inbox.complete(item);
                LOGGER.info(
                    "Cashfree webhook processed deliveryId={} attempt={}",
                    item.id(),
                    item.attemptCount()
                );
            } catch (Exception exception) {
                inbox.fail(item, exception);
                LOGGER.warn(
                    "Cashfree webhook processing failed deliveryId={} attempt={}",
                    item.id(),
                    item.attemptCount()
                );
            }
        }
    }
}
