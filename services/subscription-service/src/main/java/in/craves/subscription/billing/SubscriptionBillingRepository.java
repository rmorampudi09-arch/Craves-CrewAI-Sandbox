package in.craves.subscription.billing;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SubscriptionBillingRepository {
    private final JdbcTemplate jdbcTemplate;

    public SubscriptionBillingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public List<BillingClaim> claimDue(int horizonDays, int staleLockMinutes, int batchSize) {
        UUID lockToken = UUID.randomUUID();
        String sql = """
            WITH candidates AS (
                SELECT cs.id
                  FROM subscription_schema.customer_subscription cs
                  JOIN subscription_schema.subscription_plan sp ON sp.id = cs.plan_id
                 WHERE cs.status IN ('PENDING_PAYMENT', 'ACTIVE', 'PAYMENT_FAILED')
                   AND cs.next_billing_date IS NOT NULL
                   AND cs.next_billing_date <= current_date + ?
                   AND sp.status = 'ACTIVE'
                   AND (cs.billing_lock_token IS NULL OR cs.billing_locked_at < now() - (? * INTERVAL '1 minute'))
                 ORDER BY cs.next_billing_date, cs.created_at
                 FOR UPDATE OF cs SKIP LOCKED
                 LIMIT ?
            )
            UPDATE subscription_schema.customer_subscription cs
               SET billing_lock_token = ?, billing_locked_at = now()
              FROM candidates c, subscription_schema.subscription_plan sp
             WHERE cs.id = c.id
               AND sp.id = cs.plan_id
            RETURNING cs.id, cs.customer_identity_id, cs.plan_id, cs.chef_identity_id,
                      cs.next_billing_date, sp.billing_period, sp.amount, sp.currency
            """;
        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new BillingClaim(
                rs.getObject("id", UUID.class),
                rs.getObject("customer_identity_id", UUID.class),
                rs.getObject("plan_id", UUID.class),
                rs.getObject("chef_identity_id", UUID.class),
                rs.getObject("next_billing_date", LocalDate.class),
                rs.getString("billing_period"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                lockToken
            ),
            horizonDays, staleLockMinutes, batchSize, lockToken
        );
    }

    @Transactional
    public boolean createInvoiceAndOutbox(
        BillingClaim claim,
        LocalDate cycleEnd,
        UUID invoiceId,
        UUID outboxId,
        JsonNode payload
    ) {
        int inserted = jdbcTemplate.update(
            "INSERT INTO subscription_schema.subscription_invoice " +
                "(id, subscription_id, plan_id, customer_identity_id, chef_identity_id, cycle_start, cycle_end, amount, currency, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PAYMENT_REQUESTED', now(), now()) " +
                "ON CONFLICT (subscription_id, cycle_start) DO NOTHING",
            invoiceId,
            claim.subscriptionId(),
            claim.planId(),
            claim.customerIdentityId(),
            claim.chefIdentityId(),
            claim.cycleStart(),
            cycleEnd,
            claim.amount(),
            claim.currency()
        );
        if (inserted == 1) {
            jdbcTemplate.update(
                "INSERT INTO subscription_schema.subscription_invoice_history " +
                    "(id, invoice_id, old_status, new_status, reason, created_at) " +
                    "VALUES (?, ?, NULL, 'PAYMENT_REQUESTED', 'Billing cycle generated', now())",
                UUID.randomUUID(), invoiceId
            );
            jdbcTemplate.update(
                "INSERT INTO subscription_schema.subscription_payment_outbox " +
                    "(id, event_key, aggregate_id, event_type, event_version, correlation_id, payload, status, created_at, updated_at) " +
                    "VALUES (?, ?, ?, 'SUBSCRIPTION_PAYMENT_REQUESTED', 'v1', ?, CAST(? AS jsonb), 'PENDING', now(), now())",
                outboxId,
                "SUBSCRIPTION_PAYMENT_REQUESTED:" + invoiceId,
                invoiceId,
                invoiceId,
                payload.toString()
            );
        }
        releaseAndAdvance(claim, cycleEnd);
        return inserted == 1;
    }

    public void releaseAndAdvance(BillingClaim claim, LocalDate nextBillingDate) {
        int updated = jdbcTemplate.update(
            "UPDATE subscription_schema.customer_subscription SET next_billing_date = ?, billing_lock_token = NULL, billing_locked_at = NULL, updated_at = now() " +
                "WHERE id = ? AND billing_lock_token = ?",
            nextBillingDate, claim.subscriptionId(), claim.lockToken()
        );
        if (updated != 1) {
            throw new IllegalStateException("Subscription billing claim was lost");
        }
    }

    public void releaseAfterFailure(BillingClaim claim) {
        jdbcTemplate.update(
            "UPDATE subscription_schema.customer_subscription SET billing_lock_token = NULL, billing_locked_at = NULL " +
                "WHERE id = ? AND billing_lock_token = ?",
            claim.subscriptionId(), claim.lockToken()
        );
    }

    @Transactional
    public List<OutboxRecord> claimOutbox(int batchSize, int maxAttempts, int staleLockMinutes) {
        UUID lockToken = UUID.randomUUID();
        String sql = """
            WITH candidates AS (
                SELECT id
                  FROM subscription_schema.subscription_payment_outbox
                 WHERE (
                    status IN ('PENDING', 'FAILED') AND next_attempt_at <= now() AND attempt_count < ?
                 ) OR (
                    status = 'PROCESSING' AND locked_at < now() - (? * INTERVAL '1 minute')
                 )
                 ORDER BY created_at
                 FOR UPDATE SKIP LOCKED
                 LIMIT ?
            )
            UPDATE subscription_schema.subscription_payment_outbox o
               SET status = 'PROCESSING', lock_token = ?, locked_at = now(),
                   attempt_count = attempt_count + 1, updated_at = now(), last_error = NULL
              FROM candidates c
             WHERE o.id = c.id
            RETURNING o.id, o.event_type, o.correlation_id, o.payload, o.attempt_count
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

    public void markPublished(OutboxRecord record, String brokerMessageId) {
        jdbcTemplate.update(
            "UPDATE subscription_schema.subscription_payment_outbox SET status = 'PUBLISHED', published_at = now(), broker_message_id = ?, " +
                "lock_token = NULL, locked_at = NULL, updated_at = now() WHERE id = ? AND lock_token = ?",
            brokerMessageId, record.id(), record.lockToken()
        );
    }

    public void markPublishFailure(OutboxRecord record, int maxAttempts, String error) {
        boolean dead = record.attemptCount() >= maxAttempts;
        long delaySeconds = Math.min(3600L, 5L * (1L << Math.min(10, Math.max(0, record.attemptCount() - 1))));
        jdbcTemplate.update(
            "UPDATE subscription_schema.subscription_payment_outbox SET status = ?, next_attempt_at = now() + (? * INTERVAL '1 second'), " +
                "last_error = ?, lock_token = NULL, locked_at = NULL, updated_at = now() WHERE id = ? AND lock_token = ?",
            dead ? "DEAD_LETTER" : "FAILED",
            dead ? 0L : delaySeconds,
            safe(error),
            record.id(), record.lockToken()
        );
    }

    private static String safe(String value) {
        String normalized = value == null ? "Unknown publish failure" : value.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() > 1000 ? normalized.substring(0, 1000) : normalized;
    }

    public record BillingClaim(
        UUID subscriptionId,
        UUID customerIdentityId,
        UUID planId,
        UUID chefIdentityId,
        LocalDate cycleStart,
        String billingPeriod,
        BigDecimal amount,
        String currency,
        UUID lockToken
    ) {
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
}
