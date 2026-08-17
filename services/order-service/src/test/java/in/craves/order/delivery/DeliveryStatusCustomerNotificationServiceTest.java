package in.craves.order.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import in.craves.order.delivery.DeliveryStatusModels.DeliveryStatusChangedData;
import in.craves.order.service.NotificationOutboxRepository;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeliveryStatusCustomerNotificationServiceTest {
    @Test
    void recordsMeaningfulCustomerStatusWithStableEventKey() {
        NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
        DeliveryStatusCustomerNotificationService service =
            new DeliveryStatusCustomerNotificationService(repository);
        UUID eventId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(repository.savePendingIfAbsent(argThat(event ->
            event.eventKey().equals("delivery-status-" + eventId)
        ))).thenReturn(true);

        boolean inserted = service.record(
            eventId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            data(orderId, "IN_TRANSIT")
        );

        assertThat(inserted).isTrue();
        verify(repository).savePendingIfAbsent(argThat(event ->
            event.eventType().equals("DELIVERY_IN_TRANSIT")
                && event.aggregateId().equals(orderId)
                && event.userRole().equals("CUSTOMER")
                && event.channel().equals("IN_APP")
                && !event.payload().containsKey("providerDeliveryId")
        ));
    }

    @Test
    void doesNotNotifyForInternalSearchingState() {
        NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
        DeliveryStatusCustomerNotificationService service =
            new DeliveryStatusCustomerNotificationService(repository);

        boolean inserted = service.record(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            data(UUID.randomUUID(), "SEARCHING")
        );

        assertThat(inserted).isFalse();
        verifyNoInteractions(repository);
    }

    private static DeliveryStatusChangedData data(UUID orderId, String status) {
        return new DeliveryStatusChangedData(
            UUID.randomUUID(),
            UUID.randomUUID(),
            orderId,
            "borzo",
            "provider-order-1",
            status,
            "https://track.example/1",
            Instant.parse("2026-07-28T08:30:00Z")
        );
    }
}
