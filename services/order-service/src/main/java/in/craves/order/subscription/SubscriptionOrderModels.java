package in.craves.order.subscription;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class SubscriptionOrderModels {
    public static final String EVENT_TYPE = "SUBSCRIPTION_ORDER_REQUESTED";

    private SubscriptionOrderModels() {
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

    public record RequestedItem(UUID menuItemId, int quantity, int sequenceNumber) {
    }

    public record RequestedData(
        UUID occurrenceId,
        UUID subscriptionId,
        UUID planId,
        UUID customerIdentityId,
        UUID chefIdentityId,
        UUID deliveryAddressId,
        Instant scheduledServiceAt,
        List<RequestedItem> items
    ) {
    }

    public record CreatedResult(UUID occurrenceId, UUID orderId, boolean created) {
    }

    public record CallbackRecord(
        UUID id,
        UUID occurrenceId,
        UUID orderId,
        int attemptCount,
        UUID lockToken
    ) {
    }
}
