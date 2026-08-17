package in.craves.integration.delivery.command;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class DeliveryOutboxRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public DeliveryOutboxRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public UUID enqueue(String eventType, UUID aggregateId, UUID correlationId, JsonNode payload) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO delivery_schema.delivery_outbox
                (id, event_type, aggregate_type, aggregate_id, correlation_id, payload,
                 status, attempt_count, next_attempt_at, created_at, updated_at)
            VALUES (?, ?, 'DELIVERY_JOB', ?, ?, ?::jsonb, 'PENDING', 0, now(), now(), now())
            """, id, eventType, aggregateId, correlationId, payload.toString());
        return id;
    }

    @Transactional
    public List<OutboxRecord> claimBatch(int limit) {
        return jdbc.query("""
            WITH selected AS (
                SELECT id
                FROM delivery_schema.delivery_outbox
                WHERE (
                        status IN ('PENDING', 'FAILED')
                        AND next_attempt_at <= now()
                      )
                   OR (
                        status = 'PROCESSING'
                        AND processing_started_at < now() - interval '10 minutes'
                      )
                ORDER BY created_at
                FOR UPDATE SKIP LOCKED
                LIMIT ?
            )
            UPDATE delivery_schema.delivery_outbox AS outbox
            SET status = 'PROCESSING',
                attempt_count = attempt_count + 1,
                processing_started_at = now(),
                updated_at = now()
            FROM selected
            WHERE outbox.id = selected.id
            RETURNING outbox.id, outbox.event_type, outbox.aggregate_id,
                      outbox.correlation_id, outbox.payload, outbox.attempt_count
            """, this::mapRow, limit);
    }

    public void markPublished(UUID id) {
        jdbc.update("""
            UPDATE delivery_schema.delivery_outbox
            SET status = 'PUBLISHED',
                published_at = now(),
                processing_started_at = NULL,
                last_error = NULL,
                updated_at = now()
            WHERE id = ?
            """, id);
    }

    public void markFailed(UUID id, int attemptCount, String safeError) {
        long delaySeconds = Math.min(300L, 1L << Math.min(attemptCount, 8));
        jdbc.update("""
            UPDATE delivery_schema.delivery_outbox
            SET status = CASE WHEN attempt_count >= 10 THEN 'DEAD_LETTER' ELSE 'FAILED' END,
                processing_started_at = NULL,
                next_attempt_at = ?,
                last_error = ?,
                updated_at = now()
            WHERE id = ?
            """,
            Instant.now().plusSeconds(delaySeconds),
            truncate(safeError, 2000),
            id
        );
    }

    private OutboxRecord mapRow(ResultSet rs, int rowNumber) throws SQLException {
        try {
            return new OutboxRecord(
                rs.getObject("id", UUID.class),
                rs.getString("event_type"),
                rs.getObject("aggregate_id", UUID.class),
                rs.getObject("correlation_id", UUID.class),
                objectMapper.readTree(rs.getString("payload")),
                rs.getInt("attempt_count")
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored delivery outbox payload is invalid", ex);
        }
    }

    private static String truncate(String value, int maximumLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }

    public record OutboxRecord(
        UUID id,
        String eventType,
        UUID aggregateId,
        UUID correlationId,
        JsonNode payload,
        int attemptCount
    ) {}
}
