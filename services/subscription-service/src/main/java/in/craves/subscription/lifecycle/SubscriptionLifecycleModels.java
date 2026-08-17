package in.craves.subscription.lifecycle;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class SubscriptionLifecycleModels {
    private SubscriptionLifecycleModels() {
    }

    public record ResumeSubscriptionRequest(
        @NotNull LocalDate resumeDate,
        @Size(max = 1000) String reason
    ) {
    }

    public record SkipSubscriptionDateRequest(
        @NotNull LocalDate serviceDate,
        @Size(max = 1000) String reason
    ) {
    }

    public record OccurrenceItemResponse(
        UUID menuItemId,
        int quantity,
        int sequenceNumber
    ) {
    }

    public record CustomerOccurrenceResponse(
        UUID id,
        LocalDate serviceDate,
        String mealSlotCode,
        Instant serviceAt,
        String status,
        List<OccurrenceItemResponse> items
    ) {
    }

    public record SkipRequestResponse(
        UUID id,
        UUID subscriptionId,
        LocalDate serviceDate,
        String status,
        String reason,
        UUID occurrenceId,
        Instant createdAt,
        Instant appliedAt,
        Instant updatedAt
    ) {
    }

    public record SubscriptionStatusHistoryResponse(
        UUID id,
        String oldStatus,
        String newStatus,
        String reason,
        UUID actorIdentityId,
        Instant createdAt
    ) {
    }

    public record AdminSubscriptionSummary(
        UUID id,
        UUID customerIdentityId,
        UUID planId,
        UUID chefIdentityId,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate nextServiceDate,
        UUID deliveryAddressId,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record AdminSubscriptionPage(
        List<AdminSubscriptionSummary> items,
        Instant nextCreatedAt,
        UUID nextId,
        boolean hasMore
    ) {
    }
}
