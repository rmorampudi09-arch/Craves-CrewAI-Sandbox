package in.craves.notification.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.notification.api.AppNoticeResponse;
import in.craves.notification.api.CreateNotificationRequest;
import in.craves.notification.api.NotificationRequestResponse;
import in.craves.notification.domain.NotificationChannel;
import in.craves.notification.domain.NotificationStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationRepository {
    private final NamedParameterJdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public NotificationRepository(NamedParameterJdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Optional<NotificationRequestResponse> findByRequestKey(String requestKey) {
        List<NotificationRequestResponse> rows = jdbc.query("""
            SELECT id, request_key, recipient_identity_id, channel, status, title, body, target_type, target_id, created_at, updated_at
              FROM notification_schema.notification_request
             WHERE request_key = :requestKey
            """, Map.of("requestKey", requestKey), this::mapRequest);
        return rows.stream().findFirst();
    }

    public NotificationRequestResponse insertRequest(UUID id, CreateNotificationRequest request, NotificationStatus status) {
        MapSqlParameterSource p = new MapSqlParameterSource()
            .addValue("id", id)
            .addValue("requestKey", request.requestKey())
            .addValue("sourceService", required(request.sourceService(), "sourceService"))
            .addValue("eventType", required(request.eventType(), "eventType"))
            .addValue("userId", required(request.userId(), "userId"))
            .addValue("userRole", request.userRole())
            .addValue("channel", required(request.channel(), "channel").name())
            .addValue("templateCode", request.templateCode())
            .addValue("address", request.address())
            .addValue("title", titleOrFallback(request))
            .addValue("body", required(request.body(), "body"))
            .addValue("targetType", request.targetType())
            .addValue("targetId", request.targetId())
            .addValue("payload", payloadJson(request.payload()))
            .addValue("priority", request.priority() == null ? 5 : request.priority())
            .addValue("status", status.name());

        jdbc.update("""
            INSERT INTO notification_schema.notification_request(
                id, request_key, source_service, event_type, recipient_identity_id, recipient_role,
                channel, template_code, delivery_address, title, body, target_type, target_id,
                payload, priority, status, created_at, updated_at)
            VALUES (
                :id, :requestKey, :sourceService, :eventType, :userId, :userRole,
                :channel, :templateCode, :address, :title, :body, :targetType, :targetId,
                CAST(:payload AS jsonb), :priority, :status, now(), now())
            """, p);
        return findByRequestKey(request.requestKey()).orElseThrow();
    }

    public void createAppNotice(UUID requestId, CreateNotificationRequest request) {
        MapSqlParameterSource p = new MapSqlParameterSource()
            .addValue("id", UUID.randomUUID())
            .addValue("requestId", requestId)
            .addValue("userId", request.userId())
            .addValue("userRole", request.userRole())
            .addValue("title", titleOrFallback(request))
            .addValue("body", request.body())
            .addValue("type", request.eventType())
            .addValue("targetType", request.targetType())
            .addValue("targetId", request.targetId());
        jdbc.update("""
            INSERT INTO notification_schema.in_app_notification(
                id, request_id, recipient_identity_id, recipient_role, title, body,
                notification_type, target_type, target_id, created_at)
            VALUES (:id, :requestId, :userId, :userRole, :title, :body, :type, :targetType, :targetId, now())
            """, p);
    }

    public void insertAttempt(UUID requestId, NotificationChannel channel, String provider, int attemptNumber, NotificationStatus status, String error) {
        MapSqlParameterSource p = new MapSqlParameterSource()
            .addValue("id", UUID.randomUUID())
            .addValue("requestId", requestId)
            .addValue("channel", channel.name())
            .addValue("provider", provider)
            .addValue("attemptNumber", attemptNumber)
            .addValue("status", status.name())
            .addValue("error", error);
        jdbc.update("""
            INSERT INTO notification_schema.notification_delivery_attempt(
                id, request_id, channel, provider, attempt_number, status, error_message, started_at, completed_at)
            VALUES (:id, :requestId, :channel, :provider, :attemptNumber, :status, :error, now(), now())
            """, p);
    }

    public void updateRequestStatus(UUID requestId, NotificationStatus status, String error) {
        jdbc.update("""
            UPDATE notification_schema.notification_request
               SET status = :status, last_error = :error, attempt_count = attempt_count + 1, updated_at = now()
             WHERE id = :id
            """, new MapSqlParameterSource().addValue("id", requestId).addValue("status", status.name()).addValue("error", error));
    }

    public List<AppNoticeResponse> findAppNotices(UUID userId, int limit) {
        return jdbc.query("""
            SELECT id, title, body, notification_type, target_type, target_id, read_at, created_at
              FROM notification_schema.in_app_notification
             WHERE recipient_identity_id = :userId
             ORDER BY created_at DESC
             LIMIT :limit
            """, new MapSqlParameterSource().addValue("userId", userId).addValue("limit", Math.min(Math.max(limit, 1), 100)), this::mapNotice);
    }

    public void markRead(UUID userId, UUID noticeId) {
        jdbc.update("""
            UPDATE notification_schema.in_app_notification
               SET read_at = COALESCE(read_at, now())
             WHERE id = :noticeId AND recipient_identity_id = :userId
            """, new MapSqlParameterSource().addValue("noticeId", noticeId).addValue("userId", userId));
    }

    private NotificationRequestResponse mapRequest(ResultSet rs, int rowNum) throws SQLException {
        return new NotificationRequestResponse(
            rs.getObject("id", UUID.class),
            rs.getString("request_key"),
            rs.getObject("recipient_identity_id", UUID.class),
            NotificationChannel.valueOf(rs.getString("channel")),
            NotificationStatus.valueOf(rs.getString("status")),
            rs.getString("title"),
            rs.getString("body"),
            rs.getString("target_type"),
            rs.getObject("target_id", UUID.class),
            rs.getObject("created_at", OffsetDateTime.class),
            rs.getObject("updated_at", OffsetDateTime.class)
        );
    }

    private AppNoticeResponse mapNotice(ResultSet rs, int rowNum) throws SQLException {
        return new AppNoticeResponse(
            rs.getObject("id", UUID.class),
            rs.getString("title"),
            rs.getString("body"),
            rs.getString("notification_type"),
            rs.getString("target_type"),
            rs.getObject("target_id", UUID.class),
            rs.getObject("read_at", OffsetDateTime.class),
            rs.getObject("created_at", OffsetDateTime.class)
        );
    }

    private String payloadJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Payload is not valid JSON", ex);
        }
    }

    private static String titleOrFallback(CreateNotificationRequest request) {
        return request.title() == null || request.title().isBlank() ? request.eventType() : request.title();
    }

    private static <T> T required(T value, String name) {
        if (value == null || (value instanceof String s && s.isBlank())) {
            throw new IllegalArgumentException(name + " is required");
        }
        return value;
    }
}
