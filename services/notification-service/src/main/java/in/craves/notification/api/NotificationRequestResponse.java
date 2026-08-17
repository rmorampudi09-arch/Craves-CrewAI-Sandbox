package in.craves.notification.api;

import in.craves.notification.domain.NotificationChannel;
import in.craves.notification.domain.NotificationStatus;
import java.time.OffsetDateTime;
import java.util.UUID;

public record NotificationRequestResponse(
    UUID id,
    String requestKey,
    UUID userId,
    NotificationChannel channel,
    NotificationStatus status,
    String title,
    String body,
    String targetType,
    UUID targetId,
    OffsetDateTime createdAt,
    OffsetDateTime updatedAt
) {}
