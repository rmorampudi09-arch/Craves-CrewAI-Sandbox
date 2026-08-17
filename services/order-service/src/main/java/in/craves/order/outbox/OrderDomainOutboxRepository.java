package in.craves.order.outbox;

import in.craves.order.event.SerializedDomainEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class OrderDomainOutboxRepository {
    private final JdbcTemplate jdbcTemplate;

    public OrderDomainOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean insert(UUID aggregateId, SerializedDomainEvent event) {
        int inserted = jdbcTemplate.update(
            """
                INSERT INTO order_schema.domain_event_outbox (
                    id, event_key, aggregate_type, aggregate_id,
                    event_type, event_version, occurred_at,
                    correlation_id, causation_id, source, subject,
                    payload_json, status, attempts, next_attempt_at,
                    created_at, updated_at
                ) VALUES (
                    ?, ?, 'CUSTOMER_ORDER', ?,
                    ?, ?, ?,
                    ?, ?, ?, ?,
                    CAST(? AS jsonb), 'PENDING', 0, now(),
                    now(), now()
                )
                ON CONFLICT (event_key) DO NOTHING
                """,
            event.eventId(),
            event.eventKey(),
            aggregateId,
            event.eventType(),
            event.eventVersion(),
            Timestamp.from(event.occurredAt()),
            event.correlationId(),
            event.causationId(),
            event.source(),
            event.subject(),
            event.payloadJson()
        );
        return inserted == 1;
    }

    @Transactional
    public List<OrderDomainOutboxRecord> claimBatch(
        int batchSize,
        int maxAttempts,
        int staleLockSeconds,
        UUID lockToken,
        List<String> enabledEventTypes
    ) {
        if (enabledEventTypes == null || enabledEventTypes.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(",", Collections.nCopies(enabledEventTypes.size(), "?"));
        String sql = """
            WITH candidates AS (
                SELECT id
                FROM order_schema.domain_event_outbox
                WHERE attempts < ?
                  AND event_type IN (%s)
                  AND (
                      (status IN ('PENDING', 'FAILED') AND next_attempt_at <= now())
                      OR (status = 'PROCESSING' AND locked_at < now() - (? * INTERVAL '1 second'))
                  )
                ORDER BY occurred_at ASC, created_at ASC
                FOR UPDATE SKIP LOCKED
                LIMIT ?
            )
            UPDATE order_schema.domain_event_outbox outbox
            SET status = 'PROCESSING',
                attempts = outbox.attempts + 1,
                lock_token = ?,
                locked_at = now(),
                updated_at = now()
            FROM candidates
            WHERE outbox.id = candidates.id
            RETURNING outbox.*
            """.formatted(placeholders);

        List<Object> parameters = new ArrayList<>();
        parameters.add(maxAttempts);
        parameters.addAll(enabledEventTypes);
        parameters.add(staleLockSeconds);
        parameters.add(batchSize);
        parameters.add(lockToken);

        return jdbcTemplate.query(sql, this::mapRecord, parameters.toArray());
    }

    public boolean markPublished(UUID id, UUID lockToken, String brokerMessageId) {
        return jdbcTemplate.update(
            """
                UPDATE order_schema.domain_event_outbox
                SET status = 'PUBLISHED',
                    broker_message_id = ?,
                    published_at = now(),
                    lock_token = NULL,
                    locked_at = NULL,
                    last_error = NULL,
                    updated_at = now()
                WHERE id = ?
                  AND status = 'PROCESSING'
                  AND lock_token = ?
                """,
            brokerMessageId,
            id,
            lockToken
        ) == 1;
    }

    public boolean markFailed(
        UUID id,
        UUID lockToken,
        int attempts,
        int maxAttempts,
        Instant nextAttemptAt,
        String error
    ) {
        String nextStatus = attempts >= maxAttempts ? "DEAD" : "FAILED";
        return jdbcTemplate.update(
            """
                UPDATE order_schema.domain_event_outbox
                SET status = ?,
                    next_attempt_at = ?,
                    lock_token = NULL,
                    locked_at = NULL,
                    last_error = ?,
                    updated_at = now()
                WHERE id = ?
                  AND status = 'PROCESSING'
                  AND lock_token = ?
                """,
            nextStatus,
            Timestamp.from(nextAttemptAt),
            safeError(error),
            id,
            lockToken
        ) == 1;
    }

    private OrderDomainOutboxRecord mapRecord(ResultSet resultSet, int rowNumber) throws SQLException {
        return new OrderDomainOutboxRecord(
            resultSet.getObject("id", UUID.class),
            resultSet.getString("event_key"),
            resultSet.getObject("aggregate_id", UUID.class),
            resultSet.getString("event_type"),
            resultSet.getString("event_version"),
            instant(resultSet, "occurred_at"),
            resultSet.getObject("correlation_id", UUID.class),
            resultSet.getObject("causation_id", UUID.class),
            resultSet.getString("source"),
            resultSet.getString("subject"),
            resultSet.getString("payload_json"),
            resultSet.getInt("attempts"),
            resultSet.getObject("lock_token", UUID.class)
        );
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String safeError(String error) {
        if (error == null || error.isBlank()) {
            return "Unknown publisher failure";
        }
        String normalized = error.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() > 2000 ? normalized.substring(0, 2000) : normalized;
    }
}
