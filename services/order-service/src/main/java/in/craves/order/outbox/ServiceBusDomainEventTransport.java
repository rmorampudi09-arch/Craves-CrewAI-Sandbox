package in.craves.order.outbox;

import com.azure.core.util.BinaryData;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import in.craves.order.config.ServiceBusDomainEventProperties;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(
    prefix = "craves.domain-events.service-bus",
    name = "enabled",
    havingValue = "true"
)
public class ServiceBusDomainEventTransport implements DomainEventTransport {
    private final ServiceBusSenderClient senderClient;

    public ServiceBusDomainEventTransport(ServiceBusDomainEventProperties properties) {
        if (!StringUtils.hasText(properties.getFullyQualifiedNamespace())) {
            throw new IllegalStateException(
                "CRAVES_SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE is required when domain event publication is enabled"
            );
        }
        if (!StringUtils.hasText(properties.getTopicName())) {
            throw new IllegalStateException(
                "CRAVES_DOMAIN_EVENTS_TOPIC_NAME is required when domain event publication is enabled"
            );
        }

        this.senderClient = new ServiceBusClientBuilder()
            .credential(
                properties.getFullyQualifiedNamespace().trim(),
                new DefaultAzureCredentialBuilder().build()
            )
            .sender()
            .topicName(properties.getTopicName().trim())
            .buildClient();
    }

    @Override
    public String publish(OrderDomainOutboxRecord record) {
        ServiceBusMessage message = new ServiceBusMessage(BinaryData.fromString(record.payloadJson()))
            .setMessageId(record.id().toString())
            .setCorrelationId(record.correlationId().toString())
            .setSubject(record.eventType())
            .setContentType("application/json");

        // event_type is the canonical property used by Service Bus subscription SQL filters.
        // Keep eventType temporarily for backward compatibility with existing consumers and diagnostics.
        message.getApplicationProperties().put("event_type", record.eventType());
        message.getApplicationProperties().put("eventType", record.eventType());
        message.getApplicationProperties().put("eventVersion", record.eventVersion());
        message.getApplicationProperties().put("source", record.source());
        message.getApplicationProperties().put("subject", record.subject());
        message.getApplicationProperties().put("aggregateId", record.aggregateId().toString());

        senderClient.sendMessage(message);
        return message.getMessageId();
    }

    @PreDestroy
    void close() {
        senderClient.close();
    }
}
