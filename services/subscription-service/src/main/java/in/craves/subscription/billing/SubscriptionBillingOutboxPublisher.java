package in.craves.subscription.billing;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import in.craves.subscription.billing.SubscriptionBillingRepository.OutboxRecord;
import jakarta.annotation.PreDestroy;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(
    prefix = "craves.subscription.billing",
    name = "publisher-enabled",
    havingValue = "true"
)
public class SubscriptionBillingOutboxPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionBillingOutboxPublisher.class);

    private final SubscriptionBillingProperties properties;
    private final SubscriptionBillingRepository repository;
    private final ServiceBusSenderClient sender;

    public SubscriptionBillingOutboxPublisher(
        SubscriptionBillingProperties properties,
        SubscriptionBillingRepository repository
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

    @Scheduled(fixedDelayString = "${craves.subscription.billing.publisher-fixed-delay-ms:5000}")
    public void publish() {
        List<OutboxRecord> records = repository.claimOutbox(
            properties.getBatchSize(),
            properties.getMaxPublishAttempts(),
            properties.getStaleLockMinutes()
        );
        for (OutboxRecord record : records) {
            publishOne(record);
        }
    }

    private void publishOne(OutboxRecord record) {
        try {
            ServiceBusMessage message = new ServiceBusMessage(record.payload())
                .setMessageId(record.id().toString())
                .setSubject(record.eventType())
                .setCorrelationId(record.correlationId().toString())
                .setContentType("application/json");
            message.getApplicationProperties().put("event_type", record.eventType());
            message.getApplicationProperties().put("eventType", record.eventType());
            sender.sendMessage(message);
            repository.markPublished(record, record.id().toString());
            LOGGER.info(
                "Subscription payment request published outboxId={} eventType={}",
                record.id(), record.eventType()
            );
        } catch (RuntimeException exception) {
            repository.markPublishFailure(
                record,
                properties.getMaxPublishAttempts(),
                safeMessage(exception)
            );
            LOGGER.error(
                "Subscription payment request publish failed outboxId={} attempt={}",
                record.id(), record.attemptCount(), exception
            );
        }
    }

    private static String safeMessage(Throwable exception) {
        String message = exception.getMessage();
        if (!StringUtils.hasText(message)) {
            return exception.getClass().getSimpleName();
        }
        String normalized = message.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() > 1000 ? normalized.substring(0, 1000) : normalized;
    }

    @PreDestroy
    void close() {
        sender.close();
    }
}
