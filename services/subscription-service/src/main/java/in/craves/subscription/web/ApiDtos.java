package in.craves.subscription.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public final class ApiDtos {
    private ApiDtos() {
    }

    public record CreatePlanRequest(
        @NotBlank @Size(max = 80) String planCode,
        UUID chefIdentityId,
        @NotBlank @Size(max = 160) String name,
        @Size(max = 2000) String description,
        @NotBlank String billingPeriod,
        @NotNull @DecimalMin("0.00") BigDecimal amount,
        @Size(min = 3, max = 3) String currency
    ) {
    }

    public record UpdatePlanStatusRequest(@NotBlank String status) {
    }

    public record PlanResponse(
        UUID id,
        String planCode,
        UUID chefIdentityId,
        String name,
        String description,
        String billingPeriod,
        BigDecimal amount,
        String currency,
        String status,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record PublicPlanResponse(
        UUID id,
        String planCode,
        String name,
        String description,
        String billingPeriod,
        BigDecimal amount,
        String currency
    ) {
    }

    public record CreateSubscriptionRequest(
        @NotNull UUID planId,
        @NotNull LocalDate startDate,
        @NotNull UUID deliveryAddressId,
        @Size(max = 2000) String notes
    ) {
    }

    public record SubscriptionStateChangeRequest(@Size(max = 1000) String reason) {
    }

    public record SubscriptionResponse(
        UUID id,
        UUID customerIdentityId,
        UUID planId,
        UUID chefIdentityId,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate nextServiceDate,
        UUID deliveryAddressId,
        String notes,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record CustomerSubscriptionResponse(
        UUID id,
        UUID planId,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        LocalDate nextServiceDate,
        UUID deliveryAddressId,
        String notes,
        Instant createdAt,
        Instant updatedAt
    ) {
    }
}
