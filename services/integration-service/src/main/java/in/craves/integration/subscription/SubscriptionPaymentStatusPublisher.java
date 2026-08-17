package in.craves.integration.subscription;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import in.craves.integration.subscription.SubscriptionPaymentRepository.OutboxRecord;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(prefix = "craves.subscription-payments", name = "status-publisher-enabled", havingValue = "true")
public class SubscriptionPaymentStatusPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionPaymentStatusPublisher.class);

    private final SubscriptionPaymentProperties properties;
    private final SubscriptionPaymentRepository repository;
    private final ServiceBusSenderClient sender;

    public SubscriptionPaymentStatusPublisher(
        SubscriptionPaymentProperties properties,
        SubscriptionPaymentRepository repository
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

    @Scheduled(fixedDelayString = "${craves.subscription-payments.outbox-fixed-delay-ms:5000}")
    public void publish() {
        for (OutboxRecord record : repository.claimOutbox(
            properties.getOutboxBatchSize(),
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
                    "Subscription payment status publish failed outboxId={} attempt={}",
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
