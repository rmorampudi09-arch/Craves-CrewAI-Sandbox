package in.craves.integration.refund;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.azure.messaging.servicebus.models.DeadLetterOptions;
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.integration.refund.RefundEventValidator.RefundMessageValidationException;
import in.craves.integration.refund.RefundModels.EventEnvelope;
import in.craves.integration.refund.RefundModels.RefundRequestedData;
import in.craves.integration.refund.RefundRequestService.RefundNonRetryableException;
import in.craves.integration.refund.RefundRequestService.RefundRetryableException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(prefix = "craves.refund", name = "consumer-enabled", havingValue = "true")
public class RefundRequestedServiceBusProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(RefundRequestedServiceBusProcessor.class);

    private final RefundRequestService refundRequestService;
    private final RefundWorkflowProperties properties;
    private final ObjectMapper objectMapper;
    private final ServiceBusProcessorClient processorClient;

    public RefundRequestedServiceBusProcessor(
        RefundRequestService refundRequestService,
        RefundWorkflowProperties properties,
        ObjectMapper objectMapper
    ) {
        this.refundRequestService = refundRequestService;
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
                "Refund Service Bus processor error entityPath={}",
                context.getEntityPath(),
                context.getException()
            ))
            .buildProcessorClient();
    }

    @PostConstruct
    void start() {
        processorClient.start();
        LOGGER.info(
            "REFUND_REQUESTED processor started topic={} subscription={}",
            properties.getTopicName(),
            properties.getSubscriptionName()
        );
    }

    private void processMessage(ServiceBusReceivedMessageContext context) {
        try {
            String rawPayload = context.getMessage().getBody().toString();
            JavaType eventType = objectMapper.getTypeFactory().constructParametricType(
                EventEnvelope.class,
                RefundRequestedData.class
            );
            EventEnvelope<RefundRequestedData> event = objectMapper.readValue(rawPayload, eventType);
            boolean created = refundRequestService.accept(event, rawPayload);
            context.complete();
            LOGGER.info(
                "REFUND_REQUESTED message completed messageId={} eventId={} created={}",
                context.getMessage().getMessageId(),
                event.eventId(),
                created
            );
        } catch (RefundMessageValidationException | RefundNonRetryableException exception) {
            deadLetter(context, "INVALID_REFUND_REQUEST", exception);
        } catch (RefundRetryableException exception) {
            retryOrDeadLetter(context, "REFUND_REQUEST_NOT_READY", exception);
        } catch (Exception exception) {
            retryOrDeadLetter(context, "REFUND_REQUEST_PROCESSING_FAILED", exception);
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
            "Refund message will be retried messageId={} deliveryCount={} reason={}",
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
            "Refund message dead-lettered messageId={} reason={} error={}",
            context.getMessage().getMessageId(),
            reason,
            safeMessage(exception)
        );
        context.deadLetter(new DeadLetterOptions()
            .setDeadLetterReason(reason)
            .setDeadLetterErrorDescription(safeMessage(exception)));
    }

    private static void validateConfiguration(RefundWorkflowProperties properties) {
        if (!StringUtils.hasText(properties.getConnectionString())
            && !StringUtils.hasText(properties.getFullyQualifiedNamespace())) {
            throw new IllegalStateException(
                "SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE is required when the refund consumer is enabled"
            );
        }
        if (!StringUtils.hasText(properties.getTopicName())
            || !StringUtils.hasText(properties.getSubscriptionName())) {
            throw new IllegalStateException("Refund topic and subscription names are required");
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
