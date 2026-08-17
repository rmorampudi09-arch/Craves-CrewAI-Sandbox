package in.craves.order.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DeliveryStatusDtos {
    private DeliveryStatusDtos() {
    }

    public record DeliveryStatusResponse(
        UUID orderId,
        UUID deliveryJobId,
        String providerId,
        String status,
        String trackingUrl,
        Instant observedAt,
        List<DeliveryStatusHistoryResponse> history
    ) {
    }

    public record DeliveryStatusHistoryResponse(
        String oldStatus,
        String newStatus,
        String trackingUrl,
        Instant observedAt,
        Instant recordedAt
    ) {
    }
}
