package in.craves.auth.admin;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class AdminAccountInterventionRepository {
    private final JdbcTemplate jdbcTemplate;

    public AdminAccountInterventionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public InterventionResponse request(
        UUID actorIdentityId,
        UUID targetIdentityId,
        String action,
        String reason,
        UUID correlationId
    ) {
        IdentityRow identity = jdbcTemplate.query(
            "SELECT id, firebase_uid, phone_number, status, token_version FROM auth_identity WHERE id = ? FOR UPDATE",
            (rs, rowNum) -> new IdentityRow(
                rs.getObject("id", UUID.class),
                rs.getString("firebase_uid"),
                rs.getString("phone_number"),
                rs.getString("status"),
                rs.getLong("token_version")
            ),
            targetIdentityId
        ).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("Identity was not found"));

        String targetStatus = switch (action) {
            case "SUSPEND" -> "SUSPENDED";
            case "REACTIVATE" -> "ACTIVE";
            default -> throw new IllegalArgumentException("Unsupported account intervention action");
        };
        if ("SUSPEND".equals(action) && actorIdentityId.equals(targetIdentityId)) {
            throw new IllegalStateException("An administrator cannot suspend their own account");
        }

        boolean changed = !targetStatus.equals(identity.status());
        IdentityRow resultingIdentity = identity;
        if (changed) {
            long newTokenVersion = identity.tokenVersion() + 1L;
            int identityUpdated = jdbcTemplate.update(
                "UPDATE auth_identity SET status = ?, token_version = ?, updated_at = now() WHERE id = ? AND token_version = ?",
                targetStatus, newTokenVersion, targetIdentityId, identity.tokenVersion()
            );
            if (identityUpdated != 1) {
                throw new IllegalStateException("Identity state changed during account intervention");
            }

            jdbcTemplate.update(
                "UPDATE refresh_session SET revoked_at = COALESCE(revoked_at, now()), revoke_reason = " +
                    "CASE WHEN revoked_at IS NULL THEN ? ELSE revoke_reason END WHERE identity_id = ? AND revoked_at IS NULL",
                "ADMIN_" + action, targetIdentityId
            );
            resultingIdentity = new IdentityRow(
                identity.id(), identity.firebaseUid(), identity.phoneNumber(), targetStatus, newTokenVersion
            );
        }

        jdbcTemplate.update(
            """
            UPDATE auth_admin_intervention
               SET provider_status = 'SUPERSEDED', provider_lock_token = NULL,
                   provider_locked_at = NULL, provider_next_attempt_at = now(),
                   provider_last_error = 'Superseded by a newer administrator intervention',
                   updated_at = now()
             WHERE identity_id = ?
               AND provider_status IN ('PENDING', 'FAILED')
            """,
            targetIdentityId
        );

        UUID interventionId = UUID.randomUUID();
        jdbcTemplate.update(
            """
            INSERT INTO auth_admin_intervention (
                id, identity_id, action, requested_status, previous_status,
                actor_identity_id, reason, correlation_id, provider_status,
                provider_next_attempt_at, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'PENDING', now(), now(), now())
            """,
            interventionId, targetIdentityId, action, targetStatus, identity.status(),
            actorIdentityId, reason, correlationId
        );

        jdbcTemplate.update(
            """
            INSERT INTO auth_audit (
                id, identity_id, action, actor_identity_id, details, correlation_id, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, now())
            """,
            UUID.randomUUID(), targetIdentityId, auditAction(action), actorIdentityId,
            reason, correlationId.toString()
        );

        return response(interventionId, resultingIdentity, action, targetStatus, "PENDING", changed, correlationId);
    }

    public InterventionResponse find(UUID targetIdentityId) {
        IdentityRow identity = jdbcTemplate.query(
            "SELECT id, firebase_uid, phone_number, status, token_version FROM auth_identity WHERE id = ?",
            (rs, rowNum) -> new IdentityRow(
                rs.getObject("id", UUID.class), rs.getString("firebase_uid"),
                rs.getString("phone_number"), rs.getString("status"), rs.getLong("token_version")
            ),
            targetIdentityId
        ).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("Identity was not found"));

        InterventionRow latest = jdbcTemplate.query(
            """
            SELECT id, action, requested_status, provider_status, provider_attempt_count,
                   provider_last_error, created_at, provider_completed_at, correlation_id
              FROM auth_admin_intervention
             WHERE identity_id = ?
             ORDER BY created_at DESC, id DESC
             LIMIT 1
            """,
            (rs, rowNum) -> new InterventionRow(
                rs.getObject("id", UUID.class), rs.getString("action"), rs.getString("requested_status"),
                rs.getString("provider_status"), rs.getInt("provider_attempt_count"),
                safe(rs.getString("provider_last_error")), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("provider_completed_at", OffsetDateTime.class),
                rs.getObject("correlation_id", UUID.class)
            ),
            targetIdentityId
        ).stream().findFirst().orElse(null);

        return new InterventionResponse(
            latest == null ? null : latest.id(), identity.id(), maskPhone(identity.phoneNumber()),
            identity.status(), identity.tokenVersion(), latest == null ? null : latest.action(),
            latest == null ? null : latest.requestedStatus(),
            latest == null ? "NOT_REQUIRED" : latest.providerStatus(),
            latest == null ? 0 : latest.providerAttemptCount(),
            latest == null ? null : latest.providerLastError(),
            latest == null ? null : latest.createdAt(),
            latest == null ? null : latest.providerCompletedAt(),
            latest == null ? null : latest.correlationId(), false
        );
    }

    public void auditStatusRead(UUID actorIdentityId, UUID targetIdentityId, String reason, UUID correlationId) {
        jdbcTemplate.update(
            """
            INSERT INTO auth_audit (
                id, identity_id, action, actor_identity_id, details, correlation_id, created_at
            ) VALUES (?, ?, 'ACCOUNT_INTERVENTION_STATUS_READ', ?, ?, ?, now())
            """,
            UUID.randomUUID(), targetIdentityId, actorIdentityId, reason, correlationId.toString()
        );
    }

    @Transactional
    public List<ProviderWorkItem> claimProviderWork(int batchSize, int maxAttempts, int staleLockMinutes) {
        jdbcTemplate.update(
            """
            UPDATE auth_admin_intervention
               SET provider_status = 'FAILED', provider_lock_token = NULL,
                   provider_locked_at = NULL, provider_next_attempt_at = now(),
                   provider_last_error = COALESCE(provider_last_error, 'Recovered stale provider lease'),
                   updated_at = now()
             WHERE provider_status = 'PROCESSING'
               AND provider_locked_at < now() - (? * INTERVAL '1 minute')
            """,
            staleLockMinutes
        );

        UUID lockToken = UUID.randomUUID();
        String sql = """
            WITH due_identities AS (
                SELECT identity.id
                  FROM auth_identity identity
                 WHERE NOT EXISTS (
                           SELECT 1
                             FROM auth_admin_intervention active
                            WHERE active.identity_id = identity.id
                              AND active.provider_status = 'PROCESSING'
                       )
                   AND EXISTS (
                           SELECT 1
                             FROM auth_admin_intervention due
                            WHERE due.identity_id = identity.id
                              AND due.provider_attempt_count < ?
                              AND due.provider_status IN ('PENDING', 'FAILED')
                              AND due.provider_next_attempt_at <= now()
                       )
                 ORDER BY identity.id
                 FOR UPDATE OF identity SKIP LOCKED
                 LIMIT ?
            ),
            candidates AS (
                SELECT DISTINCT ON (intervention.identity_id) intervention.id
                  FROM auth_admin_intervention intervention
                  JOIN due_identities due_identity ON due_identity.id = intervention.identity_id
                 WHERE intervention.provider_attempt_count < ?
                   AND intervention.provider_status IN ('PENDING', 'FAILED')
                   AND intervention.provider_next_attempt_at <= now()
                 ORDER BY intervention.identity_id, intervention.created_at DESC, intervention.id DESC
            )
            UPDATE auth_admin_intervention intervention
               SET provider_status = 'PROCESSING', provider_lock_token = ?, provider_locked_at = now(),
                   provider_attempt_count = provider_attempt_count + 1,
                   provider_last_error = NULL, updated_at = now()
              FROM candidates candidate
             WHERE intervention.id = candidate.id
            RETURNING intervention.id, intervention.identity_id, intervention.action,
                      intervention.provider_attempt_count, intervention.provider_lock_token,
                      (SELECT firebase_uid FROM auth_identity WHERE id = intervention.identity_id) AS firebase_uid
            """;
        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new ProviderWorkItem(
                rs.getObject("id", UUID.class), rs.getObject("identity_id", UUID.class),
                rs.getString("firebase_uid"), rs.getString("action"),
                rs.getInt("provider_attempt_count"), rs.getObject("provider_lock_token", UUID.class)
            ),
            maxAttempts, batchSize, maxAttempts, lockToken
        );
    }

    public boolean currentProviderDisabled(UUID identityId) {
        Boolean disabled = jdbcTemplate.queryForObject(
            "SELECT status = 'SUSPENDED' FROM auth_identity WHERE id = ?", Boolean.class, identityId
        );
        if (disabled == null) {
            throw new IllegalStateException("Identity provider state could not be resolved");
        }
        return disabled;
    }

    public void markProviderCompleted(ProviderWorkItem item) {
        int updated = jdbcTemplate.update(
            """
            UPDATE auth_admin_intervention
               SET provider_status = 'COMPLETED', provider_completed_at = now(),
                   provider_lock_token = NULL, provider_locked_at = NULL,
                   provider_last_error = NULL, updated_at = now()
             WHERE id = ? AND provider_lock_token = ? AND provider_status = 'PROCESSING'
            """,
            item.interventionId(), item.lockToken()
        );
        if (updated != 1) {
            throw new IllegalStateException("Provider work lease was lost before completion");
        }
    }

    public void markProviderFailure(ProviderWorkItem item, int maxAttempts, int retryBaseSeconds, Throwable error) {
        boolean dead = item.attemptCount() >= maxAttempts;
        long delay = Math.min(3600L,
            (long) retryBaseSeconds * (1L << Math.min(10, Math.max(0, item.attemptCount() - 1))));
        jdbcTemplate.update(
            """
            UPDATE auth_admin_intervention
               SET provider_status = ?, provider_next_attempt_at = now() + (? * INTERVAL '1 second'),
                   provider_last_error = ?, provider_lock_token = NULL, provider_locked_at = NULL,
                   updated_at = now()
             WHERE id = ? AND provider_lock_token = ? AND provider_status = 'PROCESSING'
            """,
            dead ? "DEAD_LETTER" : "FAILED", dead ? 0L : delay,
            safe(error == null ? null : error.getMessage()), item.interventionId(), item.lockToken()
        );
    }

    static String auditAction(String action) {
        return switch (action) {
            case "SUSPEND" -> "ACCOUNT_SUSPENDED";
            case "REACTIVATE" -> "ACCOUNT_REACTIVATED";
            default -> throw new IllegalArgumentException("Unsupported account intervention action");
        };
    }

    private static InterventionResponse response(
        UUID interventionId, IdentityRow identity, String action, String targetStatus,
        String providerStatus, boolean changed, UUID correlationId
    ) {
        return new InterventionResponse(
            interventionId, identity.id(), maskPhone(identity.phoneNumber()), identity.status(),
            identity.tokenVersion(), action, targetStatus, providerStatus, 0, null,
            OffsetDateTime.now(), null, correlationId, changed
        );
    }

    private static String maskPhone(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.replaceAll("\\s", "");
        int visible = Math.min(4, normalized.length());
        return "*".repeat(Math.max(0, normalized.length() - visible)) + normalized.substring(normalized.length() - visible);
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() > 1000 ? normalized.substring(0, 1000) : normalized;
    }

    private record IdentityRow(UUID id, String firebaseUid, String phoneNumber, String status, long tokenVersion) {}
    private record InterventionRow(
        UUID id, String action, String requestedStatus, String providerStatus,
        int providerAttemptCount, String providerLastError, OffsetDateTime createdAt,
        OffsetDateTime providerCompletedAt, UUID correlationId
    ) {}
    public record ProviderWorkItem(
        UUID interventionId, UUID identityId, String firebaseUid, String action,
        int attemptCount, UUID lockToken
    ) {}
    public record InterventionResponse(
        UUID interventionId, UUID identityId, String maskedPhoneNumber, String status,
        long tokenVersion, String action, String requestedStatus, String providerStatus,
        int providerAttemptCount, String providerLastError, OffsetDateTime requestedAt,
        OffsetDateTime providerCompletedAt, UUID correlationId, boolean changed
    ) {}
}
