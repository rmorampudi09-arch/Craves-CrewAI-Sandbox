package in.craves.integration.payment;

import in.craves.integration.config.PaymentProviderProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CashfreeWebhookInboxService {
    private static final int MAX_PAYLOAD_BYTES = 1_048_576;

    private final JdbcTemplate jdbcTemplate;
    private final PaymentProviderProperties provider;
    private final CashfreeWebhookProperties workerProperties;

    public CashfreeWebhookInboxService(
        JdbcTemplate jdbcTemplate,
        PaymentProviderProperties provider,
        CashfreeWebhookProperties workerProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.provider = provider;
        this.workerProperties = workerProperties;
    }

    @Transactional
    public boolean accept(String timestamp, String signature, String version, String idempotencyKey, String rawBody) {
        String effectiveIdempotencyKey = validate(timestamp, signature, version, idempotencyKey, rawBody);
        int inserted = jdbcTemplate.update(
            "INSERT INTO payment_schema.cashfree_webhook_delivery " +
                "(id, idempotency_key, webhook_version, webhook_timestamp, webhook_signature, raw_payload, processing_status, next_attempt_at, first_seen_at, last_seen_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, 'RECEIVED', now(), now(), now()) ON CONFLICT (idempotency_key) DO NOTHING",
            UUID.randomUUID(), effectiveIdempotencyKey, version, Long.parseLong(timestamp), signature, rawBody
        );
        if (inserted == 1) {
            return true;
        }
        DuplicateRow existing = jdbcTemplate.query(
            "SELECT raw_payload, webhook_signature FROM payment_schema.cashfree_webhook_delivery WHERE idempotency_key = ?",
            (rs, rowNum) -> new DuplicateRow(rs.getString("raw_payload"), rs.getString("webhook_signature")),
            effectiveIdempotencyKey
        ).stream().findFirst().orElseThrow();
        if (!MessageDigest.isEqual(
            existing.rawPayload().getBytes(StandardCharsets.UTF_8),
            rawBody.getBytes(StandardCharsets.UTF_8)
        ) || !MessageDigest.isEqual(
            existing.signature().getBytes(StandardCharsets.UTF_8),
            signature.getBytes(StandardCharsets.UTF_8)
        )) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Webhook idempotency key was reused with different content");
        }
        jdbcTemplate.update(
            "UPDATE payment_schema.cashfree_webhook_delivery SET last_seen_at = now() WHERE idempotency_key = ?",
            effectiveIdempotencyKey
        );
        return false;
    }

    @Transactional
    public List<WorkItem> claimBatch() {
        UUID lockToken = UUID.randomUUID();
        String sql = """
            WITH candidates AS (
                SELECT id
                  FROM payment_schema.cashfree_webhook_delivery
                 WHERE (
                    processing_status IN ('RECEIVED', 'FAILED') AND next_attempt_at <= now()
                 ) OR (
                    processing_status = 'PROCESSING'
                    AND processing_started_at < now() - (? * INTERVAL '1 minute')
                 )
                 ORDER BY next_attempt_at, first_seen_at
                 FOR UPDATE SKIP LOCKED
                 LIMIT ?
            )
            UPDATE payment_schema.cashfree_webhook_delivery d
               SET processing_status = 'PROCESSING',
                   lock_token = ?,
                   processing_started_at = now(),
                   attempt_count = attempt_count + 1,
                   last_error = NULL
              FROM candidates c
             WHERE d.id = c.id
            RETURNING d.id, d.webhook_timestamp, d.webhook_signature, d.raw_payload, d.attempt_count
            """;
        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new WorkItem(
                rs.getObject("id", UUID.class),
                lockToken,
                Long.toString(rs.getLong("webhook_timestamp")),
                rs.getString("webhook_signature"),
                rs.getString("raw_payload"),
                rs.getInt("attempt_count")
            ),
            workerProperties.getStaleMinutes(), workerProperties.getBatchSize(), lockToken
        );
    }

    public void complete(WorkItem item) {
        jdbcTemplate.update(
            "UPDATE payment_schema.cashfree_webhook_delivery SET processing_status = 'COMPLETED', completed_at = now(), lock_token = NULL, processing_started_at = NULL WHERE id = ? AND lock_token = ?",
            item.id(), item.lockToken()
        );
    }

    public void fail(WorkItem item, Throwable error) {
        boolean dead = item.attemptCount() >= workerProperties.getMaxAttempts();
        long delaySeconds = Math.min(
            3600L,
            (long) workerProperties.getRetryBaseSeconds() * (1L << Math.min(10, Math.max(0, item.attemptCount() - 1)))
        );
        jdbcTemplate.update(
            "UPDATE payment_schema.cashfree_webhook_delivery SET processing_status = ?, next_attempt_at = now() + (? * INTERVAL '1 second'), last_error = ?, lock_token = NULL, processing_started_at = NULL WHERE id = ? AND lock_token = ?",
            dead ? "DEAD_LETTER" : "FAILED",
            dead ? 0L : delaySeconds,
            safe(error),
            item.id(),
            item.lockToken()
        );
    }

    private String validate(String timestamp, String signature, String version, String idempotencyKey, String rawBody) {
        if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(signature) || !StringUtils.hasText(version)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Required Cashfree webhook headers are missing");
        }
        if (rawBody == null || rawBody.isBlank() || rawBody.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Webhook payload is empty or too large");
        }
        if (!provider.allowedWebhookVersions().contains(version)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported Cashfree webhook version");
        }
        long timestampMillis;
        try {
            timestampMillis = Long.parseLong(timestamp);
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid Cashfree webhook timestamp");
        }
        Duration skew = Duration.between(Instant.ofEpochMilli(timestampMillis), Instant.now()).abs();
        if (skew.getSeconds() > provider.webhookMaxSkewSeconds()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Cashfree webhook timestamp is outside the accepted window");
        }
        if (!verifySignature(timestamp, signature, rawBody)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Cashfree webhook signature");
        }
        String effectiveIdempotencyKey = StringUtils.hasText(idempotencyKey)
            ? idempotencyKey.trim()
            : derivedIdempotencyKey(version, rawBody);
        if (effectiveIdempotencyKey.length() > 160) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook idempotency key is too long");
        }
        return effectiveIdempotencyKey;
    }

    static String derivedIdempotencyKey(String version, String rawBody) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(
                (version + "\n" + rawBody).getBytes(StandardCharsets.UTF_8)
            );
            return "derived-" + HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private boolean verifySignature(String timestamp, String signature, String rawBody) {
        if (!StringUtils.hasText(provider.clientKey())) {
            return false;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(provider.clientKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String expected = Base64.getEncoder().encodeToString(
                mac.doFinal((timestamp + rawBody).getBytes(StandardCharsets.UTF_8))
            );
            return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception exception) {
            return false;
        }
    }

    private static String safe(Throwable error) {
        String value = error == null ? "Unknown webhook processing failure" : error.getMessage();
        if (!StringUtils.hasText(value)) {
            value = error.getClass().getSimpleName();
        }
        value = value.replace('\n', ' ').replace('\r', ' ').trim();
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }

    private record DuplicateRow(String rawPayload, String signature) {
    }

    public record WorkItem(
        UUID id,
        UUID lockToken,
        String timestamp,
        String signature,
        String rawPayload,
        int attemptCount
    ) {
    }
}
