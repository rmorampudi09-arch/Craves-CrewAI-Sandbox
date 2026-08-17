package in.craves.subscription.policy;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

public final class SubscriptionPolicyModels {
    private SubscriptionPolicyModels() {
    }

    public record PutPolicyRequest(
        boolean customerPauseEnabled,
        boolean customerResumeEnabled,
        boolean customerCancelEnabled,
        boolean customerSkipEnabled,
        @PositiveOrZero Integer pauseCutoffMinutes,
        @PositiveOrZero Integer resumeLeadMinutes,
        @PositiveOrZero Integer cancelCutoffMinutes,
        @PositiveOrZero Integer skipCutoffMinutes,
        @Size(max = 200) String holidayPolicyReference,
        @Size(max = 200) String unusedMealPolicyReference,
        @Size(max = 200) String refundPolicyReference,
        @Size(max = 4000) String notes
    ) {
    }

    public record ActivatePolicyRequest(
        @NotBlank @Size(max = 1000) String reason
    ) {
    }

    public record SubscriptionPolicyResponse(
        UUID id,
        UUID planId,
        int version,
        String status,
        boolean customerPauseEnabled,
        boolean customerResumeEnabled,
        boolean customerCancelEnabled,
        boolean customerSkipEnabled,
        Integer pauseCutoffMinutes,
        Integer resumeLeadMinutes,
        Integer cancelCutoffMinutes,
        Integer skipCutoffMinutes,
        String holidayPolicyReference,
        String unusedMealPolicyReference,
        String refundPolicyReference,
        String notes,
        Instant createdAt,
        Instant updatedAt,
        Instant activatedAt
    ) {
    }

    public record PublicSubscriptionPolicyResponse(
        boolean customerPauseEnabled,
        boolean customerResumeEnabled,
        boolean customerCancelEnabled,
        boolean customerSkipEnabled,
        Integer pauseCutoffMinutes,
        Integer resumeLeadMinutes,
        Integer cancelCutoffMinutes,
        Integer skipCutoffMinutes,
        String holidayPolicyReference,
        String unusedMealPolicyReference,
        String refundPolicyReference
    ) {
    }

    public record PolicyReadinessResponse(
        @NotNull UUID planId,
        boolean activeSchedule,
        boolean activePolicy,
        boolean chefAssigned,
        boolean readyForActivation
    ) {
    }
}
