package in.craves.subscription.order;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class OccurrenceOrderRepository {
    private final JdbcTemplate jdbcTemplate;

    public OccurrenceOrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public List<OccurrenceClaim> claimReady(int leadHours, int staleLockMinutes, int batchSize) {
        UUID lockToken = UUID.randomUUID();
        String sql = """
            WITH candidates AS (
                SELECT id
                  FROM subscription_schema.subscription_occurrence
                 WHERE status = 'READY_FOR_ORDER'
                   AND service_at <= now() + (? * INTERVAL '1 hour')
                   AND (order_dispatch_lock_token IS NULL OR order_dispatch_locked_at < now() - (? * INTERVAL '1 minute'))
                 ORDER BY service_at, created_at
                 FOR UPDATE SKIP LOCKED
                 LIMIT ?
            )
            UPDATE subscription_schema.subscription_occurrence occurrence
               SET order_dispatch_lock_token = ?, order_dispatch_locked_at = now(), updated_at = now()
              FROM candidates candidate
             WHERE occurrence.id = candidate.id
            RETURNING occurrence.id, occurrence.subscription_id, occurrence.plan_id,
                      occurrence.customer_identity_id, occurrence.chef_identity_id,
                      occurrence.delivery_address_id, occurrence.service_at
            """;
        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new OccurrenceClaim(
                rs.getObject("id", UUID.class),
                rs.getObject("subscription_id", UUID.class),
                rs.getObject("plan_id", UUID.class),
                rs.getObject("customer_identity_id", UUID.class),
                rs.getObject("chef_identity_id", UUID.class),
                rs.getObject("delivery_address_id", UUID.class),
                rs.getTimestamp("service_at").toInstant(),
                findItems(rs.getObject("id", UUID.class)),
                lockToken
            ),
            leadHours, staleLockMinutes, batchSize, lockToken
        );
    }

    @Transactional
    public boolean createRequest(OccurrenceClaim claim, UUID outboxId, JsonNode payload) {
        int updated = jdbcTemplate.update(
            "UPDATE subscription_schema.subscription_occurrence SET status = 'ORDER_REQUESTED', order_requested_at = now(), " +
                "order_dispatch_lock_token = NULL, order_dispatch_locked_at = NULL, updated_at = now() " +
                "WHERE id = ? AND status = 'READY_FOR_ORDER' AND order_dispatch_lock_token = ?",
            claim.occurrenceId(), claim.lockToken()
        );
        if (updated != 1) {
            return false;
        }
        jdbcTemplate.update(
            "INSERT INTO subscription_schema.subscription_occurrence_history " +
                "(id, occurrence_id, old_status, new_status, reason, created_at) " +
                "VALUES (?, ?, 'READY_FOR_ORDER', 'ORDER_REQUESTED', 'Order request queued', now())",
            UUID.randomUUID(), claim.occurrenceId()
        );
        jdbcTemplate.update(
            "INSERT INTO subscription_schema.subscription_order_request_outbox " +
                "(id, event_key, aggregate_id, event_type, event_version, correlation_id, causation_id, subject, payload, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 'v1', ?, ?, ?, CAST(? AS jsonb), 'PENDING', now(), now())",
            outboxId,
            OccurrenceOrderModels.EVENT_TYPE + ":" + claim.occurrenceId(),
            claim.occurrenceId(),
            OccurrenceOrderModels.EVENT_TYPE,
            claim.occurrenceId(),
            claim.subscriptionId(),
            claim.occurrenceId(),
            payload.toString()
        );
        return true;
    }

    public void releaseClaim(OccurrenceClaim claim) {
        jdbcTemplate.update(
            "UPDATE subscription_schema.subscription_occurrence SET order_dispatch_lock_token = NULL, order_dispatch_locked_at = NULL, updated_at = now() " +
                "WHERE id = ? AND order_dispatch_lock_token = ?",
            claim.occurrenceId(), claim.lockToken()
        );
    }

    @Transactional
    public List<OutboxRecord> claimOutbox(int batchSize, int maxAttempts, int staleLockMinutes) {
        UUID lockToken = UUID.randomUUID();
        String sql = """
            WITH candidates AS (
                SELECT id
                  FROM subscription_schema.subscription_order_request_outbox
                 WHERE (status IN ('PENDING', 'FAILED') AND next_attempt_at <= now() AND attempt_count < ?)
                    OR (status = 'PROCESSING' AND locked_at < now() - (? * INTERVAL '1 minute'))
                 ORDER BY created_at
                 FOR UPDATE SKIP LOCKED
                 LIMIT ?
            )
            UPDATE subscription_schema.subscription_order_request_outbox outbox
               SET status = 'PROCESSING', lock_token = ?, locked_at = now(),
                   attempt_count = attempt_count + 1, last_error = NULL, updated_at = now()
              FROM candidates candidate
             WHERE outbox.id = candidate.id
            RETURNING outbox.id, outbox.event_type, outbox.correlation_id,
                      outbox.payload::text, outbox.attempt_count
            """;
        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new OutboxRecord(
                rs.getObject("id", UUID.class),
                rs.getString("event_type"),
                rs.getObject("correlation_id", UUID.class),
                rs.getString("payload"),
                rs.getInt("attempt_count"),
                lockToken
            ),
            maxAttempts, staleLockMinutes, batchSize, lockToken
        );
    }

    public void markPublished(OutboxRecord record) {
        jdbcTemplate.update(
            "UPDATE subscription_schema.subscription_order_request_outbox SET status = 'PUBLISHED', published_at = now(), " +
                "broker_message_id = ?, lock_token = NULL, locked_at = NULL, updated_at = now() " +
                "WHERE id = ? AND lock_token = ?",
            record.id().toString(), record.id(), record.lockToken()
        );
    }

    public void markPublishFailure(OutboxRecord record, int maxAttempts, Throwable error) {
        boolean dead = record.attemptCount() >= maxAttempts;
        long delay = Math.min(3600L, 5L * (1L << Math.min(10, Math.max(0, record.attemptCount() - 1))));
        jdbcTemplate.update(
            "UPDATE subscription_schema.subscription_order_request_outbox SET status = ?, " +
                "next_attempt_at = now() + (? * INTERVAL '1 second'), last_error = ?, " +
                "lock_token = NULL, locked_at = NULL, updated_at = now() WHERE id = ? AND lock_token = ?",
            dead ? "DEAD_LETTER" : "FAILED",
            dead ? 0L : delay,
            safe(error),
            record.id(),
            record.lockToken()
        );
    }

    @Transactional
    public boolean markOrderCreated(UUID occurrenceId, UUID orderId) {
        List<OccurrenceState> rows = jdbcTemplate.query(
            "SELECT status, order_id FROM subscription_schema.subscription_occurrence WHERE id = ? FOR UPDATE",
            (rs, rowNum) -> new OccurrenceState(rs.getString("status"), rs.getObject("order_id", UUID.class)),
            occurrenceId
        );
        if (rows.isEmpty()) {
            throw new IllegalArgumentException("Subscription occurrence was not found");
        }
        OccurrenceState current = rows.getFirst();
        if (orderId.equals(current.orderId()) && "ORDER_CREATED".equals(current.status())) {
            return false;
        }
        if (current.orderId() != null && !orderId.equals(current.orderId())) {
            throw new IllegalStateException("Subscription occurrence is already linked to a different order");
        }
        if (!"ORDER_REQUESTED".equals(current.status()) && !"ORDER_CREATED".equals(current.status())) {
            throw new IllegalStateException("Subscription occurrence is not waiting for an order callback");
        }
        jdbcTemplate.update(
            "UPDATE subscription_schema.subscription_occurrence SET status = 'ORDER_CREATED', order_id = ?, " +
                "order_created_at = now(), updated_at = now() WHERE id = ?",
            orderId, occurrenceId
        );
        jdbcTemplate.update(
            "INSERT INTO subscription_schema.subscription_occurrence_history " +
                "(id, occurrence_id, old_status, new_status, reason, created_at) " +
                "VALUES (?, ?, ?, 'ORDER_CREATED', 'Order Service confirmed order creation', now())",
            UUID.randomUUID(), occurrenceId, current.status()
        );
        return true;
    }

    private List<OccurrenceItem> findItems(UUID occurrenceId) {
        return jdbcTemplate.query(
            "SELECT menu_item_id, quantity, sequence_number FROM subscription_schema.subscription_occurrence_item " +
                "WHERE occurrence_id = ? ORDER BY sequence_number",
            (rs, rowNum) -> new OccurrenceItem(
                rs.getObject("menu_item_id", UUID.class),
                rs.getInt("quantity"),
                rs.getInt("sequence_number")
            ),
            occurrenceId
        );
    }

    private static String safe(Throwable error) {
        String value = error == null || error.getMessage() == null
            ? (error == null ? "Unknown order dispatch error" : error.getClass().getSimpleName())
            : error.getMessage();
        value = value.replace('\n', ' ').replace('\r', ' ').trim();
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }

    public record OccurrenceClaim(
        UUID occurrenceId,
        UUID subscriptionId,
        UUID planId,
        UUID customerIdentityId,
        UUID chefIdentityId,
        UUID deliveryAddressId,
        Instant scheduledServiceAt,
        List<OccurrenceItem> items,
        UUID lockToken
    ) {
    }

    public record OccurrenceItem(UUID menuItemId, int quantity, int sequenceNumber) {
    }

    public record OutboxRecord(
        UUID id,
        String eventType,
        UUID correlationId,
        String payload,
        int attemptCount,
        UUID lockToken
    ) {
    }

    private record OccurrenceState(String status, UUID orderId) {
    }
}
