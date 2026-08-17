package in.craves.subscription.capacity;

import in.craves.subscription.capacity.CapacityProjectionService.ProjectionSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "craves.subscription.capacity",
    name = "projection-scheduler-enabled",
    havingValue = "true"
)
public class CapacityProjectionWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(CapacityProjectionWorker.class);

    private final CapacityProjectionService service;

    public CapacityProjectionWorker(CapacityProjectionService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${craves.subscription.capacity.projection-fixed-delay-ms:60000}")
    public void extendProjection() {
        ProjectionSummary summary = service.projectLaggingBatch();
        if (summary.claimedSubscriptions() > 0) {
            LOGGER.info(
                "Subscription capacity projection claimed={} projectedSubscriptions={} projectedDates={} incidentsRaised={}",
                summary.claimedSubscriptions(),
                summary.projectedSubscriptions(),
                summary.projectedDates(),
                summary.incidentsRaised()
            );
        }
    }
}
