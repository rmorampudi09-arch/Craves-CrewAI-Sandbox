package in.craves.userchef.service;

import java.util.Map;
import java.util.UUID;

public record ChefNoticeOutboxEvent(
    String eventKey,
    String eventType,
    String aggregateType,
    UUID aggregateId,
    UUID userIdentityId,
    String userRole,
    String channel,
    String templateCode,
    String title,
    String body,
    String targetType,
    UUID targetId,
    Map<String, Object> payload
) {
}
