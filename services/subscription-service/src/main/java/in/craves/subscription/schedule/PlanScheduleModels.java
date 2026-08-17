package in.craves.subscription.schedule;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

public final class PlanScheduleModels {
    private PlanScheduleModels() {
    }

    public record ScheduleItemRequest(
        @NotNull UUID menuItemId,
        @NotNull @Min(1) @Max(100) Integer quantity,
        @Min(1) @Max(7) Integer isoDayOfWeek,
        @Min(1) @Max(28) Integer dayOfMonth,
        @NotBlank @Size(max = 40) @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{0,39}$") String mealSlotCode,
        @NotNull LocalTime serviceTime,
        @NotNull @Min(1) @Max(100) Integer sequenceNumber
    ) {
    }

    public record PutScheduleRequest(
        @NotBlank String recurrenceType,
        @NotBlank @Size(max = 80) String timezone,
        @NotNull @Min(1) @Max(168) Integer generationLeadHours,
        @NotEmpty @Size(max = 100) List<@Valid ScheduleItemRequest> items
    ) {
    }

    public record ActivateScheduleRequest(
        @NotBlank @Size(max = 1000) String reason
    ) {
    }

    public record ScheduleItemResponse(
        UUID id,
        UUID menuItemId,
        String menuItemName,
        String menuItemCategory,
        String menuItemFoodType,
        BigDecimal menuItemPrice,
        String menuItemCurrency,
        int quantity,
        Integer isoDayOfWeek,
        Integer dayOfMonth,
        String mealSlotCode,
        LocalTime serviceTime,
        int sequenceNumber
    ) {
    }

    public record PlanScheduleResponse(
        UUID planId,
        String recurrenceType,
        String timezone,
        LocalTime serviceTime,
        int generationLeadHours,
        String status,
        int version,
        List<ScheduleItemResponse> items,
        Instant createdAt,
        Instant updatedAt,
        Instant activatedAt
    ) {
    }

    public record PublicScheduleItemResponse(
        UUID menuItemId,
        String menuItemName,
        String menuItemCategory,
        String menuItemFoodType,
        BigDecimal menuItemPrice,
        String menuItemCurrency,
        int quantity,
        Integer isoDayOfWeek,
        Integer dayOfMonth,
        String mealSlotCode,
        LocalTime serviceTime,
        int sequenceNumber
    ) {
    }

    public record PublicPlanScheduleResponse(
        UUID planId,
        String recurrenceType,
        String timezone,
        List<PublicScheduleItemResponse> items
    ) {
    }
}
