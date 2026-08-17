package in.craves.subscription.policy;

import in.craves.subscription.exception.ApiException;
import in.craves.subscription.policy.SubscriptionPolicyModels.ActivatePolicyRequest;
import in.craves.subscription.policy.SubscriptionPolicyModels.PolicyReadinessResponse;
import in.craves.subscription.policy.SubscriptionPolicyModels.PublicSubscriptionPolicyResponse;
import in.craves.subscription.policy.SubscriptionPolicyModels.PutPolicyRequest;
import in.craves.subscription.policy.SubscriptionPolicyModels.SubscriptionPolicyResponse;
import in.craves.subscription.security.CurrentUser;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class SubscriptionPolicyService {
    private final SubscriptionPolicyRepository repository;

    public SubscriptionPolicyService(SubscriptionPolicyRepository repository) {
        this.repository = repository;
    }

    public SubscriptionPolicyResponse getLatest(UUID planId, CurrentUser user) {
        requireAdmin(user);
        return repository.findLatest(planId)
            .orElseThrow(() -> ApiException.notFound("SUBSCRIPTION_POLICY_NOT_FOUND", "Subscription plan policy was not found"));
    }

    public PublicSubscriptionPolicyResponse getPublicActive(UUID planId) {
        SubscriptionPolicyResponse policy = repository.findPublicActive(planId)
            .orElseThrow(() -> ApiException.notFound("SUBSCRIPTION_POLICY_NOT_FOUND", "Active subscription policy was not found"));
        return toPublic(policy);
    }

    public SubscriptionPolicyResponse putDraft(UUID planId, PutPolicyRequest request, CurrentUser user) {
        requireAdmin(user);
        validate(request);
        try {
            return repository.putDraft(planId, request, user.identityId());
        } catch (IllegalArgumentException exception) {
            throw ApiException.notFound("PLAN_NOT_FOUND", "Subscription plan was not found");
        }
    }

    public SubscriptionPolicyResponse activate(
        UUID planId,
        ActivatePolicyRequest request,
        CurrentUser user
    ) {
        requireAdmin(user);
        try {
            return repository.activate(planId, user.identityId(), request.reason().trim());
        } catch (IllegalArgumentException exception) {
            throw ApiException.notFound("PLAN_NOT_FOUND", "Subscription plan was not found");
        } catch (IllegalStateException exception) {
            throw ApiException.conflict("SUBSCRIPTION_POLICY_NOT_DRAFT", exception.getMessage());
        }
    }

    public PolicyReadinessResponse readiness(UUID planId, CurrentUser user) {
        requireAdmin(user);
        try {
            return repository.readiness(planId);
        } catch (IllegalArgumentException exception) {
            throw ApiException.notFound("PLAN_NOT_FOUND", "Subscription plan was not found");
        }
    }

    private static void validate(PutPolicyRequest request) {
        requireCutoff(request.customerPauseEnabled(), request.pauseCutoffMinutes(), "pauseCutoffMinutes");
        requireCutoff(request.customerResumeEnabled(), request.resumeLeadMinutes(), "resumeLeadMinutes");
        requireCutoff(request.customerCancelEnabled(), request.cancelCutoffMinutes(), "cancelCutoffMinutes");
        requireCutoff(request.customerSkipEnabled(), request.skipCutoffMinutes(), "skipCutoffMinutes");
        validateOptionalReference(request.holidayPolicyReference(), "holidayPolicyReference");
        validateOptionalReference(request.unusedMealPolicyReference(), "unusedMealPolicyReference");
        validateOptionalReference(request.refundPolicyReference(), "refundPolicyReference");
    }

    private static void requireCutoff(boolean enabled, Integer value, String field) {
        if (enabled && value == null) {
            throw ApiException.badRequest("SUBSCRIPTION_POLICY_INCOMPLETE", field + " is required when the action is enabled");
        }
        if (value != null && value < 0) {
            throw ApiException.badRequest("SUBSCRIPTION_POLICY_INVALID", field + " cannot be negative");
        }
    }

    private static void validateOptionalReference(String value, String field) {
        if (value != null && !StringUtils.hasText(value)) {
            throw ApiException.badRequest("SUBSCRIPTION_POLICY_INVALID", field + " cannot be blank when supplied");
        }
    }

    private static PublicSubscriptionPolicyResponse toPublic(SubscriptionPolicyResponse policy) {
        return new PublicSubscriptionPolicyResponse(
            policy.customerPauseEnabled(),
            policy.customerResumeEnabled(),
            policy.customerCancelEnabled(),
            policy.customerSkipEnabled(),
            policy.pauseCutoffMinutes(),
            policy.resumeLeadMinutes(),
            policy.cancelCutoffMinutes(),
            policy.skipCutoffMinutes(),
            policy.holidayPolicyReference(),
            policy.unusedMealPolicyReference(),
            policy.refundPolicyReference()
        );
    }

    private static void requireAdmin(CurrentUser user) {
        if (user == null || !user.hasAnyRole("PLATFORM_ADMIN", "SUBSCRIPTION_ADMIN")) {
            throw ApiException.forbidden("ROLE_NOT_ALLOWED", "Subscription administration role is required");
        }
    }
}
