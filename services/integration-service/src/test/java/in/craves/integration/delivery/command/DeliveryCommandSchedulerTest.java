package in.craves.integration.delivery.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.craves.integration.delivery.command.DeliveryCommandModels.ChefAcceptedOrderData;
import in.craves.integration.delivery.command.DeliveryCommandModels.DeliveryCommandMessage;
import in.craves.integration.delivery.command.DeliveryCommandModels.EventEnvelope;
import in.craves.integration.delivery.command.DeliveryCommandRepository.CommandRecord;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.QuoteRequest;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.Stop;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class DeliveryCommandSchedulerTest {

    @Test
    void schedulesTenMinutesBeforeReadyAtAndPersistsIntelligenceContext() {
        DeliveryCommandRepository repository = mock(DeliveryCommandRepository.class);
        DeliveryServiceBusPublisher publisher = mock(DeliveryServiceBusPublisher.class);
        DeliveryCommandProperties properties = new DeliveryCommandProperties();
        properties.setLeadTimeMinutes(10);
        Instant now = Instant.parse("2026-07-14T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        DeliveryCommandScheduler scheduler = new DeliveryCommandScheduler(
            repository, publisher, properties, clock
        );

        EventEnvelope<ChefAcceptedOrderData> event = event(now.plusSeconds(30 * 60));
        when(repository.createOrFind(any())).thenAnswer(invocation -> {
            DeliveryCommandMessage message = invocation.getArgument(0);
            return new CommandRecord(
                message.commandId(), message.chefSubOrderId(), message.orderId(),
                "SCHEDULED", 0, message, null, null,
                null, null, null, 0,
                0, null, null
            );
        });
        when(publisher.schedule(any())).thenReturn(
            new DeliveryServiceBusPublisher.ScheduledMessage(7001L, "delivery-command:test")
        );
        when(repository.recordScheduled(any(), anyLong(), any())).thenReturn(true);

        var receipt = scheduler.schedule(event);

        assertThat(receipt.dispatchAt()).isEqualTo(now.plusSeconds(20 * 60));
        assertThat(receipt.scheduledSequenceNumber()).isEqualTo(7001L);
        assertThat(receipt.duplicate()).isFalse();

        ArgumentCaptor<DeliveryCommandMessage> command = ArgumentCaptor.forClass(DeliveryCommandMessage.class);
        verify(repository).createOrFind(command.capture());
        assertThat(command.getValue().chefSubOrderId()).isEqualTo(event.data().chefSubOrderId());
        assertThat(command.getValue().idempotencyKey()).isEqualTo(event.data().chefSubOrderId().toString());
        assertThat(command.getValue().area()).isEqualTo("Madhapur");
        assertThat(command.getValue().distanceKm()).isBetween(4.0, 5.0);
        assertThat(command.getValue().orderHour()).isEqualTo(17);
        assertThat(command.getValue().dayOfWeek()).isEqualTo(1);
    }

    private static EventEnvelope<ChefAcceptedOrderData> event(Instant readyAt) {
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID subOrderId = UUID.randomUUID();
        QuoteRequest request = new QuoteRequest(
            "Packaged food",
            2000,
            true,
            new Stop(
                "Madhapur, Hyderabad", "Chef", "919999999991",
                new BigDecimal("17.4483"), new BigDecimal("78.3915"),
                null, null, "Pickup"
            ),
            new Stop(
                "Gachibowli, Hyderabad", "Customer", "919999999992",
                new BigDecimal("17.4401"), new BigDecimal("78.3489"),
                null, null, "Dropoff"
            )
        );
        return new EventEnvelope<>(
            eventId,
            DeliveryCommandModels.CHEF_ACCEPTED_ORDER,
            "1.0",
            Instant.parse("2026-07-14T12:00:00Z"),
            orderId,
            null,
            "order-service",
            "chef-sub-order/" + subOrderId,
            new ChefAcceptedOrderData(orderId, subOrderId, readyAt, null, null, request)
        );
    }
}
