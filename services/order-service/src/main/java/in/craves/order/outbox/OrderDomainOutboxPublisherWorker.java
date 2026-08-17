package in.craves.order.outbox;

import in.craves.order.config.DomainEventOutboxProperties;
import in.craves.order.config.ServiceBusDomainEventProperties;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OrderDomainOutboxPublisherWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(OrderDomainOutboxPublisherWorker.class);
    private static final long MAX_RETRY_DELAY_SECONDS = 3600;

    private final OrderDomainOutboxRepository repository;
    private final ObjectProvider<DomainEventTransport> transportProvider;
    private final DomainEventOutboxProperties outboxProperties;
    private final ServiceBusDomainEventProperties serviceBusProperties;

    public OrderDomainOutboxPublisherWorker(
        OrderDomainOutboxRepository repository,
        ObjectProvider<DomainEventTransport> transportProvider,
        DomainEventOutboxProperties outboxProperties,
        ServiceBusDomainEventProperties serviceBusProperties
    ) {
        this.repository = repository;
        this.transportProvider = transportProvider;
        this.outboxProperties = outboxProperties;
        this.serviceBusProperties = serviceBusProperties;
    }

    @Scheduled(fixedDelayString = "${craves.domain-events.outbox.fixed-delay-ms:5000}")
    public void dispatch() {
        if (!outboxProperties.isEnabled() || !serviceBusProperties.isEnabled()) {
            return;
        }

        Set<String> enabledEventTypes = outboxProperties.normalizedEnabledEventTypes();
        if (enabledEventTypes.isEmpty()) {
            LOGGER.warn("Domain event outbox is enabled but no event types are allowed for publication");
            return;
        }

        DomainEventTransport transport = transportProvider.getIfAvailable();
        if (transport == null) {
            LOGGER.error("Domain event outbox is enabled but no transport is available");
            return;
        }

        UUID lockToken = UUID.randomUUID();
        List<OrderDomainOutboxRecord> records = repository.claimBatch(
            positive(outboxProperties.getBatchSize(), 20),
            positive(outboxProperties.getMaxAttempts(), 10),
            positive(outboxProperties.getStaleLockSeconds(), 300),
            lockToken,
            List.copyOf(enabledEventTypes)
        );

        for (OrderDomainOutboxRecord record : records) {
            publishOne(transport, record, lockToken);
        }
    }

    private void publishOne(
        DomainEventTransport transport,
        OrderDomainOutboxRecord record,
        UUID lockToken
    ) {
        try {
            String brokerMessageId = transport.publish(record);
            boolean marked = repository.markPublished(record.id(), lockToken, brokerMessageId);
            if (!marked) {
                LOGGER.warn(
                    "Domain event was sent but its outbox claim was no longer current: eventId={} eventType={}",
                    record.id(),
                    record.eventType()
                );
            }
        } catch (RuntimeException exception) {
            int maxAttempts = positive(outboxProperties.getMaxAttempts(), 10);
            Instant nextAttemptAt = Instant.now().plusSeconds(retryDelaySeconds(record.attempts()));
            repository.markFailed(
                record.id(),
                lockToken,
                record.attempts(),
                maxAttempts,
                nextAttemptAt,
                errorSummary(exception)
            );
            LOGGER.error(
                "Domain event publication failed: eventId={} eventType={} attempt={}/{}",
                record.id(),
                record.eventType(),
                record.attempts(),
                maxAttempts,
                exception
            );
        }
    }

    private long retryDelaySeconds(int attempt) {
        long base = positive(outboxProperties.getRetryBaseDelaySeconds(), 5);
        int exponent = Math.max(0, Math.min(attempt - 1, 20));
        long multiplier = 1L << exponent;
        if (base > MAX_RETRY_DELAY_SECONDS / multiplier) {
            return MAX_RETRY_DELAY_SECONDS;
        }
        return Math.min(MAX_RETRY_DELAY_SECONDS, base * multiplier);
    }

    private static int positive(int configured, int fallback) {
        return configured > 0 ? configured : fallback;
    }

    private static String errorSummary(RuntimeException exception) {
        String message = exception.getMessage();
        return exception.getClass().getSimpleName()
            + (message == null || message.isBlank() ? "" : ": " + message);
    }
}
