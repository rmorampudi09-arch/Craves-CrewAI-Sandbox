package in.craves.notification.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.craves.notification.delivery.NotificationDeliveryModels.DeliveryWorkItem;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class NotificationDeliveryRepositoryTest {
    @Test
    void repeatedDeadLetterRefreshesTheLatestProjection() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);
        NotificationDeliveryRepository repository = new NotificationDeliveryRepository(jdbcTemplate);
        DeliveryWorkItem item = new DeliveryWorkItem(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "PUSH",
            null,
            "Order update",
            "Your order status changed",
            "ORDER",
            UUID.randomUUID(),
            Map.of(),
            1,
            3,
            UUID.randomUUID()
        );

        repository.markFailure(item, 3, 30, "FCM", new IllegalStateException("second provider outage"));

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(3)).update(sqlCaptor.capture(), any(Object[].class));
        List<String> statements = sqlCaptor.getAllValues();
        String deadLetterStatement = statements.stream()
            .filter(sql -> sql.contains("channel_delivery_dead_letter"))
            .findFirst()
            .orElseThrow();

        assertThat(deadLetterStatement).contains("ON CONFLICT (notification_request_id) DO UPDATE SET");
        assertThat(deadLetterStatement).contains("final_error_code = EXCLUDED.final_error_code");
        assertThat(deadLetterStatement).contains("attempt_count = EXCLUDED.attempt_count");
        assertThat(deadLetterStatement).doesNotContain("DO NOTHING");
    }
}
