package in.craves.integration.delivery.status;

import in.craves.integration.delivery.command.DeliveryCommandProperties;
import in.craves.integration.delivery.status.DeliveryStatusRepository.WebhookWorkItem;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "craves.delivery-command",
    name = "webhook-processing-enabled",
    havingValue = "true"
)
public class DeliveryWebhookProcessor {
    private static final Logger log = LoggerFactory.getLogger(DeliveryWebhookProcessor.class);
    private static final long MAX_RETRY_SECONDS = 300;

    private final DeliveryStatusRepository repository;
    private final DeliveryLeaseRecoveryRepository leaseRecovery;
    private final DeliveryStatusUpdateService statusService;
    private final DeliveryCommandProperties properties;

    public DeliveryWebhookProcessor(DeliveryStatusRepository repository,
                                    DeliveryLeaseRecoveryRepository leaseRecovery,
                                    DeliveryStatusUpdateService statusService,
                                    DeliveryCommandProperties properties) {
        this.repository = repository;
        this.leaseRecovery = leaseRecovery;
        this.statusService = statusService;
        this.properties = properties;
    }

    @Scheduled(
        fixedDelayString = "${craves.delivery-command.webhook-processing-interval-ms:2000}"
    )
    public void process() {
        int recovered = leaseRecovery.deadLetterExhaustedWebhookLeases(
            properties.getMaxWebhookAttempts(),
            properties.getWebhookStaleMinutes()
        );
        if (recovered > 0) {
            log.error(
                "Dead-lettered {} webhook rows whose final processing lease expired",
                recovered
            );
        }

        List<WebhookWorkItem> workItems = repository.claimWebhookBatch(
            properties.getWebhookBatchSize(),
            properties.getWebhookStaleMinutes(),
            properties.getMaxWebhookAttempts()
        );
        for (WebhookWorkItem workItem : workItems) {
            processOne(workItem);
        }
    }

    private void processOne(WebhookWorkItem workItem) {
        try {
            DeliveryStatusUpdateService.ProcessingResult result =
                statusService.processWebhook(workItem);
            log.info(
                "Delivery webhook processed inboxId={} provider={} providerEventId={} deliveryJobId={} applied={} duplicate={} result={}",
                workItem.id(),
                workItem.providerId(),
                workItem.providerEventId(),
                result.deliveryJobId(),
                result.applied(),
                result.duplicate(),
                result.result()
            );
        } catch (RuntimeException ex) {
            long delaySeconds = retryDelaySeconds(workItem.attemptCount());
            repository.markWebhookFailed(
                workItem.id(),
                workItem.attemptCount(),
                properties.getMaxWebhookAttempts(),
                Instant.now().plusSeconds(delaySeconds),
                safeMessage(ex)
            );
            log.error(
                "Delivery webhook processing failed inboxId={} provider={} providerEventId={} attempt={}",
                workItem.id(),
                workItem.providerId(),
                workItem.providerEventId(),
                workItem.attemptCount(),
                ex
            );
        }
    }

    private long retryDelaySeconds(int attemptCount) {
        int exponent = Math.max(0, Math.min(attemptCount - 1, 20));
        long multiplier = 1L << exponent;
        long base = properties.getWebhookRetryBaseSeconds();
        if (base > MAX_RETRY_SECONDS / multiplier) {
            return MAX_RETRY_SECONDS;
        }
        return Math.min(MAX_RETRY_SECONDS, base * multiplier);
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        String normalized = message.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= 1000
            ? normalized
            : normalized.substring(0, 1000);
    }
}
