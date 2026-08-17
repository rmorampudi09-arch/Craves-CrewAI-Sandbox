package in.craves.integration.delivery.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

class DeliveryLeaseRecoveryRepositoryTest {

    @Test
    void movesExhaustedStaleLeasesToExplicitTerminalStates() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.update(anyString(), eq(20), eq(5)))
            .thenReturn(1, 2, 3);
        DeliveryLeaseRecoveryRepository repository =
            new DeliveryLeaseRecoveryRepository(jdbc);

        assertThat(
            repository.deadLetterExhaustedCreateReconciliationLeases(20, 5)
        ).isEqualTo(1);
        assertThat(
            repository.deadLetterExhaustedWebhookLeases(20, 5)
        ).isEqualTo(2);
        assertThat(
            repository.deadLetterExhaustedTrackingLeases(20, 5)
        ).isEqualTo(3);

        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbc, org.mockito.Mockito.times(3))
            .update(sql.capture(), eq(20), eq(5));

        assertThat(sql.getAllValues().get(0))
            .contains("status = 'DEAD_LETTER'")
            .contains("reconciliation_processing_started_at");
        assertThat(sql.getAllValues().get(1))
            .contains("processing_status = 'DEAD_LETTER'")
            .contains("processing_started_at");
        assertThat(sql.getAllValues().get(2))
            .contains("tracking_dead_lettered_at = now()")
            .contains("tracking_processing_started_at");
    }
}
