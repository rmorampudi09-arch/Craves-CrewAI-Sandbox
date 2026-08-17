package in.craves.integration.delivery;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DeliveryMetricsMaintenanceJob {
    private static final Logger log = LoggerFactory.getLogger(DeliveryMetricsMaintenanceJob.class);
    private final DeliveryMetricsMaintenanceService maintenanceService;

    public DeliveryMetricsMaintenanceJob(DeliveryMetricsMaintenanceService maintenanceService) {
        this.maintenanceService = maintenanceService;
    }

    @Scheduled(
        cron = "${craves.delivery-intelligence.maintenance-cron:0 0 2 * * *}",
        zone = "${craves.delivery-intelligence.maintenance-zone:Asia/Kolkata}"
    )
    public void run() {
        DeliveryMetricsMaintenanceService.MaintenanceReport report = maintenanceService.archiveAgedScores();
        if (!report.lockAcquired()) {
            log.info("Delivery intelligence maintenance skipped because another replica owns the advisory lock");
            return;
        }
        log.info("Delivery intelligence maintenance completed: archivedScores={}, providersProcessed={}, providerDaysFolded={}",
            report.archivedScores(), report.providersProcessed(), report.providerDaysFolded());
    }
}
