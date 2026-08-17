package in.craves.integration.delivery.command;

import in.craves.integration.delivery.command.DeliveryCommandModels.RoutingResult;
import in.craves.integration.delivery.command.DeliveryCommandRepository.CommandRecord;
import in.craves.integration.delivery.command.DeliveryProviderRouter.DeliveryCreateReconciliationPendingException;
import in.craves.integration.delivery.status.DeliveryLeaseRecoveryRepository;
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
    name = "reconciliation-enabled",
    havingValue = "true"
)
public class DeliveryCreateReconciliationWorker {
    private static final Logger log = LoggerFactory.getLogger(
        DeliveryCreateReconciliationWorker.class
    );
    private static final long MAX_RETRY_DELAY_SECONDS = 3600;

    private final DeliveryCommandRepository commands;
    private final DeliveryJobRepository deliveryJobs;
    private final DeliveryProviderRouter router;
    private final DeliveryCommandCompletionService completionService;
    private final DeliveryLeaseRecoveryRepository leaseRecovery;
    private final DeliveryCommandProperties properties;

    public DeliveryCreateReconciliationWorker(
        DeliveryCommandRepository commands,
        DeliveryJobRepository deliveryJobs,
        DeliveryProviderRouter router,
        DeliveryCommandCompletionService completionService,
        DeliveryLeaseRecoveryRepository leaseRecovery,
        DeliveryCommandProperties properties
    ) {
        this.commands = commands;
        this.deliveryJobs = deliveryJobs;
        this.router = router;
        this.completionService = completionService;
        this.leaseRecovery = leaseRecovery;
        this.properties = properties;
    }

    @Scheduled(
        fixedDelayString = "${craves.delivery-command.reconciliation-interval-ms:15000}"
    )
    public void reconcilePending() {
        int recovered = leaseRecovery.deadLetterExhaustedCreateReconciliationLeases(
            properties.getMaxReconciliationAttempts(),
            properties.getReconciliationStaleMinutes()
        );
        if (recovered > 0) {
            log.error(
                "Dead-lettered {} create-reconciliation rows whose final processing lease expired",
                recovered
            );
        }

        List<CommandRecord> records = commands.claimReconciliationBatch(
            properties.getReconciliationBatchSize(),
            properties.getMaxReconciliationAttempts(),
            properties.getReconciliationStaleMinutes()
        );
        for (CommandRecord command : records) {
            reconcileOne(command);
        }
    }

    private void reconcileOne(CommandRecord command) {
        try {
            if (deliveryJobs.findIdByChefSubOrderId(
                command.chefSubOrderId()
            ).isPresent()) {
                commands.markCompleted(command.id());
                log.info(
                    "Delivery create reconciliation found an existing local job commandId={} chefSubOrderId={}",
                    command.id(),
                    command.chefSubOrderId()
                );
                return;
            }

            RoutingResult result = router.reconcile(command);
            var receipt = completionService.complete(command.message(), result);
            log.info(
                "Delivery create reconciliation completed commandId={} chefSubOrderId={} providerId={} deliveryJobId={} duplicate={}",
                command.id(),
                command.chefSubOrderId(),
                result.providerId(),
                receipt.deliveryJobId(),
                receipt.duplicate()
            );
        } catch (DeliveryCreateReconciliationPendingException ex) {
            retry(command, safeMessage(ex));
        } catch (RuntimeException ex) {
            retry(
                command,
                "Delivery create reconciliation failed: " + safeMessage(ex)
            );
            log.error(
                "Delivery create reconciliation failed commandId={} chefSubOrderId={}",
                command.id(),
                command.chefSubOrderId(),
                ex
            );
        }
    }

    private void retry(CommandRecord command,
                       String error) {
        int attempt = command.reconciliationAttemptCount();
        Instant nextAttemptAt = Instant.now().plusSeconds(
            retryDelaySeconds(attempt)
        );
        commands.scheduleReconciliationRetry(
            command.id(),
            attempt,
            properties.getMaxReconciliationAttempts(),
            nextAttemptAt,
            error
        );
        log.warn(
            "Delivery create reconciliation remains pending commandId={} chefSubOrderId={} providerId={} attempt={} nextAttemptAt={} error={}",
            command.id(),
            command.chefSubOrderId(),
            command.reconciliationProviderId(),
            attempt,
            nextAttemptAt,
            error
        );
    }

    private long retryDelaySeconds(int attempt) {
        long base = properties.getReconciliationRetryBaseSeconds();
        int exponent = Math.max(0, Math.min(attempt - 1, 20));
        long multiplier = 1L << exponent;
        if (base > MAX_RETRY_DELAY_SECONDS / multiplier) {
            return MAX_RETRY_DELAY_SECONDS;
        }
        return Math.min(MAX_RETRY_DELAY_SECONDS, base * multiplier);
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
