package in.craves.notification.recovery;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class NotificationRecoveryRepository {
    private final JdbcTemplate jdbcTemplate;

    public NotificationRecoveryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<BacklogItem> backlog(String requestedStatus, int limit) {
        String status = normalizeStatus(requestedStatus);
        return jdbcTemplate.query(
            """
            SELECT request.id, request.request_key, request.source_service, request.event_type,
                   request.recipient_identity_id, request.channel, request.status,
                   request.attempt_count, request.next_attempt_at, request.last_error,
                   request.created_at, request.updated_at,
                   dead_letter.final_error_code, dead_letter.created_at AS dead_lettered_at
              FROM notification_schema.notification_request request
              LEFT JOIN notification_schema.channel_delivery_dead_letter dead_letter
                ON dead_letter.notification_request_id = request.id
             WHERE request.status = ?
             ORDER BY request.updated_at, request.created_at
             LIMIT ?
            """,
            (rs, rowNum) -> new BacklogItem(
                rs.getObject("id", UUID.class), rs.getString("request_key"),
                rs.getString("source_service"), rs.getString("event_type"),
                rs.getObject("recipient_identity_id", UUID.class), rs.getString("channel"),
                rs.getString("status"), rs.getInt("attempt_count"),
                rs.getObject("next_attempt_at", OffsetDateTime.class),
                safe(rs.getString("last_error")), rs.getString("final_error_code"),
                rs.getObject("dead_lettered_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
            ),
            status,
            limit
        );
    }

    @Transactional
    public RecoveryResponse requeue(
        UUID requestId,
        UUID actorIdentityId,
        String reason,
        UUID correlationId
    ) {
        BacklogItem current = jdbcTemplate.query(
            """
            SELECT id, request_key, source_service, event_type, recipient_identity_id,
                   channel, status, attempt_count, next_attempt_at, last_error,
                   created_at, updated_at, NULL::varchar AS final_error_code,
                   NULL::timestamptz AS dead_lettered_at
              FROM notification_schema.notification_request
             WHERE id = ?
             FOR UPDATE
            """,
            (rs, rowNum) -> new BacklogItem(
                rs.getObject("id", UUID.class), rs.getString("request_key"),
                rs.getString("source_service"), rs.getString("event_type"),
                rs.getObject("recipient_identity_id", UUID.class), rs.getString("channel"),
                rs.getString("status"), rs.getInt("attempt_count"),
                rs.getObject("next_attempt_at", OffsetDateTime.class),
                safe(rs.getString("last_error")), rs.getString("final_error_code"),
                rs.getObject("dead_lettered_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
            ),
            requestId
        ).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification request was not found"));

        if (!"FAILED".equals(current.status()) && !"DEAD_LETTER".equals(current.status())) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Only FAILED or DEAD_LETTER notification requests may be requeued"
            );
        }

        jdbcTemplate.update(
            """
            UPDATE notification_schema.notification_request
               SET status = 'PENDING', attempt_count = 0, next_attempt_at = now(),
                   last_error = NULL, lock_token = NULL, locked_at = NULL,
                   provider_message_id = NULL, sent_at = NULL, updated_at = now()
             WHERE id = ?
            """,
            requestId
        );

        UUID auditId = UUID.randomUUID();
        jdbcTemplate.update(
            """
            INSERT INTO notification_schema.notification_recovery_audit (
                id, request_id, actor_identity_id, previous_status,
                previous_attempt_count, action, reason, correlation_id, created_at
            ) VALUES (?, ?, ?, ?, ?, 'REQUEUE', ?, ?, now())
            """,
            auditId, requestId, actorIdentityId, current.status(), current.attemptCount(), reason, correlationId
        );

        return new RecoveryResponse(
            auditId, requestId, current.status(), "PENDING", current.attemptCount(),
            correlationId, OffsetDateTime.now()
        );
    }

    private static String normalizeStatus(String value) {
        String status = value == null ? "DEAD_LETTER" : value.trim().toUpperCase(Locale.ROOT);
        if (!"FAILED".equals(status) && !"DEAD_LETTER".equals(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status must be FAILED or DEAD_LETTER");
        }
        return status;
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
    }

    public record BacklogItem(
        UUID requestId, String requestKey, String sourceService, String eventType,
        UUID recipientIdentityId, String channel, String status, int attemptCount,
        OffsetDateTime nextAttemptAt, String lastError, String finalErrorCode,
        OffsetDateTime deadLetteredAt, OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {}

    public record RecoveryResponse(
        UUID recoveryAuditId, UUID requestId, String previousStatus, String newStatus,
        int previousAttemptCount, UUID correlationId, OffsetDateTime requeuedAt
    ) {}
}
