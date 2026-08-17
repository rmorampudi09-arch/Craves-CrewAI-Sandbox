package in.craves.subscription.payment;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.azure.messaging.servicebus.models.DeadLetterOptions;
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode;
import in.craves.subscription.capacity.CapacityFailureReporter;
import in.craves.subscription.exception.ApiException;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Component
@ConditionalOnProperty(prefix = "craves.subscription.payment-status-consumer", name = "enabled", havingValue = "true")
public class SubscriptionPaymentStatusProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionPaymentStatusProcessor.class);

    private final SubscriptionPaymentStatusProperties properties;
    private final SubscriptionPaymentStatusService service;
    private final CapacityFailureReporter capacityFailureReporter;
    private final ServiceBusProcessorClient processor;

    public SubscriptionPaymentStatusProcessor(
        SubscriptionPaymentStatusProperties properties,
        SubscriptionPaymentStatusService service,
        CapacityFailureReporter capacityFailureReporter
    ) {
        this.properties = properties;
        this.service = service;
        this.capacityFailureReporter = capacityFailureReporter;
        ServiceBusClientBuilder builder = new ServiceBusClientBuilder();
        if (StringUtils.hasText(properties.getConnectionString())) {
            builder.connectionString(properties.getConnectionString());
        } else {
            builder.credential(
                properties.getFullyQualifiedNamespace(),
                new DefaultAzureCredentialBuilder().build()
            );
        }
        this.processor = builder.processor()
            .topicName(properties.getTopicName())
            .subscriptionName(properties.getSubscriptionName())
            .receiveMode(ServiceBusReceiveMode.PEEK_LOCK)
            .disableAutoComplete()
            .maxConcurrentCalls(properties.getMaxConcurrentMessages())
            .prefetchCount(properties.getPrefetchCount())
            .maxAutoLockRenewDuration(properties.maxAutoLockRenewDuration())
            .processMessage(this::process)
            .processError(context -> LOGGER.error(
                "Subscription payment status processor error entityPath={}",
                context.getEntityPath(), context.getException()
            ))
            .buildProcessorClient();
    }

    @PostConstruct
    void start() {
        processor.start();
        LOGGER.info(
            "SUBSCRIPTION_PAYMENT_STATUS_CHANGED processor started topic={} subscription={}",
            properties.getTopicName(), properties.getSubscriptionName()
        );
    }

    private void process(ServiceBusReceivedMessageContext context) {
        try {
            boolean changed = service.accept(context.getMessage().getBody().toString());
            context.complete();
            LOGGER.info(
                "Subscription payment status completed messageId={} changed={}",
                context.getMessage().getMessageId(), changed
            );
        } catch (ResponseStatusException exception) {
            deadLetter(context, "INVALID_SUBSCRIPTION_PAYMENT_STATUS", exception);
        } catch (ApiException exception) {
            reportCapacityConflictIfPossible(context, exception);
            retryOrDeadLetter(context, "SUBSCRIPTION_PAYMENT_CAPACITY_CONFLICT", exception);
        } catch (Exception exception) {
            retryOrDeadLetter(context, "SUBSCRIPTION_PAYMENT_STATUS_FAILED", exception);
        }
    }

    private void reportCapacityConflictIfPossible(
        ServiceBusReceivedMessageContext context,
        ApiException exception
    ) {
        try {
            UUID subscriptionId = SubscriptionPaymentStatusEventIdentity.subscriptionId(
                context.getMessage().getBody().toString()
            );
            capacityFailureReporter.reportPaidCapacityConflict(
                subscriptionId,
                exception.getCode(),
                exception.getMessage()
            );
        } catch (RuntimeException reportingError) {
            LOGGER.error(
                "Could not persist paid capacity conflict incident messageId={}",
                context.getMessage().getMessageId(),
                reportingError
            );
        }
    }

    private void retryOrDeadLetter(
        ServiceBusReceivedMessageContext context,
        String deadLetterReason,
        Throwable error
    ) {
        if (context.getMessage().getDeliveryCount() >= properties.getMaxDeliveryAttempts()) {
            deadLetter(context, deadLetterReason, error);
        } else {
            context.abandon();
        }
    }

    private void deadLetter(ServiceBusReceivedMessageContext context, String reason, Throwable error) {
        context.deadLetter(new DeadLetterOptions()
            .setDeadLetterReason(reason)
            .setDeadLetterErrorDescription(safe(error)));
        LOGGER.error(
            "Subscription payment status dead-lettered messageId={} reason={}",
            context.getMessage().getMessageId(), reason
        );
    }

    private static String safe(Throwable error) {
        String value = error == null || error.getMessage() == null
            ? (error == null ? "Unknown error" : error.getClass().getSimpleName())
            : error.getMessage();
        value = value.replace('\n', ' ').replace('\r', ' ').trim();
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }

    @PreDestroy
    void close() {
        processor.close();
    }
}
