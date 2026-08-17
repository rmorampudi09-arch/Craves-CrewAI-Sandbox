package in.craves.subscription.occurrence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "craves.subscription.occurrence-generator",
    name = "enabled",
    havingValue = "true"
)
public class OccurrenceGeneratorWorker {
    private final OccurrenceGeneratorService service;

    public OccurrenceGeneratorWorker(OccurrenceGeneratorService service) {
        this.service = service;
    }

    @Scheduled(fixedDelayString = "${craves.subscription.occurrence-generator.fixed-delay-ms:60000}")
    public void run() {
        service.generateDue();
    }
}
