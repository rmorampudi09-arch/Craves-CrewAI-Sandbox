package in.craves.integration.refund;

import in.craves.integration.refund.RefundModels.ProviderRefundResult;
import in.craves.integration.refund.RefundModels.RefundWorkItem;
import in.craves.integration.refund.RefundStatusEventFactory.SerializedRefundStatusEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class RefundRepository {
    private final JdbcTemplate jdbcTemplate;
    private final RefundStatusEventFactory statusEventFactory;

    public RefundRepository(JdbcTemplate jdbcTemplate, RefundStatusEventFactory statusEventFactory) {
        this.jdbcTemplate = jdbcTemplate;
        this.statusEventFactory = statusEventFactory;
    }

    @Transactional
    public List<RefundWorkItem> claimBatch(
        boolean createEnabled,
        boolean reconciliationEnabled,
        int batchSize,
        int maxAttempts,
        int staleLockSeconds,
        UUID lockToken
    ) {
        return jdbcTemplate.query(
            """
                WITH candidates AS (
                    SELECT id
                    FROM payment_schema.refund
                    WHERE attempt_count < ?
                      AND checkout_id IS NOT NULL
                      AND chef_sub_order_id IS NOT NULL
                      AND customer_identity_id IS NOT NULL
                      AND request_event_id IS NOT NULL
                      AND idempotency_key IS NOT NULL
                      AND provider IN ('CASHFREE', 'RAZORPAY')
                      AND provider_order_id IS NOT NULL
                      AND (provider <> 'RAZORPAY' OR provider_payment_id IS NOT NULL)
                      AND (
                          (? AND status IN ('REQUESTED', 'RETRY') AND next_attempt_at <= now())
                          OR (? AND status IN ('PENDING', 'ONHOLD') AND next_attempt_at <= now())
                          OR (
                              status = 'PROCESSING'
                              AND locked_at < now() - (? * INTERVAL '1 second')
                              AND (
                                  (? AND provider_refund_id IS NULL)
                                  OR (? AND provider_refund_id IS NOT NULL)
                              )
                          )
                      )
                    ORDER BY next_attempt_at ASC, created_at ASC
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE payment_schema.refund refund
                SET status = 'PROCESSING',
                    attempt_count = refund.attempt_count + 1,
                    lock_token = ?,
                    locked_at = now(),
                    updated_at = now()
                FROM candidates
                WHERE refund.id = candidates.id
                RETURNING refund.*
                """,
            this::mapWorkItem,
            maxAttempts,
            createEnabled,
            reconciliationEnabled,
            staleLockSeconds,
            createEnabled,
            reconciliationEnabled,
            batchSize,
            lockToken
        );
    }

    @Transactional
    public boolean applyProviderResult(
        RefundWorkItem workItem,
        ProviderRefundResult providerResult,
        String databaseStatus,
        String normalizedStatus,
        Instant nextAttemptAt,
        Instant occurredAt
    ) {
        int updated = jdbcTemplate.update(
            """
                UPDATE payment_schema.refund
                SET status = ?,
                    provider_status = ?,
                    cf_refund_id = COALESCE(?, cf_refund_id),
                    provider_refund_id = COALESCE(?, provider_refund_id),
                    provider_payload = CAST(? AS jsonb),
                    next_attempt_at = ?,
                    processed_at = CASE WHEN ? IN ('SUCCESS', 'FAILED', 'CANCELLED') THEN ? ELSE processed_at END,
                    lock_token = NULL,
                    locked_at = NULL,
                    last_error = NULL,
                    updated_at = now()
                WHERE id = ?
                  AND status = 'PROCESSING'
                  AND lock_token = ?
                """,
            databaseStatus,
            providerResult.providerStatus(),
            providerResult.cfRefundId(),
            providerResult.cfRefundId(),
            providerResult.providerPayload(),
            Timestamp.from(nextAttemptAt),
            databaseStatus,
            Timestamp.from(occurredAt),
            workItem.refundId(),
            workItem.lockToken()
        );
        if (updated != 1) {
            return false;
        }

        insertStatusOutbox(statusEventFactory.create(
            workItem,
            normalizedStatus,
            providerResult,
            occurredAt
        ));
        return true;
    }

    @Transactional
    public boolean markFailure(
        RefundWorkItem workItem,
        int maxAttempts,
        Instant nextAttemptAt,
        String error,
        boolean terminal,
        Instant occurredAt
    ) {
        String status = terminal || workItem.attemptCount() >= maxAttempts
            ? "DEAD_LETTER"
            : "RETRY";
        int updated = jdbcTemplate.update(
            """
                UPDATE payment_schema.refund
                SET status = ?,
                    next_attempt_at = ?,
                    processed_at = CASE WHEN ? = 'DEAD_LETTER' THEN ? ELSE processed_at END,
                    lock_token = NULL,
                    locked_at = NULL,
                    last_error = ?,
                    updated_at = now()
                WHERE id = ?
                  AND status = 'PROCESSING'
                  AND lock_token = ?
                """,
            status,
            Timestamp.from(nextAttemptAt),
            status,
            Timestamp.from(occurredAt),
            safeError(error),
            workItem.refundId(),
            workItem.lockToken()
        );
        if (updated != 1) {
            return false;
        }
        if ("DEAD_LETTER".equals(status)) {
            ProviderRefundResult result = new ProviderRefundResult(
                "FAILED",
                workItem.cfRefundId(),
                "{}"
            );
            insertStatusOutbox(statusEventFactory.create(
                workItem,
                "REFUND_FAILED",
                result,
                occurredAt
            ));
        }
        return true;
    }

    private void insertStatusOutbox(SerializedRefundStatusEvent event) {
        jdbcTemplate.update(
            """
                INSERT INTO payment_schema.refund_status_outbox (
                    id, event_key, aggregate_id, event_type, event_version,
                    correlation_id, causation_id, subject, payload,
                    status, attempt_count, next_attempt_at, created_at, updated_at
                ) VALUES (
                    ?, ?, ?, ?, ?,
                    ?, ?, ?, CAST(? AS jsonb),
                    'PENDING', 0, now(), now(), now()
                )
                ON CONFLICT (event_key) DO NOTHING
                """,
            event.eventId(),
            event.eventKey(),
            event.subject(),
            event.eventType(),
            event.eventVersion(),
            event.correlationId(),
            event.causationId(),
            event.subject(),
            event.payloadJson()
        );
    }

    @Transactional
    public List<RefundStatusOutboxRecord> claimStatusOutbox(
        int batchSize,
        int maxAttempts,
        int staleLockSeconds,
        UUID lockToken
    ) {
        return jdbcTemplate.query(
            """
                WITH candidates AS (
                    SELECT id
                    FROM payment_schema.refund_status_outbox
                    WHERE attempt_count < ?
                      AND (
                          (status IN ('PENDING', 'FAILED') AND next_attempt_at <= now())
                          OR (status = 'PROCESSING' AND locked_at < now() - (? * INTERVAL '1 second'))
                      )
                    ORDER BY created_at ASC
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE payment_schema.refund_status_outbox outbox
                SET status = 'PROCESSING',
                    attempt_count = outbox.attempt_count + 1,
                    lock_token = ?,
                    locked_at = now(),
                    updated_at = now()
                FROM candidates
                WHERE outbox.id = candidates.id
                RETURNING outbox.*
                """,
            this::mapOutbox,
            maxAttempts,
            staleLockSeconds,
            batchSize,
            lockToken
        );
    }

    public boolean markStatusPublished(UUID id, UUID lockToken, String brokerMessageId) {
        return jdbcTemplate.update(
            """
                UPDATE payment_schema.refund_status_outbox
                SET status = 'PUBLISHED', broker_message_id = ?, published_at = now(),
                    lock_token = NULL, locked_at = NULL, last_error = NULL, updated_at = now()
                WHERE id = ? AND status = 'PROCESSING' AND lock_token = ?
                """,
            brokerMessageId,
            id,
            lockToken
        ) == 1;
    }

    public boolean markStatusPublishFailed(
        RefundStatusOutboxRecord record,
        UUID lockToken,
        int maxAttempts,
        Instant nextAttemptAt,
        String error
    ) {
        String status = record.attemptCount() >= maxAttempts ? "DEAD_LETTER" : "FAILED";
        return jdbcTemplate.update(
            """
                UPDATE payment_schema.refund_status_outbox
                SET status = ?, next_attempt_at = ?, lock_token = NULL,
                    locked_at = NULL, last_error = ?, updated_at = now()
                WHERE id = ? AND status = 'PROCESSING' AND lock_token = ?
                """,
            status,
            Timestamp.from(nextAttemptAt),
            safeError(error),
            record.id(),
            lockToken
        ) == 1;
    }

    private RefundWorkItem mapWorkItem(ResultSet resultSet, int rowNumber) throws SQLException {
        return new RefundWorkItem(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("payment_order_id", UUID.class),
            resultSet.getObject("checkout_id", UUID.class),
            resultSet.getObject("chef_sub_order_id", UUID.class),
            resultSet.getObject("customer_identity_id", UUID.class),
            resultSet.getObject("request_event_id", UUID.class),
            resultSet.getString("cashfree_order_id"),
            resultSet.getString("refund_ref"),
            resultSet.getObject("idempotency_key", UUID.class),
            resultSet.getBigDecimal("amount"),
            resultSet.getString("currency"),
            resultSet.getString("reason"),
            resultSet.getString("status"),
            resultSet.getString("provider_status"),
            resultSet.getString("cf_refund_id"),
            resultSet.getInt("attempt_count"),
            resultSet.getObject("lock_token", UUID.class),
            resultSet.getString("provider"),
            resultSet.getString("provider_order_id"),
            resultSet.getString("provider_payment_id"),
            resultSet.getString("provider_refund_id")
        );
    }

    private RefundStatusOutboxRecord mapOutbox(ResultSet resultSet, int rowNumber) throws SQLException {
        return new RefundStatusOutboxRecord(
            resultSet.getObject("id", UUID.class),
            resultSet.getString("event_type"),
            resultSet.getString("event_version"),
            resultSet.getObject("correlation_id", UUID.class),
            resultSet.getObject("subject", UUID.class),
            resultSet.getString("payload"),
            resultSet.getInt("attempt_count")
        );
    }

    private static String safeError(String error) {
        if (error == null || error.isBlank()) {
            return "Unknown refund failure";
        }
        String normalized = error.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() > 2000 ? normalized.substring(0, 2000) : normalized;
    }

    public record RefundStatusOutboxRecord(
        UUID id,
        String eventType,
        String eventVersion,
        UUID correlationId,
        UUID subject,
        String payloadJson,
        int attemptCount
    ) {
    }
}
