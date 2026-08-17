package in.craves.auth.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class AdminAccountInterventionRepositoryTest {
    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void repeatedSuspendStillCreatesAuditAndProviderWorkWithoutMutatingTokenState() throws Exception {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        ResultSet resultSet = mock(ResultSet.class);
        UUID actorIdentityId = UUID.randomUUID();
        UUID targetIdentityId = UUID.randomUUID();
        UUID correlationId = UUID.randomUUID();

        when(resultSet.getObject("id", UUID.class)).thenReturn(targetIdentityId);
        when(resultSet.getString("firebase_uid")).thenReturn("firebase-target");
        when(resultSet.getString("phone_number")).thenReturn("+919999991234");
        when(resultSet.getString("status")).thenReturn("SUSPENDED");
        when(resultSet.getLong("token_version")).thenReturn(7L);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenAnswer(invocation -> {
                RowMapper mapper = invocation.getArgument(1);
                return List.of(mapper.mapRow(resultSet, 0));
            });
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(1);

        AdminAccountInterventionRepository repository = new AdminAccountInterventionRepository(jdbcTemplate);
        AdminAccountInterventionRepository.InterventionResponse response = repository.request(
            actorIdentityId,
            targetIdentityId,
            "SUSPEND",
            "Repeated suspension requested after a support escalation",
            correlationId
        );

        assertThat(response.changed()).isFalse();
        assertThat(response.interventionId()).isNotNull();
        assertThat(response.providerStatus()).isEqualTo("PENDING");
        assertThat(response.tokenVersion()).isEqualTo(7L);
        assertThat(response.correlationId()).isEqualTo(correlationId);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(3)).update(sqlCaptor.capture(), any(Object[].class));
        assertThat(sqlCaptor.getAllValues()).anyMatch(sql -> sql.contains("provider_status = 'SUPERSEDED'"));
        assertThat(sqlCaptor.getAllValues()).anyMatch(sql -> sql.contains("INSERT INTO auth_admin_intervention"));
        assertThat(sqlCaptor.getAllValues()).anyMatch(sql -> sql.contains("INSERT INTO auth_audit"));
        assertThat(sqlCaptor.getAllValues()).noneMatch(sql -> sql.contains("UPDATE auth_identity"));
        assertThat(sqlCaptor.getAllValues()).noneMatch(sql -> sql.contains("UPDATE refresh_session"));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Test
    void providerClaimLocksIdentityAndSelectsOnlyLatestDueIntervention() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenReturn(0);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(List.of());

        AdminAccountInterventionRepository repository = new AdminAccountInterventionRepository(jdbcTemplate);
        assertThat(repository.claimProviderWork(20, 8, 5)).isEmpty();

        ArgumentCaptor<String> queryCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).query(queryCaptor.capture(), any(RowMapper.class), any(Object[].class));
        assertThat(queryCaptor.getValue()).contains("FOR UPDATE OF identity SKIP LOCKED");
        assertThat(queryCaptor.getValue()).contains("DISTINCT ON (intervention.identity_id)");
        assertThat(queryCaptor.getValue()).contains("ORDER BY intervention.identity_id, intervention.created_at DESC");
    }

    @Test
    void auditActionNamesAreCanonical() {
        assertThat(AdminAccountInterventionRepository.auditAction("SUSPEND")).isEqualTo("ACCOUNT_SUSPENDED");
        assertThat(AdminAccountInterventionRepository.auditAction("REACTIVATE")).isEqualTo("ACCOUNT_REACTIVATED");
    }
}
