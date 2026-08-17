package in.craves.integration.delivery.command;

import com.azure.core.credential.TokenCredential;
import com.azure.identity.DefaultAzureCredentialBuilder;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
@ConditionalOnExpression(
    "${craves.delivery-command.enabled:false} || "
        + "${craves.delivery-command.status-publisher-enabled:false}"
)
public class DeliveryServiceBusConfiguration {

    @Bean
    DeliveryServiceBusClientFactory deliveryServiceBusClientFactory(DeliveryCommandProperties properties) {
        return new DeliveryServiceBusClientFactory(properties);
    }

    @Bean(destroyMethod = "close")
    @Qualifier("deliveryCommandSender")
    @ConditionalOnProperty(
        prefix = "craves.delivery-command",
        name = "enabled",
        havingValue = "true"
    )
    ServiceBusSenderClient deliveryCommandSender(DeliveryServiceBusClientFactory factory,
                                                   DeliveryCommandProperties properties) {
        return factory.newBuilder()
            .sender()
            .queueName(properties.getQueueName())
            .buildClient();
    }

    @Bean(destroyMethod = "close")
    @Qualifier("deliveryDomainEventSender")
    @ConditionalOnProperty(
        prefix = "craves.delivery-command",
        name = "status-publisher-enabled",
        havingValue = "true"
    )
    ServiceBusSenderClient deliveryDomainEventSender(DeliveryServiceBusClientFactory factory,
                                                       DeliveryCommandProperties properties) {
        return factory.newBuilder()
            .sender()
            .topicName(properties.getTopicName())
            .buildClient();
    }

    public static final class DeliveryServiceBusClientFactory {
        private final DeliveryCommandProperties properties;
        private final TokenCredential credential;

        DeliveryServiceBusClientFactory(DeliveryCommandProperties properties) {
            this.properties = properties;
            this.credential = StringUtils.hasText(properties.getConnectionString())
                ? null
                : new DefaultAzureCredentialBuilder().build();
        }

        ServiceBusClientBuilder newBuilder() {
            ServiceBusClientBuilder builder = new ServiceBusClientBuilder();
            if (StringUtils.hasText(properties.getConnectionString())) {
                return builder.connectionString(properties.getConnectionString());
            }
            return builder.credential(properties.getFullyQualifiedNamespace(), credential);
        }
    }
}
