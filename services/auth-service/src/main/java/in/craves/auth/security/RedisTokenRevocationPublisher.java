package in.craves.auth.security;

import in.craves.auth.config.JwtProperties;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@ConditionalOnProperty(
    prefix = "craves.token-revocation",
    name = "publisher-enabled",
    havingValue = "true"
)
public class RedisTokenRevocationPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(RedisTokenRevocationPublisher.class);

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final JwtProperties jwtProperties;
    private final String keyPrefix;
    private final int ttlGraceSeconds;
    private final int batchSize;
    private final int maxAttempts;
    private final int staleLockMinutes;
    private final int retryBaseSeconds;

    public RedisTokenRevocationPublisher(
        JdbcTemplate jdbcTemplate,
        StringRedisTemplate redisTemplate,
        JwtProperties jwtProperties,
        @Value("${CRAVES_TOKEN_REVOCATION_KEY_PREFIX:craves:auth:revocation}") String keyPrefix,
        @Value("${CRAVES_TOKEN_REVOCATION_TTL_GRACE_SECONDS:300}") int ttlGraceSeconds,
        @Value("${CRAVES_TOKEN_REVOCATION_BATCH_SIZE:50}") int batchSize,
        @Value("${CRAVES_TOKEN_REVOCATION_MAX_ATTEMPTS:10}") int maxAttempts,
        @Value("${CRAVES_TOKEN_REVOCATION_STALE_LOCK_MINUTES:5}") int staleLockMinutes,
        @Value("${CRAVES_TOKEN_REVOCATION_RETRY_BASE_SECONDS:5}") int retryBaseSeconds
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.jwtProperties = jwtProperties;
        this.keyPrefix = keyPrefix;
        this.ttlGraceSeconds = ttlGraceSeconds;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
        this.staleLockMinutes = staleLockMinutes;
        this.retryBaseSeconds = retryBaseSeconds;
    }

    @PostConstruct
    void validate() {
        if (keyPrefix == null || keyPrefix.isBlank()) {
            throw new IllegalStateException("Token revocation Redis key prefix is required");
        }
        if (ttlGraceSeconds < 60 || ttlGraceSeconds > 86400) {
            throw new IllegalStateException("Token revocation TTL grace must be between 60 and 86400 seconds");
        }
        if (batchSize < 1 || batchSize > 500 || maxAttempts < 1 || maxAttempts > 100) {
            throw new IllegalStateException("Token revocation batch or attempt setting is invalid");
        }
        if (staleLockMinutes < 1 || staleLockMinutes > 120 || retryBaseSeconds < 1 || retryBaseSeconds > 3600) {
            throw new IllegalStateException("Token revocation retry setting is invalid");
        }
    }

    @Scheduled(fixedDelayString = "${CRAVES_TOKEN_REVOCATION_PUBLISHER_FIXED_DELAY_MS:2000}")
    public void publish() {
        for (ProjectionWorkItem item : claim()) {
            try {
                Duration ttl = jwtProperties.getAccessTokenTtl().plusSeconds(ttlGraceSeconds);
                redisTemplate.opsForValue().set(
                    keyPrefix + ":" + item.identityId(),
                    item.accountStatus() + "|" + item.minimumTokenVersion(),
                    ttl
                );
                markPublished(item);
                LOGGER.info(
                    "Token revocation projection published identityId={} minimumVersion={} status={}",
                    item.identityId(), item.minimumTokenVersion(), item.accountStatus()
                );
            } catch (RuntimeException exception) {
                markFailure(item, exception);
                LOGGER.error(
                    "Token revocation projection failed identityId={} attempt={}",
                    item.identityId(), item.attemptCount(), exception
                );
            }
        }
    }

    @Transactional
    List<ProjectionWorkItem> claim() {
        UUID lockToken = UUID.randomUUID();
        String sql = """
            WITH candidates AS (
                SELECT id
                  FROM auth_token_revocation_outbox
                 WHERE attempt_count < ?
                   AND (
                       (status IN ('PENDING', 'FAILED') AND next_attempt_at <= now())
                       OR (status = 'PROCESSING' AND locked_at < now() - (? * INTERVAL '1 minute'))
                   )
                 ORDER BY created_at
                 FOR UPDATE SKIP LOCKED
                 LIMIT ?
            )
            UPDATE auth_token_revocation_outbox outbox
               SET status = 'PROCESSING', lock_token = ?, locked_at = now(),
                   attempt_count = attempt_count + 1, last_error = NULL, updated_at = now()
              FROM candidates candidate
             WHERE outbox.id = candidate.id
            RETURNING outbox.id, outbox.identity_id, outbox.account_status,
                      outbox.minimum_token_version, outbox.attempt_count, outbox.lock_token
            """;
        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new ProjectionWorkItem(
                rs.getObject("id", UUID.class), rs.getObject("identity_id", UUID.class),
                rs.getString("account_status"), rs.getLong("minimum_token_version"),
                rs.getInt("attempt_count"), rs.getObject("lock_token", UUID.class)
            ),
            maxAttempts, staleLockMinutes, batchSize, lockToken
        );
    }

    void markPublished(ProjectionWorkItem item) {
        jdbcTemplate.update(
            """
            UPDATE auth_token_revocation_outbox
               SET status = 'PUBLISHED', published_at = now(), lock_token = NULL,
                   locked_at = NULL, last_error = NULL, updated_at = now()
             WHERE id = ? AND lock_token = ?
            """,
            item.id(), item.lockToken()
        );
    }

    void markFailure(ProjectionWorkItem item, Throwable error) {
        boolean dead = item.attemptCount() >= maxAttempts;
        long delay = Math.min(
            3600L,
            (long) retryBaseSeconds * (1L << Math.min(10, Math.max(0, item.attemptCount() - 1)))
        );
        jdbcTemplate.update(
            """
            UPDATE auth_token_revocation_outbox
               SET status = ?, next_attempt_at = now() + (? * INTERVAL '1 second'),
                   last_error = ?, lock_token = NULL, locked_at = NULL, updated_at = now()
             WHERE id = ? AND lock_token = ?
            """,
            dead ? "DEAD_LETTER" : "FAILED", dead ? 0L : delay,
            safe(error == null ? null : error.getMessage()), item.id(), item.lockToken()
        );
    }

    private static String safe(String value) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() > 1000 ? normalized.substring(0, 1000) : normalized;
    }

    record ProjectionWorkItem(
        UUID id, UUID identityId, String accountStatus, long minimumTokenVersion,
        int attemptCount, UUID lockToken
    ) {}
}
