package in.craves.subscription.order;

import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OccurrenceOrderModels {
    public static final String EVENT_TYPE = "SUBSCRIPTION_ORDER_REQUESTED";

    private OccurrenceOrderModels() {
    }

    public record OrderItem(UUID menuItemId, int quantity, int sequenceNumber) {
    }

    public record OrderRequestedData(
        UUID occurrenceId,
        UUID subscriptionId,
        UUID planId,
        UUID customerIdentityId,
        UUID chefIdentityId,
        UUID deliveryAddressId,
        Instant scheduledServiceAt,
        List<OrderItem> items
    ) {
    }

    public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        String eventVersion,
        Instant occurredAt,
        UUID correlationId,
        UUID causationId,
        UUID subject,
        T data
    ) {
    }

    public record OrderCreatedRequest(@NotNull UUID orderId) {
    }
}
