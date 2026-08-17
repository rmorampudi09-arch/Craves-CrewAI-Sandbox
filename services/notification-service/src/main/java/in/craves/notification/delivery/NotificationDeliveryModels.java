package in.craves.notification.delivery;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

public final class NotificationDeliveryModels {
    private NotificationDeliveryModels() {
    }

    public record RegisterDeviceRequest(
        @NotBlank @Size(max = 20) String platform,
        @NotBlank @Size(max = 4096) String deviceToken,
        @Size(max = 160) String appInstanceId,
        @Size(max = 40) String appVersion
    ) {
    }

    public record DeviceResponse(
        UUID id,
        String platform,
        String tokenHash,
        String appInstanceId,
        String appVersion,
        boolean active,
        OffsetDateTime lastSeenAt
    ) {
    }

    public record PreferenceRequest(boolean enabled) {
    }

    public record PreferenceResponse(String channel, boolean enabled, OffsetDateTime updatedAt) {
    }

    public record DeliveryWorkItem(
        UUID requestId,
        UUID recipientIdentityId,
        String channel,
        String deliveryAddress,
        String title,
        String body,
        String targetType,
        UUID targetId,
        Map<String, String> payload,
        int priority,
        int attemptCount,
        UUID lockToken
    ) {
    }

    public record PushDevice(UUID id, String deviceToken, String tokenHash) {
    }

    public record DeliveryResult(String provider, String providerMessageId) {
    }
}
