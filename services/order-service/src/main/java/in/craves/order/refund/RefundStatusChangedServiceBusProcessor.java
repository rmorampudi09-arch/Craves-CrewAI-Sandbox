package in.craves.order.refund;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.azure.messaging.servicebus.models.DeadLetterOptions;
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.order.config.RefundStatusConsumerProperties;
import in.craves.order.refund.RefundStatusEventValidator.RefundStatusValidationException;
import in.craves.order.refund.RefundStatusModels.EventEnvelope;
import in.craves.order.refund.RefundStatusModels.RefundStatusChangedData;
import in.craves.order.refund.RefundStatusUpdateService.RefundStatusRetryableException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(
    prefix = "craves.refund-status-consumer",
    name = "enabled",
    havingValue = "true"
)
public class RefundStatusChangedServiceBusProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(RefundStatusChangedServiceBusProcessor.class);

    private final RefundStatusUpdateService updateService;
    private final RefundStatusConsumerProperties properties;
    private final ObjectMapper objectMapper;
    private final ServiceBusProcessorClient processorClient;

    public RefundStatusChangedServiceBusProcessor(
        RefundStatusUpdateService updateService,
        RefundStatusConsumerProperties properties,
        ObjectMapper objectMapper
    ) {
        this.updateService = updateService;
        this.properties = properties;
        this.objectMapper = objectMapper;
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

        this.processorClient = builder
            .processor()
            .topicName(properties.getTopicName())
            .subscriptionName(properties.getSubscriptionName())
            .receiveMode(ServiceBusReceiveMode.PEEK_LOCK)
            .disableAutoComplete()
            .maxConcurrentCalls(properties.validatedMaxConcurrentMessages())
            .prefetchCount(properties.validatedPrefetchCount())
            .maxAutoLockRenewDuration(properties.maxAutoLockRenewDuration())
            .processMessage(this::processMessage)
            .processError(context -> LOGGER.error(
                "Refund status Service Bus processor error entityPath={}",
                context.getEntityPath(),
                context.getException()
            ))
            .buildProcessorClient();
    }

    @PostConstruct
    void start() {
        processorClient.start();
        LOGGER.info(
            "REFUND_STATUS_CHANGED processor started topic={} subscription={}",
            properties.getTopicName(),
            properties.getSubscriptionName()
        );
    }

    private void processMessage(ServiceBusReceivedMessageContext context) {
        try {
            String rawPayload = context.getMessage().getBody().toString();
            JavaType eventType = objectMapper.getTypeFactory().constructParametricType(
                EventEnvelope.class,
                RefundStatusChangedData.class
            );
            EventEnvelope<RefundStatusChangedData> event = objectMapper.readValue(rawPayload, eventType);
            boolean applied = updateService.accept(event, rawPayload);
            context.complete();
            LOGGER.info(
                "REFUND_STATUS_CHANGED message completed messageId={} eventId={} applied={}",
                context.getMessage().getMessageId(),
                event.eventId(),
                applied
            );
        } catch (RefundStatusValidationException exception) {
            deadLetter(context, "INVALID_REFUND_STATUS", exception);
        } catch (RefundStatusRetryableException exception) {
            retryOrDeadLetter(context, "REFUND_STATUS_NOT_READY", exception);
        } catch (Exception exception) {
            retryOrDeadLetter(context, "REFUND_STATUS_PROCESSING_FAILED", exception);
        }
    }

    private void retryOrDeadLetter(
        ServiceBusReceivedMessageContext context,
        String reason,
        Exception exception
    ) {
        long deliveryCount = context.getMessage().getDeliveryCount();
        if (deliveryCount >= properties.validatedMaxDeliveryAttempts()) {
            deadLetter(context, reason, exception);
            return;
        }
        LOGGER.warn(
            "Refund status message will be retried messageId={} deliveryCount={} reason={}",
            context.getMessage().getMessageId(),
            deliveryCount,
            safeMessage(exception)
        );
        context.abandon();
    }

    private void deadLetter(
        ServiceBusReceivedMessageContext context,
        String reason,
        Exception exception
    ) {
        LOGGER.error(
            "Refund status message dead-lettered messageId={} reason={} error={}",
            context.getMessage().getMessageId(),
            reason,
            safeMessage(exception)
        );
        context.deadLetter(new DeadLetterOptions()
            .setDeadLetterReason(reason)
            .setDeadLetterErrorDescription(safeMessage(exception)));
    }

    private static void validateConfiguration(RefundStatusConsumerProperties properties) {
        if (!StringUtils.hasText(properties.getConnectionString())
            && !StringUtils.hasText(properties.getFullyQualifiedNamespace())) {
            throw new IllegalStateException(
                "CRAVES_SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE is required when the refund status consumer is enabled"
            );
        }
        if (!StringUtils.hasText(properties.getTopicName())
            || !StringUtils.hasText(properties.getSubscriptionName())) {
            throw new IllegalStateException("Refund status topic and subscription are required");
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
        processorClient.close();
    }
}
