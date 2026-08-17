package in.craves.integration.delivery.status;

import in.craves.integration.delivery.command.DeliveryCommandProperties;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.TrackingSnapshot;
import in.craves.integration.delivery.status.DeliveryStatusRepository.TrackingWorkItem;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "craves.delivery-command",
    name = "tracking-reconciliation-enabled",
    havingValue = "true"
)
public class DeliveryTrackingReconciliationWorker {
    private static final Logger log = LoggerFactory.getLogger(
        DeliveryTrackingReconciliationWorker.class
    );
    private static final long MAX_RETRY_SECONDS = 900;

    private final Map<String, DeliveryProviderAdapter> adapters;
    private final DeliveryStatusRepository repository;
    private final DeliveryLeaseRecoveryRepository leaseRecovery;
    private final DeliveryStatusUpdateService statusService;
    private final DeliveryCommandProperties properties;

    public DeliveryTrackingReconciliationWorker(
        List<DeliveryProviderAdapter> adapters,
        DeliveryStatusRepository repository,
        DeliveryLeaseRecoveryRepository leaseRecovery,
        DeliveryStatusUpdateService statusService,
        DeliveryCommandProperties properties
    ) {
        this.adapters = indexAdapters(adapters);
        this.repository = repository;
        this.leaseRecovery = leaseRecovery;
        this.statusService = statusService;
        this.properties = properties;
    }

    @Scheduled(
        fixedDelayString = "${craves.delivery-command.tracking-reconciliation-interval-ms:15000}"
    )
    public void reconcile() {
        int recovered = leaseRecovery.deadLetterExhaustedTrackingLeases(
            properties.getMaxTrackingAttempts(),
            properties.getTrackingStaleMinutes()
        );
        if (recovered > 0) {
            log.error(
                "Dead-lettered {} tracking jobs whose final processing lease expired",
                recovered
            );
        }

        List<TrackingWorkItem> workItems = repository.claimTrackingBatch(
            properties.getTrackingBatchSize(),
            properties.getTrackingStaleMinutes(),
            properties.getMaxTrackingAttempts()
        );
        for (TrackingWorkItem workItem : workItems) {
            reconcileOne(workItem);
        }
    }

    private void reconcileOne(TrackingWorkItem workItem) {
        try {
            DeliveryProviderAdapter adapter = adapters.get(
                normalize(workItem.providerId())
            );
            if (adapter == null) {
                throw new IllegalStateException(
                    "No delivery adapter is registered for provider "
                        + workItem.providerId()
                );
            }
            TrackingSnapshot snapshot = adapter.track(
                workItem.providerDeliveryId()
            );
            DeliveryStatusUpdateService.ProcessingResult result =
                statusService.processTracking(workItem, snapshot);
            log.info(
                "Delivery tracking reconciled deliveryJobId={} provider={} applied={} duplicate={} result={}",
                workItem.deliveryJobId(),
                workItem.providerId(),
                result.applied(),
                result.duplicate(),
                result.result()
            );
        } catch (RuntimeException ex) {
            long delaySeconds = retryDelaySeconds(workItem.attemptCount());
            repository.markTrackingFailed(
                workItem.deliveryJobId(),
                workItem.attemptCount(),
                properties.getMaxTrackingAttempts(),
                Instant.now().plusSeconds(delaySeconds),
                safeMessage(ex)
            );
            log.error(
                "Delivery tracking reconciliation failed deliveryJobId={} provider={} attempt={}",
                workItem.deliveryJobId(),
                workItem.providerId(),
                workItem.attemptCount(),
                ex
            );
        }
    }

    private long retryDelaySeconds(int attemptCount) {
        int exponent = Math.max(0, Math.min(attemptCount - 1, 20));
        long multiplier = 1L << exponent;
        long base = properties.getTrackingRetryBaseSeconds();
        if (base > MAX_RETRY_SECONDS / multiplier) {
            return MAX_RETRY_SECONDS;
        }
        return Math.min(MAX_RETRY_SECONDS, base * multiplier);
    }

    private static Map<String, DeliveryProviderAdapter> indexAdapters(
        List<DeliveryProviderAdapter> adapters
    ) {
        Map<String, DeliveryProviderAdapter> indexed = new HashMap<>();
        for (DeliveryProviderAdapter adapter : adapters) {
            DeliveryProviderAdapter previous = indexed.put(
                normalize(adapter.providerId()),
                adapter
            );
            if (previous != null) {
                throw new IllegalStateException(
                    "Duplicate delivery adapter " + adapter.providerId()
                );
            }
        }
        return Map.copyOf(indexed);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
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
