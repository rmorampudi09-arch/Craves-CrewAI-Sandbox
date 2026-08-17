package in.craves.subscription.capacity;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class CapacityModels {
    private CapacityModels() {
    }

    public record PutSlotRuleRequest(
        @NotNull @Min(1) @Max(7) Integer isoDayOfWeek,
        @NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{0,39}$") String mealSlotCode,
        @NotNull @Min(0) @Max(100000) Integer totalCapacityUnits,
        @NotNull @Min(0) @Max(100000) Integer subscriptionCapacityUnits,
        @NotNull Boolean salesEnabled,
        @NotBlank @Size(max = 1000) String reason
    ) {
    }

    public record PutMenuItemRuleRequest(
        @NotNull UUID menuItemId,
        @NotNull @Min(1) @Max(7) Integer isoDayOfWeek,
        @NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{0,39}$") String mealSlotCode,
        @NotNull @Min(0) @Max(100000) Integer maxSubscriptionUnits,
        @NotNull Boolean salesEnabled,
        @NotBlank @Size(max = 1000) String reason
    ) {
    }

    public record PutDateOverrideRequest(
        @NotNull LocalDate serviceDate,
        @NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{0,39}$") String mealSlotCode,
        @NotNull @Min(0) @Max(100000) Integer totalCapacityUnits,
        @NotNull @Min(0) @Max(100000) Integer subscriptionCapacityUnits,
        @NotNull Boolean closed,
        @NotBlank @Size(max = 1000) String reason
    ) {
    }

    public record PutMenuItemDateOverrideRequest(
        @NotNull UUID menuItemId,
        @NotNull LocalDate serviceDate,
        @NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{0,39}$") String mealSlotCode,
        @NotNull @Min(0) @Max(100000) Integer maxSubscriptionUnits,
        @NotNull Boolean closed,
        @NotBlank @Size(max = 1000) String reason
    ) {
    }

    public record CapacityFreezeRequest(
        @NotNull Boolean frozen,
        @NotBlank @Size(max = 1000) String reason
    ) {
    }

    public record CapacityReconcileRequest(
        @NotBlank @Size(max = 1000) String reason
    ) {
    }

    public record SlotRuleResponse(
        UUID id,
        UUID chefIdentityId,
        int isoDayOfWeek,
        String mealSlotCode,
        int totalCapacityUnits,
        int subscriptionCapacityUnits,
        boolean salesEnabled,
        int recurringReservedUnits,
        int recurringAvailableUnits,
        int recurringDeficitUnits,
        int version,
        Instant updatedAt
    ) {
    }

    public record MenuItemRuleResponse(
        UUID id,
        UUID chefIdentityId,
        UUID menuItemId,
        int isoDayOfWeek,
        String mealSlotCode,
        int maxSubscriptionUnits,
        boolean salesEnabled,
        int recurringReservedUnits,
        int recurringAvailableUnits,
        int recurringDeficitUnits,
        int version,
        Instant updatedAt
    ) {
    }

    public record DateOverrideResponse(
        UUID id,
        UUID chefIdentityId,
        LocalDate serviceDate,
        String mealSlotCode,
        int totalCapacityUnits,
        int subscriptionCapacityUnits,
        boolean closed,
        String reason,
        int heldUnits,
        int committedUnits,
        int deficitUnits,
        Instant updatedAt
    ) {
    }

    public record MenuItemDateOverrideResponse(
        UUID id,
        UUID chefIdentityId,
        UUID menuItemId,
        LocalDate serviceDate,
        String mealSlotCode,
        int maxSubscriptionUnits,
        boolean closed,
        String reason,
        int heldUnits,
        int committedUnits,
        int deficitUnits,
        Instant updatedAt
    ) {
    }

    public record ChefCapacitySummary(
        UUID chefIdentityId,
        boolean adminSalesFrozen,
        String freezeReason,
        List<SlotRuleResponse> slotRules,
        List<MenuItemRuleResponse> menuItemRules,
        List<DateOverrideResponse> dateOverrides,
        List<MenuItemDateOverrideResponse> menuItemDateOverrides,
        long openIncidentCount
    ) {
    }

    public record CapacityIncidentResponse(
        UUID id,
        UUID chefIdentityId,
        LocalDate serviceDate,
        Integer isoDayOfWeek,
        String mealSlotCode,
        UUID menuItemId,
        String incidentType,
        String severity,
        String status,
        int reservedUnits,
        int capacityUnits,
        String reason,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt
    ) {
    }

    public record CapacityIncidentPage(
        List<CapacityIncidentResponse> items,
        Instant nextCreatedAt,
        UUID nextId,
        boolean hasMore
    ) {
    }

    public record CapacityAvailability(
        UUID planId,
        boolean bookable,
        String reasonCode
    ) {
    }
}
