package in.craves.integration.delivery.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.lang.reflect.Method;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

class DeliveryServiceBusPublisherIsolationTest {

    @Test
    void publisherOnlyDoesNotRequireDeliveryCommandSender() {
        ObjectProvider<ServiceBusSenderClient> commandProvider = providerReturning(null);
        ServiceBusSenderClient domainSender = mock(ServiceBusSenderClient.class);
        ObjectProvider<ServiceBusSenderClient> domainProvider = providerReturning(domainSender);

        DeliveryServiceBusPublisher publisher = new DeliveryServiceBusPublisher(
            commandProvider,
            domainProvider,
            new ObjectMapper()
        );

        publisher.publishDomainEvent(
            UUID.randomUUID(),
            "DELIVERY_STATUS_CHANGED",
            UUID.randomUUID(),
            new ObjectMapper().createObjectNode().put("status", "IN_TRANSIT")
        );

        verify(domainSender).sendMessage(any(ServiceBusMessage.class));
        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> publisher.cancelScheduled(1L)
        );
        assertEquals("Delivery command Service Bus sender is not enabled", error.getMessage());
    }

    @Test
    void commandOnlyDoesNotRequireDomainEventSender() {
        ServiceBusSenderClient commandSender = mock(ServiceBusSenderClient.class);
        ObjectProvider<ServiceBusSenderClient> commandProvider = providerReturning(commandSender);
        ObjectProvider<ServiceBusSenderClient> domainProvider = providerReturning(null);

        DeliveryServiceBusPublisher publisher = new DeliveryServiceBusPublisher(
            commandProvider,
            domainProvider,
            new ObjectMapper()
        );

        publisher.cancelScheduled(42L);
        verify(commandSender).cancelScheduledMessage(42L);

        IllegalStateException error = assertThrows(
            IllegalStateException.class,
            () -> publisher.publishDomainEvent(
                UUID.randomUUID(),
                "DELIVERY_STATUS_CHANGED",
                UUID.randomUUID(),
                new ObjectMapper().createObjectNode()
            )
        );
        assertEquals("Delivery domain-event Service Bus sender is not enabled", error.getMessage());
    }

    @Test
    void senderBeansAreConditionedOnTheirOwnFeatureFlags() throws Exception {
        Method commandMethod = DeliveryServiceBusConfiguration.class.getDeclaredMethod(
            "deliveryCommandSender",
            DeliveryServiceBusConfiguration.DeliveryServiceBusClientFactory.class,
            DeliveryCommandProperties.class
        );
        ConditionalOnProperty commandCondition = commandMethod.getAnnotation(ConditionalOnProperty.class);
        assertEquals("craves.delivery-command", commandCondition.prefix());
        assertEquals("enabled", commandCondition.name()[0]);
        assertEquals("true", commandCondition.havingValue());

        Method domainMethod = DeliveryServiceBusConfiguration.class.getDeclaredMethod(
            "deliveryDomainEventSender",
            DeliveryServiceBusConfiguration.DeliveryServiceBusClientFactory.class,
            DeliveryCommandProperties.class
        );
        ConditionalOnProperty domainCondition = domainMethod.getAnnotation(ConditionalOnProperty.class);
        assertEquals("craves.delivery-command", domainCondition.prefix());
        assertEquals("status-publisher-enabled", domainCondition.name()[0]);
        assertEquals("true", domainCondition.havingValue());
    }

    @SuppressWarnings("unchecked")
    private static ObjectProvider<ServiceBusSenderClient> providerReturning(ServiceBusSenderClient sender) {
        ObjectProvider<ServiceBusSenderClient> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(sender);
        return provider;
    }
}
