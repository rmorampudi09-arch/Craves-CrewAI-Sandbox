package in.craves.notification.api;

import java.time.OffsetDateTime;
import java.util.UUID;

public record AppNoticeResponse(
    UUID id,
    String title,
    String body,
    String noticeType,
    String targetType,
    UUID targetId,
    OffsetDateTime readAt,
    OffsetDateTime createdAt
) {}
