package in.craves.integration.subscription;

import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusProcessorClient;
import com.azure.messaging.servicebus.ServiceBusReceivedMessageContext;
import com.azure.messaging.servicebus.models.DeadLetterOptions;
import com.azure.messaging.servicebus.models.ServiceBusReceiveMode;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Component
@ConditionalOnProperty(prefix = "craves.subscription-payments", name = "consumer-enabled", havingValue = "true")
public class SubscriptionPaymentRequestedProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionPaymentRequestedProcessor.class);

    private final SubscriptionPaymentProperties properties;
    private final SubscriptionPaymentService service;
    private final ServiceBusProcessorClient processor;

    public SubscriptionPaymentRequestedProcessor(
        SubscriptionPaymentProperties properties,
        SubscriptionPaymentService service
    ) {
        this.properties = properties;
        this.service = service;
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
            .subscriptionName(properties.getRequestSubscriptionName())
            .receiveMode(ServiceBusReceiveMode.PEEK_LOCK)
            .disableAutoComplete()
            .maxConcurrentCalls(properties.getMaxConcurrentMessages())
            .prefetchCount(properties.getPrefetchCount())
            .maxAutoLockRenewDuration(properties.maxAutoLockRenewDuration())
            .processMessage(this::process)
            .processError(context -> LOGGER.error(
                "Subscription payment processor error entityPath={}",
                context.getEntityPath(), context.getException()
            ))
            .buildProcessorClient();
    }

    @PostConstruct
    void start() {
        processor.start();
        LOGGER.info(
            "SUBSCRIPTION_PAYMENT_REQUESTED processor started topic={} subscription={}",
            properties.getTopicName(), properties.getRequestSubscriptionName()
        );
    }

    private void process(ServiceBusReceivedMessageContext context) {
        try {
            boolean created = service.acceptRequested(context.getMessage().getBody().toString());
            context.complete();
            LOGGER.info(
                "Subscription payment request completed messageId={} created={}",
                context.getMessage().getMessageId(), created
            );
        } catch (ResponseStatusException exception) {
            deadLetter(context, "INVALID_SUBSCRIPTION_PAYMENT_REQUEST", exception);
        } catch (Exception exception) {
            if (context.getMessage().getDeliveryCount() >= properties.getMaxDeliveryAttempts()) {
                deadLetter(context, "SUBSCRIPTION_PAYMENT_REQUEST_FAILED", exception);
            } else {
                context.abandon();
            }
        }
    }

    private void deadLetter(ServiceBusReceivedMessageContext context, String reason, Throwable error) {
        context.deadLetter(new DeadLetterOptions()
            .setDeadLetterReason(reason)
            .setDeadLetterErrorDescription(safe(error)));
        LOGGER.error(
            "Subscription payment request dead-lettered messageId={} reason={}",
            context.getMessage().getMessageId(), reason
        );
    }

    private static String safe(Throwable error) {
        if (error == null) {
            return "Unknown subscription payment processing error";
        }
        String value = StringUtils.hasText(error.getMessage())
            ? error.getMessage()
            : error.getClass().getSimpleName();
        value = value.replace('\n', ' ').replace('\r', ' ').trim();
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }

    @PreDestroy
    void close() {
        processor.close();
    }
}
