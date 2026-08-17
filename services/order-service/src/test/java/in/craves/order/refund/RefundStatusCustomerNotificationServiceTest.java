package in.craves.order.refund;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.craves.order.refund.RefundStatusModels.RefundStatusChangedData;
import in.craves.order.service.NotificationOutboxEvent;
import in.craves.order.service.NotificationOutboxRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RefundStatusCustomerNotificationServiceTest {

    @Test
    void recordsCompletedRefundNotificationWithStableEventKeyAndPayload() {
        NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
        when(repository.savePendingIfAbsent(any())).thenReturn(true);
        RefundStatusCustomerNotificationService service =
            new RefundStatusCustomerNotificationService(repository);

        UUID eventId = UUID.randomUUID();
        UUID checkoutId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID refundId = UUID.randomUUID();
        Instant updatedAt = Instant.parse("2026-07-17T01:02:03Z");

        RefundStatusChangedData data = new RefundStatusChangedData(
            refundId,
            checkoutId,
            orderId,
            customerId,
            "CRVREFUND123",
            new BigDecimal("125.50"),
            "INR",
            "CHEF_ACCEPTANCE_TIMEOUT",
            "REFUNDED",
            "SUCCESS",
            "cashfree-refund-1",
            updatedAt
        );

        assertThat(service.record(eventId, checkoutId, customerId, data)).isTrue();

        ArgumentCaptor<NotificationOutboxEvent> captor =
            ArgumentCaptor.forClass(NotificationOutboxEvent.class);
        verify(repository).savePendingIfAbsent(captor.capture());

        NotificationOutboxEvent event = captor.getValue();
        assertThat(event.eventKey()).isEqualTo("refund-status-" + eventId);
        assertThat(event.eventType()).isEqualTo("REFUNDED");
        assertThat(event.userIdentityId()).isEqualTo(customerId);
        assertThat(event.userRole()).isEqualTo("CUSTOMER");
        assertThat(event.channel()).isEqualTo("IN_APP");
        assertThat(event.templateCode()).isEqualTo("REFUND_COMPLETED_IN_APP");
        assertThat(event.targetId()).isEqualTo(orderId);
        assertThat(event.payload())
            .containsEntry("eventId", eventId.toString())
            .containsEntry("refundAmount", "125.50")
            .containsEntry("currency", "INR")
            .containsEntry("providerStatus", "SUCCESS")
            .containsEntry("cfRefundId", "cashfree-refund-1");
    }

    @Test
    void rejectsUnsupportedStatusInsteadOfCreatingAnAmbiguousMessage() {
        NotificationOutboxRepository repository = mock(NotificationOutboxRepository.class);
        RefundStatusCustomerNotificationService service =
            new RefundStatusCustomerNotificationService(repository);

        RefundStatusChangedData data = new RefundStatusChangedData(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "CRVREFUND456",
            BigDecimal.ONE,
            "INR",
            "CHEF_DECLINED",
            "UNKNOWN",
            "PENDING",
            null,
            Instant.now()
        );

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.record(
            UUID.randomUUID(),
            data.checkoutId(),
            data.customerIdentityId(),
            data
        )).isInstanceOf(IllegalArgumentException.class)
          .hasMessageContaining("Unsupported customer refund notification status");
    }
}
