package in.craves.order.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class NotificationOutboxRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public NotificationOutboxRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void savePending(NotificationOutboxEvent event) {
        savePendingIfAbsent(event);
    }

    public boolean savePendingIfAbsent(NotificationOutboxEvent event) {
        int inserted = jdbcTemplate.update(
            "INSERT INTO order_schema.notification_outbox " +
                "(id, event_key, event_type, aggregate_type, aggregate_id, user_identity_id, user_role, channel, template_code, title, body, target_type, target_id, payload, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, 'PENDING', now(), now()) " +
                "ON CONFLICT (event_key) DO NOTHING",
            UUID.randomUUID(),
            event.eventKey(),
            event.eventType(),
            event.aggregateType(),
            event.aggregateId(),
            event.userIdentityId(),
            event.userRole(),
            event.channel(),
            event.templateCode(),
            event.title(),
            event.body(),
            event.targetType(),
            event.targetId(),
            toJson(event.payload())
        );
        return inserted == 1;
    }

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload == null ? Map.of() : payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Notification outbox payload could not be serialized", ex);
        }
    }
}
