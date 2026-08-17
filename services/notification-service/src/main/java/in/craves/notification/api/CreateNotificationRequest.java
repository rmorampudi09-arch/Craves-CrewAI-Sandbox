package in.craves.notification.api;

import in.craves.notification.domain.NotificationChannel;
import java.util.Map;
import java.util.UUID;

public record CreateNotificationRequest(
    String requestKey,
    String sourceService,
    String eventType,
    UUID userId,
    String userRole,
    NotificationChannel channel,
    String templateCode,
    String address,
    String title,
    String body,
    String targetType,
    UUID targetId,
    Map<String, Object> payload,
    Integer priority
) {}
