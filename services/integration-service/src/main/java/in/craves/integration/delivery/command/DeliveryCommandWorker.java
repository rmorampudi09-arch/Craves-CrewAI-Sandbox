package in.craves.integration.delivery.command;

import in.craves.integration.delivery.command.DeliveryCommandCompletionService.CompletionReceipt;
import in.craves.integration.delivery.command.DeliveryCommandModels.DeliveryCommandMessage;
import in.craves.integration.delivery.command.DeliveryCommandModels.RoutingResult;
import in.craves.integration.delivery.command.DeliveryCommandRepository.CommandRecord;
import in.craves.integration.delivery.command.DeliveryProviderRouter.DeliveryCreateReconciliationPendingException;
import in.craves.integration.delivery.command.DeliveryProviderRouter.DeliveryProviderTemporarilyUnavailableException;
import in.craves.integration.delivery.command.DeliveryProviderRouter.DeliveryRoutingException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DeliveryCommandWorker {
    private final DeliveryCommandRepository commands;
    private final DeliveryJobRepository deliveryJobs;
    private final DeliveryProviderRouter router;
    private final DeliveryCommandCompletionService completionService;
    private final DeliveryCommandProperties properties;
    private final DeliveryCommandRetryProperties retryProperties;

    public DeliveryCommandWorker(DeliveryCommandRepository commands,
                                 DeliveryJobRepository deliveryJobs,
                                 DeliveryProviderRouter router,
                                 DeliveryCommandCompletionService completionService,
                                 DeliveryCommandProperties properties,
                                 DeliveryCommandRetryProperties retryProperties) {
        this.commands = commands;
        this.deliveryJobs = deliveryJobs;
        this.router = router;
        this.completionService = completionService;
        this.properties = properties;
        this.retryProperties = retryProperties;
    }

    public WorkerReceipt process(DeliveryCommandMessage message) {
        if (message == null || message.commandId() == null || message.chefSubOrderId() == null) {
            throw new DeliveryCommandNonRetryableException("Delivery command identity is missing");
        }

        Optional<CommandRecord> claimed = commands.claim(
            message.commandId(), properties.getMaxDeliveryAttempts()
        );
        if (claimed.isEmpty()) {
            return handleUnclaimed(message.commandId(), message.chefSubOrderId());
        }

        CommandRecord command = claimed.get();
        Optional<UUID> existingJob = deliveryJobs.findIdByChefSubOrderId(message.chefSubOrderId());
        if (existingJob.isPresent()) {
            commands.markCompleted(message.commandId());
            return new WorkerReceipt(existingJob.get(), true, "ALREADY_COMPLETED");
        }

        try {
            RoutingResult routingResult = router.route(command.message());
            CompletionReceipt receipt = completionService.complete(command.message(), routingResult);
            return new WorkerReceipt(
                receipt.deliveryJobId(), receipt.duplicate(), routingResult.providerId()
            );
        } catch (DeliveryCreateReconciliationPendingException ex) {
            boolean stored = commands.markReconciliationPending(
                command.id(),
                ex.providerId(),
                ex.clientReference(),
                ex.attemptedAt(),
                safeMessage(ex)
            );
            if (!stored) {
                throw transientRetry(
                    command,
                    "Uncertain provider create could not be moved to reconciliation",
                    ex
                );
            }
            return new WorkerReceipt(null, false, "RECONCILIATION_PENDING");
        } catch (DeliveryProviderTemporarilyUnavailableException ex) {
            int providerWaitAttempt = command.providerWaitAttemptCount() + 1;
            Instant retryAt = Instant.now().plus(retryProperties.delay(providerWaitAttempt));
            boolean stored = commands.markProviderWait(command.id(), retryAt, safeMessage(ex));
            if (!stored) {
                throw new DeliveryCommandTransientException(
                    "Provider wait state could not be persisted",
                    Instant.now().plus(retryProperties.claimContentionDelay()),
                    "provider-wait-store-" + command.id() + "-" + providerWaitAttempt,
                    ex
                );
            }
            throw new DeliveryCommandDeferredException(
                safeMessage(ex),
                retryAt,
                "provider-wait-" + command.id() + "-" + providerWaitAttempt,
                ex
            );
        } catch (DeliveryRoutingException ex) {
            handleFailure(command, ex);
            throw transientRetry(command, ex.getMessage(), ex);
        } catch (RuntimeException ex) {
            handleFailure(command, ex);
            throw transientRetry(command, "Delivery command processing failed", ex);
        }
    }

    private WorkerReceipt handleUnclaimed(UUID commandId, UUID chefSubOrderId) {
        Optional<CommandRecord> current = commands.findById(commandId);
        if (current.isEmpty()) {
            throw new DeliveryCommandNonRetryableException("Delivery command does not exist in the database");
        }
        CommandRecord command = current.get();
        if ("COMPLETED".equals(command.status())) {
            UUID deliveryJobId = deliveryJobs.findIdByChefSubOrderId(chefSubOrderId)
                .orElseThrow(() -> new DeliveryCommandNonRetryableException(
                    "Completed delivery command has no delivery job"
                ));
            return new WorkerReceipt(deliveryJobId, true, "ALREADY_COMPLETED");
        }
        if ("RECONCILIATION_PENDING".equals(command.status())) {
            return new WorkerReceipt(null, true, "RECONCILIATION_PENDING");
        }
        if ("WAITING_FOR_PROVIDER".equals(command.status())) {
            Instant retryAt = command.nextProviderRetryAt();
            if (retryAt == null) {
                throw new DeliveryCommandNonRetryableException(
                    "Provider wait command has no next retry time"
                );
            }
            throw new DeliveryCommandDeferredException(
                "Delivery provider is temporarily unavailable",
                retryAt,
                "provider-wait-" + command.id() + "-" + command.providerWaitAttemptCount()
            );
        }
        if ("DEAD_LETTER".equals(command.status())
            || command.attemptCount() >= properties.getMaxDeliveryAttempts()) {
            commands.markDeadLetter(commandId, "Delivery command attempt limit exhausted");
            throw new DeliveryCommandNonRetryableException(
                "Delivery command attempt limit exhausted"
            );
        }
        throw new DeliveryCommandTransientException(
            "Delivery command is currently being processed or is not claimable",
            Instant.now().plus(retryProperties.claimContentionDelay()),
            "claim-contention-" + commandId + "-" + UUID.randomUUID()
        );
    }

    private DeliveryCommandTransientException transientRetry(CommandRecord command,
                                                              String message,
                                                              Throwable cause) {
        Instant retryAt = Instant.now().plus(
            retryProperties.delay(Math.max(1, command.attemptCount()))
        );
        return new DeliveryCommandTransientException(
            message,
            retryAt,
            "delivery-attempt-" + command.id() + "-" + command.attemptCount(),
            cause
        );
    }

    private void handleFailure(CommandRecord command, RuntimeException error) {
        String message = safeMessage(error);
        if (command.attemptCount() >= properties.getMaxDeliveryAttempts()) {
            commands.markDeadLetter(command.id(), message);
            throw new DeliveryCommandNonRetryableException(
                "Delivery command exhausted its retry budget: " + message,
                error
            );
        }
        commands.markFailed(command.id(), message);
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    public record WorkerReceipt(UUID deliveryJobId, boolean duplicate, String providerId) {}

    public static class DeliveryCommandTransientException extends RuntimeException {
        private final Instant retryAt;
        private final String retryKey;

        public DeliveryCommandTransientException(String message,
                                                 Instant retryAt,
                                                 String retryKey) {
            super(message);
            this.retryAt = retryAt;
            this.retryKey = retryKey;
        }

        public DeliveryCommandTransientException(String message,
                                                 Instant retryAt,
                                                 String retryKey,
                                                 Throwable cause) {
            super(message, cause);
            this.retryAt = retryAt;
            this.retryKey = retryKey;
        }

        public Instant retryAt() {
            return retryAt;
        }

        public String retryKey() {
            return retryKey;
        }
    }

    public static final class DeliveryCommandDeferredException
        extends DeliveryCommandTransientException {

        public DeliveryCommandDeferredException(String message,
                                                Instant retryAt,
                                                String retryKey) {
            super(message, retryAt, retryKey);
        }

        public DeliveryCommandDeferredException(String message,
                                                Instant retryAt,
                                                String retryKey,
                                                Throwable cause) {
            super(message, retryAt, retryKey, cause);
        }
    }

    public static class DeliveryCommandNonRetryableException extends RuntimeException {
        public DeliveryCommandNonRetryableException(String message) {
            super(message);
        }

        public DeliveryCommandNonRetryableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
