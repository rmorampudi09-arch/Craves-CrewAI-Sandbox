package in.craves.subscription.web;

import in.craves.subscription.exception.ApiException;
import in.craves.subscription.lifecycle.SubscriptionLifecycleModels.AdminSubscriptionPage;
import in.craves.subscription.lifecycle.SubscriptionLifecycleModels.CustomerOccurrenceResponse;
import in.craves.subscription.lifecycle.SubscriptionLifecycleModels.ResumeSubscriptionRequest;
import in.craves.subscription.lifecycle.SubscriptionLifecycleModels.SkipRequestResponse;
import in.craves.subscription.lifecycle.SubscriptionLifecycleModels.SkipSubscriptionDateRequest;
import in.craves.subscription.lifecycle.SubscriptionLifecycleModels.SubscriptionStatusHistoryResponse;
import in.craves.subscription.lifecycle.SubscriptionLifecycleService;
import in.craves.subscription.security.CurrentUser;
import in.craves.subscription.service.SubscriptionService;
import in.craves.subscription.web.ApiDtos.CreateSubscriptionRequest;
import in.craves.subscription.web.ApiDtos.CustomerSubscriptionResponse;
import in.craves.subscription.web.ApiDtos.PlanResponse;
import in.craves.subscription.web.ApiDtos.PublicPlanResponse;
import in.craves.subscription.web.ApiDtos.SubscriptionResponse;
import in.craves.subscription.web.ApiDtos.SubscriptionStateChangeRequest;
import in.craves.subscription.web.ApiDtos.UpdatePlanStatusRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class SubscriptionController {
    private final SubscriptionService service;
    private final SubscriptionLifecycleService lifecycleService;

    public SubscriptionController(
        SubscriptionService service,
        SubscriptionLifecycleService lifecycleService
    ) {
        this.service = service;
        this.lifecycleService = lifecycleService;
    }

    @GetMapping("/subscriptions/plans")
    public List<PublicPlanResponse> listActivePlans() {
        return service.listActivePlans();
    }

    @GetMapping("/subscriptions/plans/{planId}")
    public PublicPlanResponse getPlan(@PathVariable UUID planId) {
        return service.getPlan(planId);
    }

    @GetMapping("/admin/subscription-plans")
    public List<PlanResponse> listAllPlans(@AuthenticationPrincipal CurrentUser user) {
        return service.listAllPlans(user);
    }

    @PatchMapping("/admin/subscription-plans/{planId}/status")
    public PlanResponse updatePlanStatus(
        @PathVariable UUID planId,
        @Valid @RequestBody UpdatePlanStatusRequest request,
        @AuthenticationPrincipal CurrentUser user
    ) {
        if (!"INACTIVE".equalsIgnoreCase(request.status())) {
            throw ApiException.badRequest(
                "PLAN_REVIEW_REQUIRED",
                "Administrators approve or reject submitted meal plans through the review action; direct activation is not allowed"
            );
        }
        return service.updatePlanStatus(planId, "INACTIVE", user);
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<CustomerSubscriptionResponse> createSubscription(
        @Valid @RequestBody CreateSubscriptionRequest request,
        @RequestHeader("Idempotency-Key") String idempotencyKey,
        @AuthenticationPrincipal CurrentUser user
    ) {
        CustomerSubscriptionResponse response = service.createSubscription(request, idempotencyKey, user);
        return ResponseEntity.created(URI.create("/api/v1/subscriptions/" + response.id())).body(response);
    }

    @GetMapping("/subscriptions")
    public List<CustomerSubscriptionResponse> listMySubscriptions(@AuthenticationPrincipal CurrentUser user) {
        return service.listMine(user);
    }

    @GetMapping("/subscriptions/{subscriptionId}")
    public CustomerSubscriptionResponse getSubscription(
        @PathVariable UUID subscriptionId,
        @AuthenticationPrincipal CurrentUser user
    ) {
        return service.getMine(subscriptionId, user);
    }

    @GetMapping("/subscriptions/{subscriptionId}/occurrences")
    public List<CustomerOccurrenceResponse> listOccurrences(
        @PathVariable UUID subscriptionId,
        @RequestParam(defaultValue = "100") int limit,
        @AuthenticationPrincipal CurrentUser user
    ) {
        return lifecycleService.listOccurrences(subscriptionId, limit, user);
    }

    @PatchMapping("/subscriptions/{subscriptionId}/pause")
    public CustomerSubscriptionResponse pauseSubscription(
        @PathVariable UUID subscriptionId,
        @RequestBody(required = false) SubscriptionStateChangeRequest request,
        @AuthenticationPrincipal CurrentUser user
    ) {
        return lifecycleService.pause(subscriptionId, reason(request), user);
    }

    @PatchMapping("/subscriptions/{subscriptionId}/resume")
    public CustomerSubscriptionResponse resumeSubscription(
        @PathVariable UUID subscriptionId,
        @Valid @RequestBody ResumeSubscriptionRequest request,
        @AuthenticationPrincipal CurrentUser user
    ) {
        return lifecycleService.resume(subscriptionId, request, user);
    }

    @PatchMapping("/subscriptions/{subscriptionId}/cancel")
    public CustomerSubscriptionResponse cancelSubscription(
        @PathVariable UUID subscriptionId,
        @RequestBody(required = false) SubscriptionStateChangeRequest request,
        @AuthenticationPrincipal CurrentUser user
    ) {
        return lifecycleService.cancel(subscriptionId, reason(request), user);
    }

    @PostMapping("/subscriptions/{subscriptionId}/skips")
    public SkipRequestResponse skipSubscriptionDate(
        @PathVariable UUID subscriptionId,
        @Valid @RequestBody SkipSubscriptionDateRequest request,
        @AuthenticationPrincipal CurrentUser user
    ) {
        return lifecycleService.skip(subscriptionId, request, user);
    }

    @GetMapping("/admin/subscriptions")
    public AdminSubscriptionPage listAdminSubscriptions(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) UUID planId,
        @RequestParam(required = false) Instant afterCreatedAt,
        @RequestParam(required = false) UUID afterId,
        @RequestParam(defaultValue = "50") int limit,
        @AuthenticationPrincipal CurrentUser user
    ) {
        return lifecycleService.listAdmin(status, planId, afterCreatedAt, afterId, limit, user);
    }

    @GetMapping("/admin/subscriptions/{subscriptionId}/history")
    public List<SubscriptionStatusHistoryResponse> adminSubscriptionHistory(
        @PathVariable UUID subscriptionId,
        @RequestParam(defaultValue = "100") int limit,
        @AuthenticationPrincipal CurrentUser user
    ) {
        return lifecycleService.history(subscriptionId, limit, user);
    }

    @PatchMapping("/admin/subscriptions/{subscriptionId}/status/{status}")
    public SubscriptionResponse adminChangeStatus(
        @PathVariable UUID subscriptionId,
        @PathVariable String status,
        @RequestBody(required = false) SubscriptionStateChangeRequest request,
        @AuthenticationPrincipal CurrentUser user
    ) {
        return service.adminChangeStatus(subscriptionId, status, reason(request), user);
    }

    private static String reason(SubscriptionStateChangeRequest request) {
        return request == null ? null : request.reason();
    }
}
