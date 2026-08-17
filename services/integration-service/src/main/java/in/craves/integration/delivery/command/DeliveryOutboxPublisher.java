package in.craves.integration.delivery.command;

import in.craves.integration.delivery.command.DeliveryOutboxRepository.OutboxRecord;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
    prefix = "craves.delivery-command",
    name = "status-publisher-enabled",
    havingValue = "true"
)
public class DeliveryOutboxPublisher {
    private static final Logger log = LoggerFactory.getLogger(DeliveryOutboxPublisher.class);

    private final DeliveryOutboxRepository outbox;
    private final DeliveryServiceBusPublisher publisher;
    private final DeliveryCommandProperties properties;

    public DeliveryOutboxPublisher(DeliveryOutboxRepository outbox,
                                   DeliveryServiceBusPublisher publisher,
                                   DeliveryCommandProperties properties) {
        this.outbox = outbox;
        this.publisher = publisher;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${craves.delivery-command.outbox-publish-interval-ms:5000}")
    public void publishPending() {
        List<OutboxRecord> records = outbox.claimBatch(properties.getOutboxBatchSize());
        for (OutboxRecord record : records) {
            try {
                publisher.publishDomainEvent(
                    record.id(),
                    record.eventType(),
                    record.correlationId(),
                    record.payload()
                );
                outbox.markPublished(record.id());
            } catch (RuntimeException ex) {
                outbox.markFailed(record.id(), record.attemptCount(), safeMessage(ex));
                log.error("Delivery outbox publication failed for {}", record.id(), ex);
            }
        }
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
