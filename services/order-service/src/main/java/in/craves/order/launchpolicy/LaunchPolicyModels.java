package in.craves.order.launchpolicy;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public final class LaunchPolicyModels {
    private LaunchPolicyModels() {
    }

    public record CreateLaunchPolicyRequest(
        @NotBlank @Size(max = 120) String policyName,
        @NotNull @DecimalMin("0.00") BigDecimal minimumOrderAmount,
        @NotNull @Min(1) @Max(100000) Integer maximumServiceabilityRadiusMeters,
        @NotNull @Min(0) @Max(1440) Integer cancellationCutoffMinutes,
        @NotNull @Min(1) @Max(2880) Integer deliverySlaMinutes,
        @NotBlank @Pattern(regexp = "[A-Za-z]{3}") String currency
    ) {
    }

    public record ActivateLaunchPolicyRequest(
        @NotBlank @Size(max = 1000) String reason
    ) {
    }

    public record LaunchPolicyResponse(
        UUID id,
        String policyName,
        BigDecimal minimumOrderAmount,
        int maximumServiceabilityRadiusMeters,
        int cancellationCutoffMinutes,
        int deliverySlaMinutes,
        String currency,
        boolean active,
        UUID createdByIdentityId,
        Instant createdAt,
        Instant activatedAt
    ) {
    }
}
