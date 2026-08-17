package in.craves.integration.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import in.craves.integration.subscription.SubscriptionPaymentModels.EventEnvelope;
import in.craves.integration.subscription.SubscriptionPaymentModels.PaymentRequestedData;
import in.craves.integration.subscription.SubscriptionPaymentModels.SubscriptionPaymentResponse;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SubscriptionPaymentRepository {
    private final JdbcTemplate jdbcTemplate;

    public SubscriptionPaymentRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public boolean acceptRequest(EventEnvelope<PaymentRequestedData> event, JsonNode rawPayload) {
        int inbox = jdbcTemplate.update(
            "INSERT INTO payment_schema.subscription_payment_request_inbox " +
                "(event_id, event_type, event_version, correlation_id, causation_id, subject, payload, processing_status, received_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb), 'RECEIVED', now()) ON CONFLICT (event_id) DO NOTHING",
            event.eventId(), event.eventType(), event.eventVersion(), event.correlationId(), event.causationId(),
            event.subject(), rawPayload.toString()
        );
        if (inbox == 0) {
            return false;
        }
        PaymentRequestedData data = event.data();
        int intent = jdbcTemplate.update(
            "INSERT INTO payment_schema.subscription_payment_intent " +
                "(id, invoice_id, subscription_id, plan_id, customer_identity_id, chef_identity_id, cycle_start, cycle_end, amount, currency, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'PAYMENT_REQUESTED', now(), now()) " +
                "ON CONFLICT (invoice_id) DO NOTHING",
            UUID.randomUUID(), data.invoiceId(), data.subscriptionId(), data.planId(), data.customerIdentityId(),
            data.chefIdentityId(), data.cycleStart(), data.cycleEnd(), data.amount(), data.currency()
        );
        jdbcTemplate.update(
            "UPDATE payment_schema.subscription_payment_request_inbox SET processing_status = ?, processed_at = now() WHERE event_id = ?",
            intent == 1 ? "PROCESSED" : "DUPLICATE", event.eventId()
        );
        return intent == 1;
    }

    public Optional<PaymentIntent> findByInvoice(UUID invoiceId) {
        return jdbcTemplate.query(
            "SELECT * FROM payment_schema.subscription_payment_intent WHERE invoice_id = ?",
            this::mapIntent,
            invoiceId
        ).stream().findFirst();
    }

    public Optional<PaymentIntent> findLatestBySubscription(UUID subscriptionId) {
        return jdbcTemplate.query(
            "SELECT * FROM payment_schema.subscription_payment_intent WHERE subscription_id = ? " +
                "ORDER BY cycle_start DESC, created_at DESC LIMIT 1",
            this::mapIntent,
            subscriptionId
        ).stream().findFirst();
    }

    public Optional<PaymentIntent> findByCashfreeOrder(String cashfreeOrderId) {
        return jdbcTemplate.query(
            "SELECT * FROM payment_schema.subscription_payment_intent WHERE cashfree_order_id = ?",
            this::mapIntent,
            cashfreeOrderId
        ).stream().findFirst();
    }

    public Optional<PaymentIntent> findByProviderOrder(String provider, String providerOrderId) {
        return jdbcTemplate.query(
            "SELECT * FROM payment_schema.subscription_payment_intent WHERE provider = ? AND provider_order_id = ?",
            this::mapIntent,
            provider,
            providerOrderId
        ).stream().findFirst();
    }

    @Transactional
    public PaymentIntent storeProviderOrder(
        UUID intentId,
        String cashfreeOrderId,
        String cfOrderId,
        String paymentSessionId,
        String providerStatus,
        JsonNode request,
        JsonNode response
    ) {
        int updated = jdbcTemplate.update(
            "UPDATE payment_schema.subscription_payment_intent SET provider = 'CASHFREE', provider_order_id = ?, " +
                "provider_payment_id = ?, cashfree_order_id = ?, cashfree_cf_order_id = ?, " +
                "payment_session_id = ?, provider_status = ?, status = 'PAYMENT_PENDING', attempt_count = attempt_count + 1, " +
                "last_error = NULL, updated_at = now() WHERE id = ? AND status IN ('PAYMENT_REQUESTED', 'FAILED')",
            cashfreeOrderId, cfOrderId, cashfreeOrderId, cfOrderId, paymentSessionId, providerStatus, intentId
        );
        if (updated != 1) {
            return findById(intentId).orElseThrow();
        }
        return findById(intentId).orElseThrow();
    }

    @Transactional
    public PaymentIntent storeRazorpayOrder(
        UUID intentId,
        String providerOrderId,
        String checkoutKeyId,
        String providerStatus
    ) {
        int updated = jdbcTemplate.update(
            "UPDATE payment_schema.subscription_payment_intent SET provider = 'RAZORPAY', provider_order_id = ?, " +
                "checkout_key_id = ?, provider_status = ?, status = 'PAYMENT_PENDING', " +
                "attempt_count = attempt_count + 1, last_error = NULL, updated_at = now() " +
                "WHERE id = ? AND status IN ('PAYMENT_REQUESTED', 'FAILED')",
            providerOrderId, checkoutKeyId, providerStatus, intentId
        );
        return findById(intentId).orElseThrow();
    }

    @Transactional
    public boolean applyProviderStatus(
        PaymentIntent intent,
        String normalizedStatus,
        String providerStatus,
        String providerPaymentId,
        JsonNode eventPayload
    ) {
        if (normalizedStatus.equals(intent.status())) {
            jdbcTemplate.update(
                "UPDATE payment_schema.subscription_payment_intent SET provider_status = ?, updated_at = now() WHERE id = ?",
                providerStatus, intent.id()
            );
            return false;
        }
        int updated = jdbcTemplate.update(
            "UPDATE payment_schema.subscription_payment_intent SET status = ?, provider_status = ?, provider_payment_id = COALESCE(?, provider_payment_id), " +
                "paid_at = CASE WHEN ? = 'PAID' THEN now() ELSE paid_at END, updated_at = now() WHERE id = ? AND status <> 'PAID'",
            normalizedStatus, providerStatus, providerPaymentId, normalizedStatus, intent.id()
        );
        if (updated != 1) {
            return false;
        }
        UUID outboxId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO payment_schema.subscription_payment_status_outbox " +
                "(id, event_key, aggregate_id, event_type, event_version, correlation_id, causation_id, subject, payload, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 'v1', ?, ?, ?, CAST(? AS jsonb), 'PENDING', now(), now()) ON CONFLICT (event_key) DO NOTHING",
            outboxId,
            SubscriptionPaymentModels.PAYMENT_STATUS_CHANGED + ":" + intent.id() + ":" + normalizedStatus,
            intent.id(),
            SubscriptionPaymentModels.PAYMENT_STATUS_CHANGED,
            intent.invoiceId(),
            providerPaymentId == null ? null : stableUuid(providerPaymentId),
            intent.invoiceId(),
            eventPayload.toString()
        );
        return true;
    }

    @Transactional
    public List<OutboxRecord> claimOutbox(int batchSize, int maxAttempts, int staleLockMinutes) {
        UUID lockToken = UUID.randomUUID();
        String sql = """
            WITH candidates AS (
                SELECT id
                  FROM payment_schema.subscription_payment_status_outbox
                 WHERE (status IN ('PENDING', 'FAILED') AND next_attempt_at <= now() AND attempt_count < ?)
                    OR (status = 'PROCESSING' AND locked_at < now() - (? * INTERVAL '1 minute'))
                 ORDER BY created_at
                 FOR UPDATE SKIP LOCKED
                 LIMIT ?
            )
            UPDATE payment_schema.subscription_payment_status_outbox o
               SET status = 'PROCESSING', lock_token = ?, locked_at = now(),
                   attempt_count = attempt_count + 1, last_error = NULL, updated_at = now()
              FROM candidates c
             WHERE o.id = c.id
            RETURNING o.id, o.event_type, o.correlation_id, o.payload::text, o.attempt_count
            """;
        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new OutboxRecord(
                rs.getObject("id", UUID.class), rs.getString("event_type"),
                rs.getObject("correlation_id", UUID.class), rs.getString("payload"),
                rs.getInt("attempt_count"), lockToken
            ),
            maxAttempts, staleLockMinutes, batchSize, lockToken
        );
    }

    public void markPublished(OutboxRecord record) {
        jdbcTemplate.update(
            "UPDATE payment_schema.subscription_payment_status_outbox SET status = 'PUBLISHED', published_at = now(), " +
                "broker_message_id = ?, lock_token = NULL, locked_at = NULL, updated_at = now() WHERE id = ? AND lock_token = ?",
            record.id().toString(), record.id(), record.lockToken()
        );
    }

    public void markPublishFailure(OutboxRecord record, int maxAttempts, Throwable error) {
        boolean dead = record.attemptCount() >= maxAttempts;
        long delay = Math.min(3600L, 5L * (1L << Math.min(10, Math.max(0, record.attemptCount() - 1))));
        jdbcTemplate.update(
            "UPDATE payment_schema.subscription_payment_status_outbox SET status = ?, next_attempt_at = now() + (? * INTERVAL '1 second'), " +
                "last_error = ?, lock_token = NULL, locked_at = NULL, updated_at = now() WHERE id = ? AND lock_token = ?",
            dead ? "DEAD_LETTER" : "FAILED", dead ? 0L : delay, safe(error), record.id(), record.lockToken()
        );
    }

    public SubscriptionPaymentResponse response(PaymentIntent intent) {
        return new SubscriptionPaymentResponse(
            intent.id(), intent.invoiceId(), intent.subscriptionId(), intent.cycleStart(), intent.cycleEnd(),
            intent.amount(), intent.currency(), intent.status(), intent.paymentSessionId(), intent.providerStatus(),
            intent.createdAt(), intent.updatedAt(), intent.paidAt(), intent.provider(), intent.providerOrderId(),
            intent.providerPaymentId(), intent.checkoutKeyId()
        );
    }

    private Optional<PaymentIntent> findById(UUID id) {
        return jdbcTemplate.query(
            "SELECT * FROM payment_schema.subscription_payment_intent WHERE id = ?",
            this::mapIntent,
            id
        ).stream().findFirst();
    }

    private PaymentIntent mapIntent(ResultSet rs, int rowNum) throws SQLException {
        return new PaymentIntent(
            rs.getObject("id", UUID.class), rs.getObject("invoice_id", UUID.class),
            rs.getObject("subscription_id", UUID.class), rs.getObject("plan_id", UUID.class),
            rs.getObject("customer_identity_id", UUID.class), rs.getObject("chef_identity_id", UUID.class),
            rs.getObject("cycle_start", LocalDate.class), rs.getObject("cycle_end", LocalDate.class),
            rs.getBigDecimal("amount"), rs.getString("currency"), rs.getString("status"),
            rs.getString("cashfree_order_id"), rs.getString("cashfree_cf_order_id"),
            rs.getString("payment_session_id"), rs.getString("provider_status"),
            instant(rs, "created_at"), instant(rs, "updated_at"), instant(rs, "paid_at"),
            rs.getString("provider"), rs.getString("provider_order_id"),
            rs.getString("provider_payment_id"), rs.getString("checkout_key_id")
        );
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static UUID stableUuid(String value) {
        return UUID.nameUUIDFromBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static String safe(Throwable error) {
        String value = error == null || error.getMessage() == null ? "Unknown publish failure" : error.getMessage();
        value = value.replace('\n', ' ').replace('\r', ' ').trim();
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }

    public record PaymentIntent(
        UUID id,
        UUID invoiceId,
        UUID subscriptionId,
        UUID planId,
        UUID customerIdentityId,
        UUID chefIdentityId,
        LocalDate cycleStart,
        LocalDate cycleEnd,
        BigDecimal amount,
        String currency,
        String status,
        String cashfreeOrderId,
        String cashfreeCfOrderId,
        String paymentSessionId,
        String providerStatus,
        Instant createdAt,
        Instant updatedAt,
        Instant paidAt,
        String provider,
        String providerOrderId,
        String providerPaymentId,
        String checkoutKeyId
    ) {
        public PaymentIntent(
            UUID id, UUID invoiceId, UUID subscriptionId, UUID planId, UUID customerIdentityId,
            UUID chefIdentityId, LocalDate cycleStart, LocalDate cycleEnd, BigDecimal amount,
            String currency, String status, String cashfreeOrderId, String cashfreeCfOrderId,
            String paymentSessionId, String providerStatus, Instant createdAt, Instant updatedAt,
            Instant paidAt
        ) {
            this(id, invoiceId, subscriptionId, planId, customerIdentityId, chefIdentityId,
                cycleStart, cycleEnd, amount, currency, status, cashfreeOrderId, cashfreeCfOrderId,
                paymentSessionId, providerStatus, createdAt, updatedAt, paidAt,
                "CASHFREE", cashfreeOrderId, cashfreeCfOrderId, null);
        }
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
