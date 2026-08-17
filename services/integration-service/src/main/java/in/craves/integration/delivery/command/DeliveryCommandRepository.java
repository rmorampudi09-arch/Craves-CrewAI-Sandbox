package in.craves.integration.delivery.command;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.integration.delivery.command.DeliveryCommandModels.DeliveryCommandMessage;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class DeliveryCommandRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public DeliveryCommandRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CommandRecord createOrFind(DeliveryCommandMessage message) {
        jdbc.update("""
            INSERT INTO delivery_schema.delivery_command
                (id, chef_sub_order_id, order_id, command_type, status, ready_at, dispatch_at,
                 idempotency_key, payload, source_event_id, created_at, updated_at)
            VALUES (?, ?, ?, 'QUOTE_AND_CREATE', 'SCHEDULED', ?, ?, ?, ?::jsonb, ?, now(), now())
            ON CONFLICT (chef_sub_order_id) DO NOTHING
            """,
            message.commandId(),
            message.chefSubOrderId(),
            message.orderId(),
            toDatabaseTimestamp(message.readyAt()),
            toDatabaseTimestamp(message.dispatchAt()),
            message.idempotencyKey(),
            writeJson(message),
            message.sourceEventId()
        );
        return findByChefSubOrderId(message.chefSubOrderId())
            .orElseThrow(() -> new IllegalStateException("Delivery command was not persisted"));
    }

    public Optional<CommandRecord> findByChefSubOrderId(UUID chefSubOrderId) {
        return queryOne("""
            SELECT id, chef_sub_order_id, order_id, status, attempt_count, payload,
                   scheduled_sequence_number, service_bus_message_id,
                   reconciliation_provider_id, reconciliation_client_reference,
                   reconciliation_started_at, reconciliation_attempt_count,
                   provider_wait_attempt_count, provider_wait_started_at, next_provider_retry_at
            FROM delivery_schema.delivery_command
            WHERE chef_sub_order_id = ?
            """, chefSubOrderId);
    }

    public Optional<CommandRecord> findById(UUID commandId) {
        return queryOne("""
            SELECT id, chef_sub_order_id, order_id, status, attempt_count, payload,
                   scheduled_sequence_number, service_bus_message_id,
                   reconciliation_provider_id, reconciliation_client_reference,
                   reconciliation_started_at, reconciliation_attempt_count,
                   provider_wait_attempt_count, provider_wait_started_at, next_provider_retry_at
            FROM delivery_schema.delivery_command
            WHERE id = ?
            """, commandId);
    }

    public boolean recordScheduled(UUID commandId, long sequenceNumber, String serviceBusMessageId) {
        return jdbc.update("""
            UPDATE delivery_schema.delivery_command
            SET scheduled_sequence_number = ?, service_bus_message_id = ?, updated_at = now()
            WHERE id = ? AND scheduled_sequence_number IS NULL
            """, sequenceNumber, serviceBusMessageId, commandId) == 1;
    }

    @Transactional
    public Optional<CommandRecord> claim(UUID commandId, int maximumAttempts) {
        List<CommandRecord> rows = jdbc.query("""
            UPDATE delivery_schema.delivery_command
            SET status = 'PROCESSING',
                attempt_count = attempt_count
                    + CASE WHEN status = 'WAITING_FOR_PROVIDER' THEN 0 ELSE 1 END,
                processing_started_at = now(),
                updated_at = now()
            WHERE id = ?
              AND (
                    (status IN ('SCHEDULED', 'FAILED') AND attempt_count < ?)
                    OR (
                        status = 'WAITING_FOR_PROVIDER'
                        AND next_provider_retry_at IS NOT NULL
                        AND next_provider_retry_at <= now()
                    )
                    OR (
                        status = 'PROCESSING'
                        AND attempt_count < ?
                        AND processing_started_at < now() - interval '10 minutes'
                    )
                  )
            RETURNING id, chef_sub_order_id, order_id, status, attempt_count, payload,
                      scheduled_sequence_number, service_bus_message_id,
                      reconciliation_provider_id, reconciliation_client_reference,
                      reconciliation_started_at, reconciliation_attempt_count,
                      provider_wait_attempt_count, provider_wait_started_at, next_provider_retry_at
            """, this::mapRow, commandId, maximumAttempts, maximumAttempts);
        return rows.stream().findFirst();
    }

    public boolean markProviderWait(UUID commandId,
                                    Instant nextRetryAt,
                                    String safeError) {
        return jdbc.update("""
            UPDATE delivery_schema.delivery_command
            SET status = 'WAITING_FOR_PROVIDER',
                processing_started_at = NULL,
                provider_wait_attempt_count = provider_wait_attempt_count + 1,
                provider_wait_started_at = COALESCE(provider_wait_started_at, now()),
                next_provider_retry_at = ?,
                last_error = ?,
                updated_at = now()
            WHERE id = ? AND status = 'PROCESSING'
            """,
            toDatabaseTimestamp(nextRetryAt),
            truncate(safeError, 2000),
            commandId
        ) == 1;
    }

    public boolean markReconciliationPending(UUID commandId,
                                             String providerId,
                                             String clientReference,
                                             Instant attemptedAt,
                                             String safeError) {
        return jdbc.update("""
            UPDATE delivery_schema.delivery_command
            SET status = 'RECONCILIATION_PENDING',
                processing_started_at = NULL,
                reconciliation_provider_id = ?,
                reconciliation_client_reference = ?,
                reconciliation_started_at = ?,
                reconciliation_attempt_count = 0,
                reconciliation_processing_started_at = NULL,
                next_reconciliation_at = now(),
                next_provider_retry_at = NULL,
                last_error = ?,
                updated_at = now()
            WHERE id = ? AND status = 'PROCESSING'
            """,
            providerId,
            clientReference,
            toDatabaseTimestamp(attemptedAt),
            truncate(safeError, 2000),
            commandId
        ) == 1;
    }

    @Transactional
    public List<CommandRecord> claimReconciliationBatch(int limit,
                                                        int maximumAttempts,
                                                        int staleMinutes) {
        return jdbc.query("""
            WITH selected AS (
                SELECT id
                FROM delivery_schema.delivery_command
                WHERE status = 'RECONCILIATION_PENDING'
                  AND reconciliation_attempt_count < ?
                  AND next_reconciliation_at <= now()
                  AND (
                        reconciliation_processing_started_at IS NULL
                        OR reconciliation_processing_started_at
                           < now() - make_interval(mins => ?)
                      )
                ORDER BY next_reconciliation_at, created_at
                FOR UPDATE SKIP LOCKED
                LIMIT ?
            )
            UPDATE delivery_schema.delivery_command AS command
            SET reconciliation_attempt_count = reconciliation_attempt_count + 1,
                reconciliation_processing_started_at = now(),
                updated_at = now()
            FROM selected
            WHERE command.id = selected.id
            RETURNING command.id, command.chef_sub_order_id, command.order_id,
                      command.status, command.attempt_count, command.payload,
                      command.scheduled_sequence_number, command.service_bus_message_id,
                      command.reconciliation_provider_id,
                      command.reconciliation_client_reference,
                      command.reconciliation_started_at,
                      command.reconciliation_attempt_count,
                      command.provider_wait_attempt_count,
                      command.provider_wait_started_at,
                      command.next_provider_retry_at
            """, this::mapRow, maximumAttempts, staleMinutes, limit);
    }

    public void scheduleReconciliationRetry(UUID commandId,
                                            int reconciliationAttemptCount,
                                            int maximumAttempts,
                                            Instant nextAttemptAt,
                                            String safeError) {
        jdbc.update("""
            UPDATE delivery_schema.delivery_command
            SET status = CASE
                    WHEN ? >= ? THEN 'DEAD_LETTER'
                    ELSE 'RECONCILIATION_PENDING'
                END,
                reconciliation_processing_started_at = NULL,
                next_reconciliation_at = ?,
                last_error = ?,
                updated_at = now()
            WHERE id = ? AND status = 'RECONCILIATION_PENDING'
            """,
            reconciliationAttemptCount,
            maximumAttempts,
            toDatabaseTimestamp(nextAttemptAt),
            truncate(safeError, 2000),
            commandId
        );
    }

    public void markCompleted(UUID commandId) {
        jdbc.update("""
            UPDATE delivery_schema.delivery_command
            SET status = 'COMPLETED',
                processing_started_at = NULL,
                reconciliation_processing_started_at = NULL,
                next_reconciliation_at = NULL,
                next_provider_retry_at = NULL,
                last_error = NULL,
                updated_at = now()
            WHERE id = ?
            """, commandId);
    }

    public void markFailed(UUID commandId, String safeError) {
        jdbc.update("""
            UPDATE delivery_schema.delivery_command
            SET status = 'FAILED',
                processing_started_at = NULL,
                next_provider_retry_at = NULL,
                last_error = ?,
                updated_at = now()
            WHERE id = ? AND status = 'PROCESSING'
            """, truncate(safeError, 2000), commandId);
    }

    public void markDeadLetter(UUID commandId, String safeError) {
        jdbc.update("""
            UPDATE delivery_schema.delivery_command
            SET status = 'DEAD_LETTER',
                processing_started_at = NULL,
                reconciliation_processing_started_at = NULL,
                next_provider_retry_at = NULL,
                last_error = ?,
                updated_at = now()
            WHERE id = ?
            """, truncate(safeError, 2000), commandId);
    }

    static OffsetDateTime toDatabaseTimestamp(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    private Optional<CommandRecord> queryOne(String sql, Object argument) {
        List<CommandRecord> rows = jdbc.query(sql, this::mapRow, argument);
        return rows.stream().findFirst();
    }

    private CommandRecord mapRow(ResultSet rs, int rowNumber) throws SQLException {
        String payload = rs.getString("payload");
        try {
            return new CommandRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("chef_sub_order_id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getString("status"),
                rs.getInt("attempt_count"),
                objectMapper.readValue(payload, DeliveryCommandMessage.class),
                rs.getObject("scheduled_sequence_number", Long.class),
                rs.getString("service_bus_message_id"),
                rs.getString("reconciliation_provider_id"),
                rs.getString("reconciliation_client_reference"),
                instantOrNull(rs, "reconciliation_started_at"),
                rs.getInt("reconciliation_attempt_count"),
                rs.getInt("provider_wait_attempt_count"),
                instantOrNull(rs, "provider_wait_started_at"),
                instantOrNull(rs, "next_provider_retry_at")
            );
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Stored delivery command payload is invalid", ex);
        }
    }

    private static Instant instantOrNull(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Delivery command payload could not be serialized", ex);
        }
    }

    private static String truncate(String value, int maximumLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maximumLength ? value : value.substring(0, maximumLength);
    }

    public record CommandRecord(
        UUID id,
        UUID chefSubOrderId,
        UUID orderId,
        String status,
        int attemptCount,
        DeliveryCommandMessage message,
        Long scheduledSequenceNumber,
        String serviceBusMessageId,
        String reconciliationProviderId,
        String reconciliationClientReference,
        Instant reconciliationStartedAt,
        int reconciliationAttemptCount,
        int providerWaitAttemptCount,
        Instant providerWaitStartedAt,
        Instant nextProviderRetryAt
    ) {}
}
