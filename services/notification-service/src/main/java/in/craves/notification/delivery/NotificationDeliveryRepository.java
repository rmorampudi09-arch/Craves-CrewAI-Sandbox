package in.craves.notification.delivery;

import in.craves.notification.delivery.NotificationDeliveryModels.DeliveryResult;
import in.craves.notification.delivery.NotificationDeliveryModels.DeliveryWorkItem;
import in.craves.notification.delivery.NotificationDeliveryModels.DeviceResponse;
import in.craves.notification.delivery.NotificationDeliveryModels.PreferenceResponse;
import in.craves.notification.delivery.NotificationDeliveryModels.PushDevice;
import in.craves.notification.delivery.NotificationDeliveryModels.RegisterDeviceRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class NotificationDeliveryRepository {
    private final JdbcTemplate jdbcTemplate;

    public NotificationDeliveryRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public DeviceResponse register(UUID identityId, RegisterDeviceRequest request) {
        String platform = request.platform().trim().toUpperCase(Locale.ROOT);
        String token = request.deviceToken().trim();
        String tokenHash = sha256(token);
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO notification_schema.push_device_registration " +
                "(id, recipient_identity_id, platform, device_token, token_hash, app_instance_id, app_version, active, last_seen_at, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, true, now(), now(), now()) " +
                "ON CONFLICT (token_hash) DO UPDATE SET recipient_identity_id = EXCLUDED.recipient_identity_id, " +
                "platform = EXCLUDED.platform, device_token = EXCLUDED.device_token, app_instance_id = EXCLUDED.app_instance_id, " +
                "app_version = EXCLUDED.app_version, active = true, failure_count = 0, last_failure_code = NULL, " +
                "disabled_at = NULL, last_seen_at = now(), updated_at = now()",
            id, identityId, platform, token, tokenHash, request.appInstanceId(), request.appVersion()
        );
        return findDevice(identityId, tokenHash);
    }

    public List<DeviceResponse> listDevices(UUID identityId) {
        return jdbcTemplate.query(
            "SELECT id, platform, token_hash, app_instance_id, app_version, active, last_seen_at " +
                "FROM notification_schema.push_device_registration WHERE recipient_identity_id = ? " +
                "ORDER BY last_seen_at DESC",
            this::mapDevice,
            identityId
        );
    }

    public void deactivateDevice(UUID identityId, UUID deviceId) {
        jdbcTemplate.update(
            "UPDATE notification_schema.push_device_registration SET active = false, disabled_at = now(), updated_at = now() " +
                "WHERE id = ? AND recipient_identity_id = ?",
            deviceId, identityId
        );
    }

    public List<PushDevice> activePushDevices(UUID identityId) {
        return jdbcTemplate.query(
            "SELECT id, device_token, token_hash FROM notification_schema.push_device_registration " +
                "WHERE recipient_identity_id = ? AND active = true ORDER BY last_seen_at DESC LIMIT 20",
            (rs, rowNum) -> new PushDevice(
                rs.getObject("id", UUID.class), rs.getString("device_token"), rs.getString("token_hash")
            ),
            identityId
        );
    }

    public void disablePushDevice(UUID deviceId, String errorCode) {
        jdbcTemplate.update(
            "UPDATE notification_schema.push_device_registration SET active = false, failure_count = failure_count + 1, " +
                "last_failure_code = ?, disabled_at = now(), updated_at = now() WHERE id = ?",
            safe(errorCode, 120), deviceId
        );
    }

    public PreferenceResponse setPreference(UUID identityId, String channel, boolean enabled) {
        String normalized = channel.trim().toUpperCase(Locale.ROOT);
        jdbcTemplate.update(
            "INSERT INTO notification_schema.notification_preference " +
                "(recipient_identity_id, channel, enabled, updated_at, updated_by_identity_id) VALUES (?, ?, ?, now(), ?) " +
                "ON CONFLICT (recipient_identity_id, channel) DO UPDATE SET enabled = EXCLUDED.enabled, " +
                "updated_at = now(), updated_by_identity_id = EXCLUDED.updated_by_identity_id",
            identityId, normalized, enabled, identityId
        );
        return new PreferenceResponse(normalized, enabled, OffsetDateTime.now());
    }

    public List<PreferenceResponse> listPreferences(UUID identityId) {
        return jdbcTemplate.query(
            "SELECT channel, enabled, updated_at FROM notification_schema.notification_preference " +
                "WHERE recipient_identity_id = ? ORDER BY channel",
            (rs, rowNum) -> new PreferenceResponse(
                rs.getString("channel"), rs.getBoolean("enabled"), rs.getObject("updated_at", OffsetDateTime.class)
            ),
            identityId
        );
    }

    @Transactional
    public List<DeliveryWorkItem> claim(
        String channel,
        int batchSize,
        int maxAttempts,
        int staleLockMinutes
    ) {
        UUID lockToken = UUID.randomUUID();
        String sql = """
            WITH candidates AS (
                SELECT request.id
                  FROM notification_schema.notification_request request
                 WHERE request.channel = ?
                   AND COALESCE((
                       SELECT preference.enabled
                         FROM notification_schema.notification_preference preference
                        WHERE preference.recipient_identity_id = request.recipient_identity_id
                          AND preference.channel = request.channel
                   ), true) = true
                   AND (
                       (request.status IN ('PENDING', 'FAILED')
                        AND COALESCE(request.next_attempt_at, now()) <= now()
                        AND request.attempt_count < ?)
                       OR
                       (request.status = 'PROCESSING'
                        AND request.locked_at < now() - (? * INTERVAL '1 minute'))
                   )
                 ORDER BY request.priority ASC, request.created_at ASC
                 FOR UPDATE SKIP LOCKED
                 LIMIT ?
            )
            UPDATE notification_schema.notification_request request
               SET status = 'PROCESSING', lock_token = ?, locked_at = now(),
                   attempt_count = attempt_count + 1, last_error = NULL, updated_at = now()
              FROM candidates candidate
             WHERE request.id = candidate.id
            RETURNING request.id, request.recipient_identity_id, request.channel,
                      request.delivery_address, request.title, request.body,
                      request.target_type, request.target_id, request.priority,
                      request.attempt_count
            """;
        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new DeliveryWorkItem(
                rs.getObject("id", UUID.class),
                rs.getObject("recipient_identity_id", UUID.class),
                rs.getString("channel"),
                rs.getString("delivery_address"),
                rs.getString("title"),
                rs.getString("body"),
                rs.getString("target_type"),
                rs.getObject("target_id", UUID.class),
                Map.of(),
                rs.getInt("priority"),
                rs.getInt("attempt_count"),
                lockToken
            ),
            channel, maxAttempts, staleLockMinutes, batchSize, lockToken
        );
    }

    @Transactional
    public void markSent(DeliveryWorkItem item, DeliveryResult result) {
        int updated = jdbcTemplate.update(
            "UPDATE notification_schema.notification_request SET status = 'SENT', provider_message_id = ?, sent_at = now(), " +
                "lock_token = NULL, locked_at = NULL, last_error = NULL, updated_at = now() WHERE id = ? AND lock_token = ?",
            result.providerMessageId(), item.requestId(), item.lockToken()
        );
        if (updated == 1) {
            insertAttempt(item, result.provider(), result.providerMessageId(), "SENT", null);
        }
    }

    @Transactional
    public void markFailure(DeliveryWorkItem item, int maxAttempts, int retryBaseSeconds, String provider, Throwable error) {
        boolean dead = item.attemptCount() >= maxAttempts;
        long delay = Math.min(3600L, (long) retryBaseSeconds * (1L << Math.min(10, Math.max(0, item.attemptCount() - 1))));
        String message = safe(error == null ? null : error.getMessage(), 1000);
        int updated = jdbcTemplate.update(
            "UPDATE notification_schema.notification_request SET status = ?, " +
                "next_attempt_at = now() + (? * INTERVAL '1 second'), last_error = ?, " +
                "lock_token = NULL, locked_at = NULL, updated_at = now() WHERE id = ? AND lock_token = ?",
            dead ? "DEAD_LETTER" : "FAILED", dead ? 0L : delay, message, item.requestId(), item.lockToken()
        );
        if (updated == 1) {
            insertAttempt(item, provider, null, dead ? "DEAD_LETTER" : "FAILED", message);
            if (dead) {
                jdbcTemplate.update(
                    "INSERT INTO notification_schema.channel_delivery_dead_letter " +
                        "(id, notification_request_id, channel, final_error_code, final_error_message, attempt_count, created_at) " +
                        "VALUES (?, ?, ?, ?, ?, ?, now()) " +
                        "ON CONFLICT (notification_request_id) DO UPDATE SET " +
                        "channel = EXCLUDED.channel, final_error_code = EXCLUDED.final_error_code, " +
                        "final_error_message = EXCLUDED.final_error_message, attempt_count = EXCLUDED.attempt_count, " +
                        "created_at = now()",
                    UUID.randomUUID(), item.requestId(), item.channel(), errorCode(error), message, item.attemptCount()
                );
            }
        }
    }

    public void markSkippedForPreference(UUID requestId) {
        jdbcTemplate.update(
            "UPDATE notification_schema.notification_request SET status = 'SKIPPED', updated_at = now() WHERE id = ?",
            requestId
        );
    }

    private void insertAttempt(
        DeliveryWorkItem item,
        String provider,
        String providerMessageId,
        String status,
        String error
    ) {
        jdbcTemplate.update(
            "INSERT INTO notification_schema.notification_delivery_attempt " +
                "(id, request_id, channel, provider, provider_message_id, attempt_number, status, error_message, started_at, completed_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, now(), now())",
            UUID.randomUUID(), item.requestId(), item.channel(), provider, providerMessageId,
            item.attemptCount(), status, error
        );
    }

    private DeviceResponse findDevice(UUID identityId, String tokenHash) {
        return jdbcTemplate.query(
            "SELECT id, platform, token_hash, app_instance_id, app_version, active, last_seen_at " +
                "FROM notification_schema.push_device_registration WHERE recipient_identity_id = ? AND token_hash = ?",
            this::mapDevice,
            identityId, tokenHash
        ).stream().findFirst().orElseThrow();
    }

    private DeviceResponse mapDevice(ResultSet rs, int rowNum) throws SQLException {
        return new DeviceResponse(
            rs.getObject("id", UUID.class), rs.getString("platform"), rs.getString("token_hash"),
            rs.getString("app_instance_id"), rs.getString("app_version"), rs.getBoolean("active"),
            rs.getObject("last_seen_at", OffsetDateTime.class)
        );
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Push token hash could not be generated", exception);
        }
    }

    private static String errorCode(Throwable error) {
        return error == null ? "UNKNOWN" : safe(error.getClass().getSimpleName(), 120);
    }

    private static String safe(String value, int max) {
        if (value == null || value.isBlank()) {
            return "UNKNOWN";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() > max ? normalized.substring(0, max) : normalized;
    }
}
