package in.craves.notification.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import in.craves.notification.api.CreateNotificationRequest;
import in.craves.notification.domain.NotificationChannel;
import in.craves.notification.repository.NotificationRepository;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class NotificationServiceChannelPolicyTest {
    @Test
    void rejectsTransactionalSmsFromNotificationService() {
        NotificationService service = new NotificationService(
            mock(NotificationRepository.class),
            new ImportantEmailPolicyProperties()
        );
        CreateNotificationRequest request = new CreateNotificationRequest(
            "sms-test",
            "order-service",
            "ORDER_CREATED",
            UUID.randomUUID(),
            "CUSTOMER",
            NotificationChannel.SMS,
            null,
            "+919999999999",
            "Order created",
            "Your order was created",
            "ORDER",
            UUID.randomUUID(),
            Map.of(),
            5
        );

        assertThatThrownBy(() -> service.create(request))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("Firebase Phone Authentication");
    }
}
