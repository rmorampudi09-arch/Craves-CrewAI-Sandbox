package in.craves.integration.delivery.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public class DeliveryStatusRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public DeliveryStatusRepository(JdbcTemplate jdbc,
                                    ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public List<WebhookWorkItem> claimWebhookBatch(int limit,
                                                   int staleMinutes,
                                                   int maxAttempts) {
        return jdbc.query("""
            WITH selected AS (
                SELECT id
                FROM delivery_schema.delivery_webhook_inbox
                WHERE (
                        processing_status IN ('RECEIVED', 'FAILED')
                        AND next_attempt_at <= now()
                        AND attempt_count < ?
                      )
                   OR (
                        processing_status = 'PROCESSING'
                        AND processing_started_at < now() - make_interval(mins => ?)
                        AND attempt_count < ?
                      )
                ORDER BY received_at
                FOR UPDATE SKIP LOCKED
                LIMIT ?
            )
            UPDATE delivery_schema.delivery_webhook_inbox AS inbox
            SET processing_status = 'PROCESSING',
                attempt_count = attempt_count + 1,
                processing_started_at = now(),
                error_message = NULL
            FROM selected
            WHERE inbox.id = selected.id
            RETURNING inbox.id,
                      inbox.provider_id,
                      inbox.provider_event_id,
                      inbox.raw_payload,
                      inbox.attempt_count
            """,
            this::mapWebhook,
            maxAttempts,
            staleMinutes,
            maxAttempts,
            limit
        );
    }

    public Optional<DeliveryJobState> findJobByProviderOrder(String providerId,
                                                             String providerOrderId) {
        return jdbc.query("""
            SELECT id,
                   order_id,
                   chef_sub_order_id,
                   provider_id,
                   provider_delivery_id,
                   status,
                   provider_status,
                   tracking_url,
                   last_status_observed_at
            FROM delivery_schema.delivery_job
            WHERE provider_id = lower(?)
              AND provider_delivery_id = ?
            """,
            this::mapJob,
            providerId,
            providerOrderId
        ).stream().findFirst();
    }

    public Optional<DeliveryJobState> lockJob(UUID deliveryJobId) {
        return jdbc.query("""
            SELECT id,
                   order_id,
                   chef_sub_order_id,
                   provider_id,
                   provider_delivery_id,
                   status,
                   provider_status,
                   tracking_url,
                   last_status_observed_at
            FROM delivery_schema.delivery_job
            WHERE id = ?
            FOR UPDATE
            """,
            this::mapJob,
            deliveryJobId
        ).stream().findFirst();
    }

    public boolean insertEventIfAbsent(UUID deliveryJobId,
                                       String providerId,
                                       String providerEventId,
                                       String eventType,
                                       String source,
                                       String normalizedStatus,
                                       String providerStatus,
                                       JsonNode payload,
                                       Instant occurredAt,
                                       boolean applied,
                                       String ignoredReason) {
        int inserted = jdbc.update("""
            INSERT INTO delivery_schema.delivery_event
                (id,
                 delivery_job_id,
                 provider_id,
                 provider_event_id,
                 event_type,
                 normalized_status,
                 provider_status,
                 source,
                 payload,
                 occurred_at,
                 applied,
                 ignored_reason,
                 created_at)
            VALUES (?, ?, lower(?), ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?, now())
            ON CONFLICT (provider_id, provider_event_id) DO NOTHING
            """,
            UUID.randomUUID(),
            deliveryJobId,
            providerId,
            providerEventId,
            eventType,
            normalizedStatus,
            providerStatus,
            source,
            payload.toString(),
            databaseTimestamp(occurredAt),
            applied,
            ignoredReason
        );
        return inserted == 1;
    }

    public void applyJobStatus(UUID deliveryJobId,
                               String normalizedStatus,
                               String providerStatus,
                               String trackingUrl,
                               Instant observedAt,
                               String source,
                               Instant nextTrackingAt) {
        jdbc.update("""
            UPDATE delivery_schema.delivery_job
            SET status = ?,
                provider_status = ?,
                tracking_url = COALESCE(NULLIF(?, ''), tracking_url),
                last_status_observed_at = ?,
                last_status_source = ?,
                picked_up_at = CASE
                    WHEN ? IN ('PICKED_UP', 'IN_TRANSIT', 'AT_DROPOFF', 'DELIVERED')
                         AND picked_up_at IS NULL
                    THEN ?
                    ELSE picked_up_at
                END,
                delivered_at = CASE
                    WHEN ? = 'DELIVERED' AND delivered_at IS NULL
                    THEN ?
                    ELSE delivered_at
                END,
                next_tracking_at = ?,
                tracking_attempt_count = 0,
                tracking_processing_started_at = NULL,
                tracking_dead_lettered_at = NULL,
                last_tracking_error = NULL,
                updated_at = now()
            WHERE id = ?
            """,
            normalizedStatus,
            providerStatus,
            trackingUrl,
            databaseTimestamp(observedAt),
            source,
            normalizedStatus,
            databaseTimestamp(observedAt),
            normalizedStatus,
            databaseTimestamp(observedAt),
            databaseTimestamp(nextTrackingAt),
            deliveryJobId
        );
    }

    public void markWebhookProcessed(UUID inboxId,
                                     UUID deliveryJobId,
                                     String providerOrderId,
                                     String providerDeliveryId,
                                     String normalizedStatus,
                                     String processingResult) {
        jdbc.update("""
            UPDATE delivery_schema.delivery_webhook_inbox
            SET processing_status = 'PROCESSED',
                delivery_job_id = ?,
                provider_order_id = ?,
                provider_delivery_id = ?,
                normalized_status = ?,
                processing_result = ?,
                processing_started_at = NULL,
                error_message = NULL,
                processed_at = now()
            WHERE id = ?
            """,
            deliveryJobId,
            providerOrderId,
            providerDeliveryId,
            normalizedStatus,
            processingResult,
            inboxId
        );
    }

    public void markWebhookDuplicate(UUID inboxId,
                                     String processingResult) {
        jdbc.update("""
            UPDATE delivery_schema.delivery_webhook_inbox
            SET processing_status = 'DUPLICATE',
                processing_result = ?,
                processing_started_at = NULL,
                error_message = NULL,
                processed_at = now()
            WHERE id = ?
            """,
            processingResult,
            inboxId
        );
    }

    public void markWebhookFailed(UUID inboxId,
                                  int attemptCount,
                                  int maxAttempts,
                                  Instant nextAttemptAt,
                                  String safeError) {
        jdbc.update("""
            UPDATE delivery_schema.delivery_webhook_inbox
            SET processing_status = CASE
                    WHEN ? >= ? THEN 'DEAD_LETTER'
                    ELSE 'FAILED'
                END,
                next_attempt_at = ?,
                processing_started_at = NULL,
                error_message = ?,
                processed_at = CASE
                    WHEN ? >= ? THEN now()
                    ELSE NULL
                END
            WHERE id = ?
            """,
            attemptCount,
            maxAttempts,
            databaseTimestamp(nextAttemptAt),
            truncate(safeError, 2000),
            attemptCount,
            maxAttempts,
            inboxId
        );
    }

    @Transactional
    public List<TrackingWorkItem> claimTrackingBatch(int limit,
                                                     int staleMinutes,
                                                     int maxAttempts) {
        return jdbc.query("""
            WITH selected AS (
                SELECT id
                FROM delivery_schema.delivery_job
                WHERE status NOT IN ('DELIVERED', 'CANCELLED', 'RETURNED', 'FAILED')
                  AND tracking_attempt_count < ?
                  AND tracking_dead_lettered_at IS NULL
                  AND (
                        (
                          next_tracking_at IS NOT NULL
                          AND next_tracking_at <= now()
                          AND tracking_processing_started_at IS NULL
                        )
                     OR (
                          tracking_processing_started_at
                              < now() - make_interval(mins => ?)
                        )
                  )
                ORDER BY next_tracking_at NULLS FIRST, updated_at
                FOR UPDATE SKIP LOCKED
                LIMIT ?
            )
            UPDATE delivery_schema.delivery_job AS job
            SET tracking_attempt_count = tracking_attempt_count + 1,
                tracking_processing_started_at = now(),
                last_tracking_error = NULL,
                updated_at = now()
            FROM selected
            WHERE job.id = selected.id
            RETURNING job.id,
                      job.order_id,
                      job.chef_sub_order_id,
                      job.provider_id,
                      job.provider_delivery_id,
                      job.tracking_attempt_count
            """,
            this::mapTracking,
            maxAttempts,
            staleMinutes,
            limit
        );
    }

    public void markTrackingNoChange(UUID deliveryJobId,
                                     Instant nextTrackingAt) {
        jdbc.update("""
            UPDATE delivery_schema.delivery_job
            SET next_tracking_at = ?,
                tracking_attempt_count = 0,
                tracking_processing_started_at = NULL,
                tracking_dead_lettered_at = NULL,
                last_tracking_error = NULL,
                updated_at = now()
            WHERE id = ?
            """,
            databaseTimestamp(nextTrackingAt),
            deliveryJobId
        );
    }

    public void markTrackingFailed(UUID deliveryJobId,
                                   int attemptCount,
                                   int maxAttempts,
                                   Instant nextTrackingAt,
                                   String safeError) {
        jdbc.update("""
            UPDATE delivery_schema.delivery_job
            SET next_tracking_at = CASE
                    WHEN ? >= ? THEN NULL
                    ELSE ?
                END,
                tracking_processing_started_at = NULL,
                tracking_dead_lettered_at = CASE
                    WHEN ? >= ? THEN now()
                    ELSE NULL
                END,
                last_tracking_error = ?,
                updated_at = now()
            WHERE id = ?
            """,
            attemptCount,
            maxAttempts,
            databaseTimestamp(nextTrackingAt),
            attemptCount,
            maxAttempts,
            truncate(safeError, 2000),
            deliveryJobId
        );
    }

    private WebhookWorkItem mapWebhook(ResultSet rs,
                                       int rowNumber) throws SQLException {
        return new WebhookWorkItem(
            rs.getObject("id", UUID.class),
            rs.getString("provider_id"),
            rs.getString("provider_event_id"),
            readJson(rs.getString("raw_payload")),
            rs.getInt("attempt_count")
        );
    }

    private DeliveryJobState mapJob(ResultSet rs,
                                    int rowNumber) throws SQLException {
        OffsetDateTime observedAt = rs.getObject(
            "last_status_observed_at",
            OffsetDateTime.class
        );
        return new DeliveryJobState(
            rs.getObject("id", UUID.class),
            rs.getObject("order_id", UUID.class),
            rs.getObject("chef_sub_order_id", UUID.class),
            rs.getString("provider_id"),
            rs.getString("provider_delivery_id"),
            rs.getString("status"),
            rs.getString("provider_status"),
            rs.getString("tracking_url"),
            observedAt == null ? null : observedAt.toInstant()
        );
    }

    private TrackingWorkItem mapTracking(ResultSet rs,
                                         int rowNumber) throws SQLException {
        return new TrackingWorkItem(
            rs.getObject("id", UUID.class),
            rs.getObject("order_id", UUID.class),
            rs.getObject("chef_sub_order_id", UUID.class),
            rs.getString("provider_id"),
            rs.getString("provider_delivery_id"),
            rs.getInt("tracking_attempt_count")
        );
    }

    private JsonNode readJson(String value) throws SQLException {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException ex) {
            throw new SQLException("Stored webhook payload is invalid JSON", ex);
        }
    }

    static OffsetDateTime databaseTimestamp(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    private static String truncate(String value,
                                   int maximumLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maximumLength
            ? value
            : value.substring(0, maximumLength);
    }

    public record WebhookWorkItem(
        UUID id,
        String providerId,
        String providerEventId,
        JsonNode rawPayload,
        int attemptCount
    ) {}

    public record DeliveryJobState(
        UUID id,
        UUID orderId,
        UUID chefSubOrderId,
        String providerId,
        String providerDeliveryId,
        String status,
        String providerStatus,
        String trackingUrl,
        Instant lastStatusObservedAt
    ) {}

    public record TrackingWorkItem(
        UUID deliveryJobId,
        UUID orderId,
        UUID chefSubOrderId,
        String providerId,
        String providerDeliveryId,
        int attemptCount
    ) {}
}
