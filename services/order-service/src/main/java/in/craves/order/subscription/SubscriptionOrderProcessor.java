package in.craves.order.subscription;

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
@ConditionalOnProperty(prefix = "craves.subscription-orders", name = "consumer-enabled", havingValue = "true")
public class SubscriptionOrderProcessor {
    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionOrderProcessor.class);

    private final SubscriptionOrderProperties properties;
    private final SubscriptionOrderService service;
    private final ServiceBusProcessorClient processor;

    public SubscriptionOrderProcessor(
        SubscriptionOrderProperties properties,
        SubscriptionOrderService service
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
            .subscriptionName(properties.getSubscriptionName())
            .receiveMode(ServiceBusReceiveMode.PEEK_LOCK)
            .disableAutoComplete()
            .maxConcurrentCalls(properties.getMaxConcurrentMessages())
            .prefetchCount(properties.getPrefetchCount())
            .maxAutoLockRenewDuration(properties.maxAutoLockRenewDuration())
            .processMessage(this::process)
            .processError(context -> LOGGER.error(
                "Subscription order processor error entityPath={}",
                context.getEntityPath(), context.getException()
            ))
            .buildProcessorClient();
    }

    @PostConstruct
    void start() {
        processor.start();
        LOGGER.info(
            "SUBSCRIPTION_ORDER_REQUESTED processor started topic={} subscription={}",
            properties.getTopicName(), properties.getSubscriptionName()
        );
    }

    private void process(ServiceBusReceivedMessageContext context) {
        try {
            var result = service.accept(context.getMessage().getBody().toString());
            context.complete();
            LOGGER.info(
                "Subscription order request completed messageId={} occurrenceId={} orderId={} created={}",
                context.getMessage().getMessageId(), result.occurrenceId(), result.orderId(), result.created()
            );
        } catch (ResponseStatusException exception) {
            deadLetter(context, "INVALID_SUBSCRIPTION_ORDER_REQUEST", exception);
        } catch (Exception exception) {
            if (context.getMessage().getDeliveryCount() >= properties.getMaxDeliveryAttempts()) {
                deadLetter(context, "SUBSCRIPTION_ORDER_CREATION_FAILED", exception);
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
            "Subscription order request dead-lettered messageId={} reason={}",
            context.getMessage().getMessageId(), reason
        );
    }

    private static String safe(Throwable error) {
        String value = error == null || error.getMessage() == null
            ? (error == null ? "Unknown subscription order error" : error.getClass().getSimpleName())
            : error.getMessage();
        value = value.replace('\n', ' ').replace('\r', ' ').trim();
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }

    @PreDestroy
    void close() {
        processor.close();
    }
}
