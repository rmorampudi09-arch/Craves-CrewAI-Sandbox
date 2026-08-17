package in.craves.subscription.capacity;

import in.craves.subscription.capacity.CapacityModels.CapacityFreezeRequest;
import in.craves.subscription.capacity.CapacityModels.CapacityIncidentPage;
import in.craves.subscription.capacity.CapacityModels.CapacityReconcileRequest;
import in.craves.subscription.capacity.CapacityModels.ChefCapacitySummary;
import in.craves.subscription.capacity.CapacityModels.DateOverrideResponse;
import in.craves.subscription.capacity.CapacityModels.MenuItemDateOverrideResponse;
import in.craves.subscription.capacity.CapacityModels.MenuItemRuleResponse;
import in.craves.subscription.capacity.CapacityModels.PutDateOverrideRequest;
import in.craves.subscription.capacity.CapacityModels.PutMenuItemDateOverrideRequest;
import in.craves.subscription.capacity.CapacityModels.PutMenuItemRuleRequest;
import in.craves.subscription.capacity.CapacityModels.PutSlotRuleRequest;
import in.craves.subscription.capacity.CapacityModels.SlotRuleResponse;
import in.craves.subscription.security.CurrentUser;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class CapacityController {
    private final CapacityService service;

    public CapacityController(CapacityService service) {
        this.service = service;
    }

    @GetMapping("/chef/subscription-capacity")
    public ChefCapacitySummary getMyCapacity(@AuthenticationPrincipal CurrentUser user) {
        return service.getMySummary(user);
    }

    @PutMapping("/chef/subscription-capacity/rules/slots")
    public SlotRuleResponse putMySlotRule(
        @Valid @RequestBody PutSlotRuleRequest request,
        @AuthenticationPrincipal CurrentUser user
    ) {
        return service.putMySlotRule(request, user);
    }

    @PutMapping("/chef/subscription-capacity/rules/menu-items")
    public MenuItemRuleResponse putMyMenuItemRule(
        @Valid @RequestBody PutMenuItemRuleRequest request,
        @AuthenticationPrincipal CurrentUser user
    ) {
        return service.putMyMenuItemRule(request, user);
    }

    @PutMapping("/chef/subscription-capacity/overrides/slots")
    public DateOverrideResponse putMyDateOverride(
        @Valid @RequestBody PutDateOverrideRequest request,
        @AuthenticationPrincipal CurrentUser user
    ) {
        return service.putMyDateOverride(request, user);
    }

    @PutMapping("/chef/subscription-capacity/overrides/menu-items")
    public MenuItemDateOverrideResponse putMyMenuItemDateOverride(
        @Valid @RequestBody PutMenuItemDateOverrideRequest request,
        @AuthenticationPrincipal CurrentUser user
    ) {
        return service.putMyMenuItemDateOverride(request, user);
    }

    @GetMapping("/admin/subscription-capacity/chefs/{chefIdentityId}")
    public ChefCapacitySummary getChefCapacity(
        @PathVariable UUID chefIdentityId,
        @AuthenticationPrincipal CurrentUser user
    ) {
        return service.adminGetChefSummary(chefIdentityId, user);
    }

    @PatchMapping("/admin/subscription-capacity/chefs/{chefIdentityId}/freeze")
    public ChefCapacitySummary freezeChefCapacity(
        @PathVariable UUID chefIdentityId,
        @Valid @RequestBody CapacityFreezeRequest request,
        @AuthenticationPrincipal CurrentUser user
    ) {
        return service.adminFreezeChef(chefIdentityId, request, user);
    }

    @GetMapping("/admin/subscription-capacity/incidents")
    public CapacityIncidentPage listCapacityIncidents(
        @RequestParam(required = false) UUID chefIdentityId,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) Instant afterCreatedAt,
        @RequestParam(required = false) UUID afterId,
        @RequestParam(defaultValue = "50") int limit,
        @AuthenticationPrincipal CurrentUser user
    ) {
        return service.adminListIncidents(chefIdentityId, status, afterCreatedAt, afterId, limit, user);
    }

    @PostMapping("/admin/subscription-capacity/subscriptions/{subscriptionId}/reconcile")
    public ResponseEntity<Void> reconcileSubscriptionCapacity(
        @PathVariable UUID subscriptionId,
        @Valid @RequestBody CapacityReconcileRequest request,
        @AuthenticationPrincipal CurrentUser user
    ) {
        service.adminReconcileSubscription(subscriptionId, request, user);
        return ResponseEntity.noContent().build();
    }
}
