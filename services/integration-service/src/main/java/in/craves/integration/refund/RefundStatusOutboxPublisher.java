package in.craves.integration.refund;

import com.azure.core.util.BinaryData;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import in.craves.integration.refund.RefundRepository.RefundStatusOutboxRecord;
import jakarta.annotation.PreDestroy;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(prefix = "craves.refund", name = "status-publisher-enabled", havingValue = "true")
public class RefundStatusOutboxPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(RefundStatusOutboxPublisher.class);
    private static final int MAX_ATTEMPTS = 10;
    private static final long MAX_RETRY_DELAY_SECONDS = 3600;

    private final RefundWorkflowProperties properties;
    private final RefundRepository repository;
    private final ServiceBusSenderClient senderClient;

    public RefundStatusOutboxPublisher(
        RefundWorkflowProperties properties,
        RefundRepository repository
    ) {
        this.properties = properties;
        this.repository = repository;
        validateConfiguration(properties);

        ServiceBusClientBuilder builder = new ServiceBusClientBuilder();
        if (StringUtils.hasText(properties.getConnectionString())) {
            builder.connectionString(properties.getConnectionString());
        } else {
            builder.credential(
                properties.getFullyQualifiedNamespace(),
                new DefaultAzureCredentialBuilder().build()
            );
        }
        this.senderClient = builder.sender()
            .topicName(properties.getTopicName())
            .buildClient();
    }

    @Scheduled(fixedDelayString = "${craves.refund.status-outbox-fixed-delay-ms:5000}")
    public void publish() {
        UUID lockToken = UUID.randomUUID();
        List<RefundStatusOutboxRecord> records = repository.claimStatusOutbox(
            properties.validatedStatusOutboxBatchSize(),
            MAX_ATTEMPTS,
            properties.validatedStaleLockSeconds(),
            lockToken
        );
        for (RefundStatusOutboxRecord record : records) {
            publishOne(record, lockToken);
        }
    }

    private void publishOne(RefundStatusOutboxRecord record, UUID lockToken) {
        try {
            ServiceBusMessage message = new ServiceBusMessage(BinaryData.fromString(record.payloadJson()))
                .setMessageId(record.id().toString())
                .setCorrelationId(record.correlationId().toString())
                .setSubject(record.eventType())
                .setContentType("application/json");
            message.getApplicationProperties().put("eventType", record.eventType());
            message.getApplicationProperties().put("eventVersion", record.eventVersion());
            message.getApplicationProperties().put("source", "integration-service");
            message.getApplicationProperties().put("subject", record.subject().toString());
            senderClient.sendMessage(message);
            repository.markStatusPublished(record.id(), lockToken, message.getMessageId());
            LOGGER.info(
                "Refund status event published eventId={} eventType={} subject={}",
                record.id(),
                record.eventType(),
                record.subject()
            );
        } catch (RuntimeException exception) {
            Instant nextAttemptAt = Instant.now().plusSeconds(retryDelaySeconds(record.attemptCount()));
            repository.markStatusPublishFailed(
                record,
                lockToken,
                MAX_ATTEMPTS,
                nextAttemptAt,
                safeMessage(exception)
            );
            LOGGER.error(
                "Refund status publication failed eventId={} attempt={}",
                record.id(),
                record.attemptCount(),
                exception
            );
        }
    }

    private static void validateConfiguration(RefundWorkflowProperties properties) {
        if (!StringUtils.hasText(properties.getConnectionString())
            && !StringUtils.hasText(properties.getFullyQualifiedNamespace())) {
            throw new IllegalStateException(
                "SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE is required when refund status publication is enabled"
            );
        }
        if (!StringUtils.hasText(properties.getTopicName())) {
            throw new IllegalStateException("Refund domain-event topic name is required");
        }
    }

    private static long retryDelaySeconds(int attempt) {
        int exponent = Math.max(0, Math.min(attempt - 1, 20));
        long delay = 5L * (1L << exponent);
        return Math.min(MAX_RETRY_DELAY_SECONDS, delay);
    }

    private static String safeMessage(Throwable exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        String normalized = message.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() > 1000 ? normalized.substring(0, 1000) : normalized;
    }

    @PreDestroy
    void close() {
        senderClient.close();
    }
}
