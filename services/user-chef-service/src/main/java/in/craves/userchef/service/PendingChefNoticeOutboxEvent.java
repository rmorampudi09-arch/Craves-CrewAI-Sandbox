package in.craves.userchef.service;

import java.util.Map;
import java.util.UUID;

public record PendingChefNoticeOutboxEvent(
    UUID id,
    String eventKey,
    String eventType,
    UUID userIdentityId,
    String userRole,
    String channel,
    String templateCode,
    String title,
    String body,
    String targetType,
    UUID targetId,
    Map<String, Object> payload,
    int attemptCount
) {
}
