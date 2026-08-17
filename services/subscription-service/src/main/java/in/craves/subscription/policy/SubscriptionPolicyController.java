package in.craves.subscription.policy;

import in.craves.subscription.policy.SubscriptionPolicyModels.ActivatePolicyRequest;
import in.craves.subscription.policy.SubscriptionPolicyModels.PolicyReadinessResponse;
import in.craves.subscription.policy.SubscriptionPolicyModels.PublicSubscriptionPolicyResponse;
import in.craves.subscription.policy.SubscriptionPolicyModels.PutPolicyRequest;
import in.craves.subscription.policy.SubscriptionPolicyModels.SubscriptionPolicyResponse;
import in.craves.subscription.security.CurrentUser;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SubscriptionPolicyController {
    private final SubscriptionPolicyService service;

    public SubscriptionPolicyController(SubscriptionPolicyService service) {
        this.service = service;
    }

    @GetMapping("/subscriptions/plans/{planId}/policy")
    public PublicSubscriptionPolicyResponse getPublicPolicy(@PathVariable UUID planId) {
        return service.getPublicActive(planId);
    }

    @GetMapping("/admin/subscription-plans/{planId}/policy")
    public SubscriptionPolicyResponse getLatest(
        @PathVariable UUID planId,
        @AuthenticationPrincipal CurrentUser user
    ) {
        return service.getLatest(planId, user);
    }

    @PutMapping("/admin/subscription-plans/{planId}/policy")
    public SubscriptionPolicyResponse putDraft(
        @PathVariable UUID planId,
        @Valid @RequestBody PutPolicyRequest request,
        @AuthenticationPrincipal CurrentUser user
    ) {
        return service.putDraft(planId, request, user);
    }

    @PostMapping("/admin/subscription-plans/{planId}/policy/activate")
    public SubscriptionPolicyResponse activate(
        @PathVariable UUID planId,
        @Valid @RequestBody ActivatePolicyRequest request,
        @AuthenticationPrincipal CurrentUser user
    ) {
        return service.activate(planId, request, user);
    }

    @GetMapping("/admin/subscription-plans/{planId}/readiness")
    public PolicyReadinessResponse readiness(
        @PathVariable UUID planId,
        @AuthenticationPrincipal CurrentUser user
    ) {
        return service.readiness(planId, user);
    }
}
