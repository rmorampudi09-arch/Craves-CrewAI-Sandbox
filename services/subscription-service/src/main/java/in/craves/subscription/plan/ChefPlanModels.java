package in.craves.subscription.plan;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class ChefPlanModels {
    private ChefPlanModels() {
    }

    public record ChefPlanInput(
        @NotBlank @Size(max = 160) String name,
        @Size(max = 2000) String description,
        @NotBlank String billingPeriod,
        @NotNull @DecimalMin("0.00") BigDecimal amount,
        @Size(min = 3, max = 3) String currency
    ) {
    }

    public record SubmitChefPlanRequest(
        @Size(max = 1000) String note
    ) {
    }

    public record ReviewChefPlanRequest(
        @NotBlank String decision,
        @NotBlank @Size(min = 3, max = 1000) String reason
    ) {
    }

    public record ChefPlanResponse(
        UUID id,
        String planCode,
        String name,
        String description,
        String billingPeriod,
        BigDecimal amount,
        String currency,
        String status,
        String reviewReason,
        Instant submittedAt,
        Instant reviewedAt,
        Instant createdAt,
        Instant updatedAt
    ) {
    }
}
