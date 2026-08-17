package in.craves.order.service;

import in.craves.order.config.ChefAcceptanceWindowProperties;
import in.craves.order.service.CatalogClient.CatalogKitchen;
import in.craves.order.service.ChefAcceptanceWorkRepository.ReminderCandidate;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ChefAcceptanceWindowWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(ChefAcceptanceWindowWorker.class);

    private final ChefAcceptanceWindowProperties properties;
    private final ChefAcceptanceWorkRepository workRepository;
    private final ChefAcceptanceInitialNotificationService initialNotificationService;
    private final ChefAcceptanceResolutionService resolutionService;
    private final CatalogClient catalogClient;

    public ChefAcceptanceWindowWorker(
        ChefAcceptanceWindowProperties properties,
        ChefAcceptanceWorkRepository workRepository,
        ChefAcceptanceInitialNotificationService initialNotificationService,
        ChefAcceptanceResolutionService resolutionService,
        CatalogClient catalogClient
    ) {
        this.properties = properties;
        this.workRepository = workRepository;
        this.initialNotificationService = initialNotificationService;
        this.resolutionService = resolutionService;
        this.catalogClient = catalogClient;
    }

    @Scheduled(fixedDelayString = "${craves.chef-acceptance.worker-fixed-delay-ms:30000}")
    public void process() {
        if (!properties.isWorkerEnabled()) {
            return;
        }

        int batchSize = properties.validatedWorkerBatchSize();
        expireOrders(batchSize);
        sendInitialNotifications(batchSize);
        sendFirstReminders(batchSize);
        sendSecondReminders(batchSize);
    }

    private void expireOrders(int batchSize) {
        for (UUID orderId : workRepository.findExpiredOrderIds(batchSize)) {
            try {
                if (resolutionService.timeoutExpiredOrder(orderId)) {
                    LOGGER.info("Chef acceptance expired and refund was requested for orderId={}", orderId);
                }
            } catch (RuntimeException exception) {
                LOGGER.error("Chef acceptance timeout processing failed for orderId={}", orderId, exception);
            }
        }
    }

    private void sendInitialNotifications(int batchSize) {
        for (ReminderCandidate candidate : workRepository.findInitialNotificationCandidates(batchSize)) {
            try {
                CatalogKitchen kitchen = catalogClient.getKitchen(candidate.kitchenId());
                if (initialNotificationService.record(candidate.orderId(), kitchen.identityId())) {
                    LOGGER.info(
                        "Initial chef acceptance notification recorded for orderId={} chefIdentityId={}",
                        candidate.orderId(),
                        kitchen.identityId()
                    );
                }
            } catch (RuntimeException exception) {
                LOGGER.warn(
                    "Initial chef acceptance notification could not be recorded for orderId={} kitchenId={}: {}",
                    candidate.orderId(),
                    candidate.kitchenId(),
                    safeMessage(exception)
                );
            }
        }
    }

    private void sendFirstReminders(int batchSize) {
        int reminderMinutes = properties.validatedFirstReminderMinutes();
        for (ReminderCandidate candidate : workRepository.findFirstReminderCandidates(reminderMinutes, batchSize)) {
            recordReminder(candidate, reminderMinutes, true);
        }
    }

    private void sendSecondReminders(int batchSize) {
        int reminderMinutes = properties.validatedSecondReminderMinutes();
        for (ReminderCandidate candidate : workRepository.findSecondReminderCandidates(reminderMinutes, batchSize)) {
            recordReminder(candidate, reminderMinutes, false);
        }
    }

    private void recordReminder(ReminderCandidate candidate, int reminderMinutes, boolean first) {
        try {
            CatalogKitchen kitchen = catalogClient.getKitchen(candidate.kitchenId());
            boolean recorded = first
                ? resolutionService.recordFirstReminder(candidate.orderId(), kitchen.identityId(), reminderMinutes)
                : resolutionService.recordSecondReminder(candidate.orderId(), kitchen.identityId(), reminderMinutes);
            if (recorded) {
                LOGGER.info(
                    "Chef acceptance {} reminder recorded for orderId={} chefIdentityId={}",
                    first ? "first" : "second",
                    candidate.orderId(),
                    kitchen.identityId()
                );
            }
        } catch (RuntimeException exception) {
            LOGGER.warn(
                "Chef acceptance reminder could not be recorded for orderId={} kitchenId={}: {}",
                candidate.orderId(),
                candidate.kitchenId(),
                safeMessage(exception)
            );
        }
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
            ? exception.getClass().getSimpleName()
            : message;
    }
}
