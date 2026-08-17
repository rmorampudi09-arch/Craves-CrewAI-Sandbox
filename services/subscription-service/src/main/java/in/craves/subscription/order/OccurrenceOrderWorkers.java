package in.craves.subscription.order;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import in.craves.subscription.order.OccurrenceOrderRepository.OutboxRecord;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

public final class OccurrenceOrderWorkers {
    private OccurrenceOrderWorkers() {
    }

    @Component
    @ConditionalOnProperty(
        prefix = "craves.subscription.order-dispatch",
        name = "request-worker-enabled",
        havingValue = "true"
    )
    public static class RequestWorker {
        private final OccurrenceOrderService service;

        public RequestWorker(OccurrenceOrderService service) {
            this.service = service;
        }

        @Scheduled(fixedDelayString = "${craves.subscription.order-dispatch.request-fixed-delay-ms:30000}")
        public void process() {
            service.queueDueOccurrences();
        }
    }

    @Component
    @ConditionalOnProperty(
        prefix = "craves.subscription.order-dispatch",
        name = "publisher-enabled",
        havingValue = "true"
    )
    public static class Publisher {
        private static final Logger LOGGER = LoggerFactory.getLogger(Publisher.class);

        private final OccurrenceOrderProperties properties;
        private final OccurrenceOrderRepository repository;
        private final ServiceBusSenderClient sender;

        public Publisher(
            OccurrenceOrderProperties properties,
            OccurrenceOrderRepository repository
        ) {
            this.properties = properties;
            this.repository = repository;
            ServiceBusClientBuilder builder = new ServiceBusClientBuilder();
            if (StringUtils.hasText(properties.getConnectionString())) {
                builder.connectionString(properties.getConnectionString());
            } else {
                builder.credential(
                    properties.getFullyQualifiedNamespace(),
                    new DefaultAzureCredentialBuilder().build()
                );
            }
            this.sender = builder.sender().topicName(properties.getTopicName()).buildClient();
        }

        @Scheduled(fixedDelayString = "${craves.subscription.order-dispatch.publisher-fixed-delay-ms:5000}")
        public void publish() {
            for (OutboxRecord record : repository.claimOutbox(
                properties.getBatchSize(),
                properties.getMaxPublishAttempts(),
                properties.getStaleLockMinutes()
            )) {
                try {
                    ServiceBusMessage message = new ServiceBusMessage(record.payload())
                        .setMessageId(record.id().toString())
                        .setSubject(record.eventType())
                        .setCorrelationId(record.correlationId().toString())
                        .setContentType("application/json");
                    message.getApplicationProperties().put("event_type", record.eventType());
                    message.getApplicationProperties().put("eventType", record.eventType());
                    sender.sendMessage(message);
                    repository.markPublished(record);
                } catch (RuntimeException exception) {
                    repository.markPublishFailure(record, properties.getMaxPublishAttempts(), exception);
                    LOGGER.error(
                        "Subscription order request publish failed outboxId={} attempt={}",
                        record.id(), record.attemptCount(), exception
                    );
                }
            }
        }

        @PreDestroy
        void close() {
            sender.close();
        }
    }
}
