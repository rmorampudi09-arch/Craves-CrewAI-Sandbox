package in.craves.subscription.capacity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import in.craves.subscription.capacity.CapacityRepository.CapacityControl;
import in.craves.subscription.capacity.CapacityRepository.DateOverrideRow;
import in.craves.subscription.capacity.CapacityRepository.EntitlementRow;
import in.craves.subscription.capacity.CapacityRepository.MenuDateOverrideRow;
import in.craves.subscription.capacity.CapacityRepository.MenuRuleRow;
import in.craves.subscription.capacity.CapacityRepository.SlotRuleRow;
import in.craves.subscription.capacity.CapacityRepository.SubscriptionProjectionCandidate;
import in.craves.subscription.exception.ApiException;
import in.craves.subscription.repository.SubscriptionRepository;
import in.craves.subscription.schedule.PlanCatalogClient;
import in.craves.subscription.schedule.PlanScheduleModels.PlanScheduleResponse;
import in.craves.subscription.schedule.PlanScheduleModels.ScheduleItemResponse;
import in.craves.subscription.schedule.PlanScheduleRepository;
import in.craves.subscription.security.CurrentUser;
import in.craves.subscription.web.ApiDtos.PlanResponse;
import in.craves.subscription.web.ApiDtos.SubscriptionResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CapacityService {
    private final CapacityRepository repository;
    private final PlanScheduleRepository scheduleRepository;
    private final PlanCatalogClient catalogClient;
    private final SubscriptionRepository subscriptionRepository;
    private final CapacityProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public CapacityService(
        CapacityRepository repository,
        PlanScheduleRepository scheduleRepository,
        PlanCatalogClient catalogClient,
        SubscriptionRepository subscriptionRepository,
        CapacityProperties properties,
        ObjectMapper objectMapper
    ) {
        this(repository, scheduleRepository, catalogClient, subscriptionRepository, properties, objectMapper, Clock.systemUTC());
    }

    CapacityService(
        CapacityRepository repository,
        PlanScheduleRepository scheduleRepository,
        PlanCatalogClient catalogClient,
        SubscriptionRepository subscriptionRepository,
        CapacityProperties properties,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.repository = repository;
        this.scheduleRepository = scheduleRepository;
        this.catalogClient = catalogClient;
        this.subscriptionRepository = subscriptionRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public ChefCapacitySummary getMySummary(CurrentUser user) {
        requireRole(user, "CHEF");
        return summary(user.identityId());
    }

    @Transactional
    public SlotRuleResponse putMySlotRule(PutSlotRuleRequest request, CurrentUser user) {
        requireRole(user, "CHEF");
        validateTotalCapacity(request.totalCapacityUnits(), request.subscriptionCapacityUnits());
        UUID chef = user.identityId();
        repository.lockChef(chef);
        String slot = slot(request.mealSlotCode());
        SlotRuleRow before = repository.findSlotRule(chef, request.isoDayOfWeek(), slot).orElse(null);
        SlotRuleRow stored = repository.upsertSlotRule(
            chef, request.isoDayOfWeek(), slot, request.totalCapacityUnits(), request.subscriptionCapacityUnits(),
            request.salesEnabled(), user.identityId()
        );
        reconcileRecurringSlotIncident(stored);
        audit(chef, user.identityId(), "UPSERT_SLOT_RULE", "SLOT_RULE", recurringKey(request.isoDayOfWeek(), slot),
            request.reason().trim(), before, stored);
        return repository.listSlotRules(chef).stream()
            .filter(value -> value.id().equals(stored.id())).findFirst().orElseThrow();
    }

    @Transactional
    public MenuItemRuleResponse putMyMenuItemRule(PutMenuItemRuleRequest request, CurrentUser user) {
        requireRole(user, "CHEF");
        UUID chef = user.identityId();
        repository.lockChef(chef);
        String slot = slot(request.mealSlotCode());
        catalogClient.requireSellableOwnedItem(request.menuItemId(), chef);
        MenuRuleRow before = repository.findMenuRule(chef, request.menuItemId(), request.isoDayOfWeek(), slot).orElse(null);
        MenuRuleRow stored = repository.upsertMenuRule(
            chef, request.menuItemId(), request.isoDayOfWeek(), slot, request.maxSubscriptionUnits(),
            request.salesEnabled(), user.identityId()
        );
        reconcileRecurringMenuIncident(stored);
        audit(chef, user.identityId(), "UPSERT_MENU_ITEM_RULE", "MENU_ITEM_RULE",
            request.menuItemId() + ":" + recurringKey(request.isoDayOfWeek(), slot), request.reason().trim(), before, stored);
        return repository.listMenuRules(chef).stream()
            .filter(value -> value.id().equals(stored.id())).findFirst().orElseThrow();
    }

    @Transactional
    public DateOverrideResponse putMyDateOverride(PutDateOverrideRequest request, CurrentUser user) {
        requireRole(user, "CHEF");
        validateTotalCapacity(request.totalCapacityUnits(), request.subscriptionCapacityUnits());
        if (request.serviceDate().isBefore(LocalDate.now(clock))) {
            throw ApiException.badRequest("CAPACITY_DATE_IN_PAST", "Capacity overrides cannot be created for past dates");
        }
        UUID chef = user.identityId();
        repository.lockChef(chef);
        String slot = slot(request.mealSlotCode());
        DateOverrideRow before = repository.findDateOverride(chef, request.serviceDate(), slot).orElse(null);
        DateOverrideRow stored = repository.upsertDateOverride(
            chef, request.serviceDate(), slot, request.totalCapacityUnits(), request.subscriptionCapacityUnits(),
            request.closed(), request.reason().trim(), user.identityId()
        );
        reconcileDateSlotIncident(stored);
        refreshSlotBucket(chef, request.serviceDate(), slot);
        audit(chef, user.identityId(), "UPSERT_DATE_OVERRIDE", "DATE_OVERRIDE",
            request.serviceDate() + ":" + slot, request.reason().trim(), before, stored);
        return repository.listDateOverrides(chef, request.serviceDate(), request.serviceDate()).stream().findFirst().orElseThrow();
    }

    @Transactional
    public MenuItemDateOverrideResponse putMyMenuItemDateOverride(
        PutMenuItemDateOverrideRequest request, CurrentUser user
    ) {
        requireRole(user, "CHEF");
        if (request.serviceDate().isBefore(LocalDate.now(clock))) {
            throw ApiException.badRequest("CAPACITY_DATE_IN_PAST", "Capacity overrides cannot be created for past dates");
        }
        UUID chef = user.identityId();
        repository.lockChef(chef);
        String slot = slot(request.mealSlotCode());
        catalogClient.requireSellableOwnedItem(request.menuItemId(), chef);
        MenuDateOverrideRow before = repository.findMenuDateOverride(chef, request.menuItemId(), request.serviceDate(), slot).orElse(null);
        MenuDateOverrideRow stored = repository.upsertMenuDateOverride(
            chef, request.menuItemId(), request.serviceDate(), slot, request.maxSubscriptionUnits(),
            request.closed(), request.reason().trim(), user.identityId()
        );
        reconcileDateItemIncident(stored);
        refreshMenuBucket(chef, request.menuItemId(), request.serviceDate(), slot);
        audit(chef, user.identityId(), "UPSERT_MENU_ITEM_DATE_OVERRIDE", "MENU_ITEM_DATE_OVERRIDE",
            request.menuItemId() + ":" + request.serviceDate() + ":" + slot, request.reason().trim(), before, stored);
        return repository.listMenuDateOverrides(chef, request.serviceDate(), request.serviceDate()).stream()
            .filter(value -> value.menuItemId().equals(request.menuItemId()) && value.mealSlotCode().equals(slot))
            .findFirst().orElseThrow();
    }

    public boolean isPlanBookable(PlanResponse plan) {
        if (plan == null || plan.chefIdentityId() == null || !"ACTIVE".equals(plan.status())) {
            return false;
        }
        try {
            PlanScheduleResponse schedule = requireActiveSchedule(plan.id());
            CapacityControl control = repository.getControl(plan.chefIdentityId());
            if (control.frozen()) return false;
            validateRecurringCapacity(plan.chefIdentityId(), schedule, entitlementDemands(schedule), false);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    @Transactional
    public void acquireEnrollmentHold(SubscriptionResponse subscription) {
        Objects.requireNonNull(subscription, "subscription");
        UUID chef = requireChef(subscription.chefIdentityId());
        CapacityControl control = repository.lockChef(chef);
        repository.expireHolds(clock.instant());
        if (control.frozen()) {
            throw ApiException.conflict("SUBSCRIPTION_CAPACITY_FROZEN", "This chef is temporarily not accepting new subscription capacity");
        }
        if (!repository.listActiveEntitlements(subscription.id()).isEmpty()) {
            return;
        }
        PlanScheduleResponse schedule = requireActiveSchedule(subscription.planId());
        List<Demand> demands = entitlementDemands(schedule);
        validateRecurringCapacity(chef, schedule, demands, true);
        Instant expiresAt = clock.instant().plusSeconds(properties.getHoldMinutes() * 60L);
        for (Demand demand : demands) {
            repository.insertEntitlement(
                subscription.id(), chef, schedule.recurrenceType(), demand.isoDayOfWeek(), demand.dayOfMonth(),
                demand.mealSlotCode(), demand.menuItemId(), demand.units(), "HOLD", expiresAt
            );
        }
        projectNewAdmission(subscription.id(), chef, subscription.startDate(), schedule, demands, "HOLD", expiresAt);
        audit(chef, subscription.customerIdentityId(), "CAPACITY_HOLD_CREATED", "SUBSCRIPTION", subscription.id().toString(),
            "Capacity held during subscription enrollment", null, Map.of("holdExpiresAt", expiresAt.toString()));
    }

    @Transactional
    public void commitForActivation(SubscriptionResponse subscription) {
        UUID chef = requireChef(subscription.chefIdentityId());
        repository.lockChef(chef);
        repository.expireHolds(clock.instant());
        List<EntitlementRow> active = repository.listActiveEntitlements(subscription.id());
        if (!active.isEmpty() && active.stream().allMatch(value -> "COMMITTED".equals(value.status()))) {
            extendProjectionProtected(subscription.id(), subscription.planId(), chef, subscription.startDate());
            return;
        }
        boolean unexpiredHold = !active.isEmpty() && active.stream().allMatch(value ->
            "HOLD".equals(value.status()) && value.holdExpiresAt() != null && value.holdExpiresAt().isAfter(clock.instant())
        );
        if (unexpiredHold) {
            repository.updateEntitlementStatus(subscription.id(), "HOLD", "COMMITTED", null);
            repository.updateAllocationStatus(subscription.id(), "HOLD", "COMMITTED", null);
            refreshBucketsForSubscription(active, subscription.startDate());
            audit(chef, subscription.customerIdentityId(), "CAPACITY_HOLD_COMMITTED", "SUBSCRIPTION", subscription.id().toString(),
                "Subscription activation committed the enrollment capacity hold", null, Map.of("status", "COMMITTED"));
            return;
        }

        repository.releaseEntitlements(subscription.id());
        repository.releaseAllocations(subscription.id(), subscription.startDate());
        PlanScheduleResponse schedule = requireActiveSchedule(subscription.planId());
        List<Demand> demands = entitlementDemands(schedule);
        validateRecurringCapacity(chef, schedule, demands, true);
        for (Demand demand : demands) {
            repository.insertEntitlement(
                subscription.id(), chef, schedule.recurrenceType(), demand.isoDayOfWeek(), demand.dayOfMonth(),
                demand.mealSlotCode(), demand.menuItemId(), demand.units(), "COMMITTED", null
            );
        }
        projectNewAdmission(subscription.id(), chef, subscription.startDate(), schedule, demands, "COMMITTED", null);
        audit(chef, subscription.customerIdentityId(), "CAPACITY_REACQUIRED", "SUBSCRIPTION", subscription.id().toString(),
            "Expired enrollment hold was reacquired before activation", null, Map.of("status", "COMMITTED"));
    }

    @Transactional
    public void releaseForPauseOrTerminal(SubscriptionResponse subscription, LocalDate effectiveDate, String reason) {
        UUID chef = subscription.chefIdentityId();
        if (chef == null) return;
        repository.lockChef(chef);
        repository.releaseEntitlements(subscription.id());
        repository.releaseAllocations(subscription.id(), effectiveDate);
        audit(chef, subscription.customerIdentityId(), "CAPACITY_RELEASED", "SUBSCRIPTION", subscription.id().toString(),
            reason, null, Map.of("effectiveDate", effectiveDate.toString()));
    }

    @Transactional
    public void reacquireForResume(SubscriptionResponse subscription, LocalDate resumeDate) {
        UUID chef = requireChef(subscription.chefIdentityId());
        CapacityControl control = repository.lockChef(chef);
        if (control.frozen()) {
            throw ApiException.conflict("SUBSCRIPTION_CAPACITY_FROZEN", "This chef is temporarily not accepting subscription capacity changes");
        }
        repository.releaseEntitlements(subscription.id());
        repository.releaseAllocations(subscription.id(), resumeDate);
        PlanScheduleResponse schedule = requireActiveSchedule(subscription.planId());
        List<Demand> demands = entitlementDemands(schedule);
        validateRecurringCapacity(chef, schedule, demands, true);
        for (Demand demand : demands) {
            repository.insertEntitlement(
                subscription.id(), chef, schedule.recurrenceType(), demand.isoDayOfWeek(), demand.dayOfMonth(),
                demand.mealSlotCode(), demand.menuItemId(), demand.units(), "COMMITTED", null
            );
        }
        projectNewAdmission(subscription.id(), chef, resumeDate, schedule, demands, "COMMITTED", null);
        audit(chef, subscription.customerIdentityId(), "CAPACITY_REACQUIRED_ON_RESUME", "SUBSCRIPTION", subscription.id().toString(),
            "Capacity reacquired before subscription resume", null, Map.of("resumeDate", resumeDate.toString()));
    }

    @Transactional
    public void releaseForSkip(SubscriptionResponse subscription, LocalDate serviceDate) {
        UUID chef = subscription.chefIdentityId();
        if (chef == null) return;
        repository.lockChef(chef);
        repository.releaseAllocationsForDate(subscription.id(), serviceDate);
        refreshBucketsForDate(chef, serviceDate);
        audit(chef, subscription.customerIdentityId(), "DATE_CAPACITY_RELEASED_FOR_SKIP", "SUBSCRIPTION", subscription.id().toString(),
            "Customer skipped a scheduled service date", null, Map.of("serviceDate", serviceDate.toString()));
    }

    @Transactional
    public void markMaterialized(UUID subscriptionId, LocalDate serviceDate, String mealSlotCode, UUID occurrenceId) {
        SubscriptionResponse subscription = subscriptionRepository.findSubscriptionById(subscriptionId).orElse(null);
        if (subscription == null || subscription.chefIdentityId() == null) return;
        repository.lockChef(subscription.chefIdentityId());
        repository.markAllocationMaterialized(subscriptionId, serviceDate, slot(mealSlotCode), occurrenceId);
        refreshSlotBucket(subscription.chefIdentityId(), serviceDate, slot(mealSlotCode));
    }

    @Transactional
    public ChefCapacitySummary adminGetChefSummary(UUID chefIdentityId, CurrentUser user) {
        requireRole(user, "PLATFORM_ADMIN", "SUBSCRIPTION_ADMIN", "SUPPORT_ADMIN", "OPERATIONS_ADMIN", "AUDIT_ADMIN");
        return summary(chefIdentityId);
    }

    @Transactional
    public ChefCapacitySummary adminFreezeChef(UUID chefIdentityId, CapacityFreezeRequest request, CurrentUser user) {
        requireRole(user, "PLATFORM_ADMIN", "SUBSCRIPTION_ADMIN", "SUPPORT_ADMIN", "OPERATIONS_ADMIN");
        repository.lockChef(chefIdentityId);
        CapacityControl before = repository.getControl(chefIdentityId);
        repository.setFrozen(chefIdentityId, request.frozen(), request.reason().trim(), user.identityId());
        CapacityControl after = repository.getControl(chefIdentityId);
        audit(chefIdentityId, user.identityId(), request.frozen() ? "ADMIN_FREEZE_SALES" : "ADMIN_UNFREEZE_SALES",
            "CHEF_CAPACITY_CONTROL", chefIdentityId.toString(), request.reason().trim(), before, after);
        return summary(chefIdentityId);
    }

    public CapacityIncidentPage adminListIncidents(
        UUID chefIdentityId, String status, Instant afterCreatedAt, UUID afterId, int limit, CurrentUser user
    ) {
        requireRole(user, "PLATFORM_ADMIN", "SUBSCRIPTION_ADMIN", "SUPPORT_ADMIN", "OPERATIONS_ADMIN", "AUDIT_ADMIN");
        int bounded = Math.min(200, Math.max(1, limit));
        String normalized = status == null || status.isBlank() ? null : status.trim().toUpperCase(Locale.ROOT);
        if (normalized != null && !Set.of("OPEN", "RESOLVED").contains(normalized)) {
            throw ApiException.badRequest("INVALID_CAPACITY_INCIDENT_STATUS", "status must be OPEN or RESOLVED");
        }
        return repository.listIncidents(chefIdentityId, normalized, afterCreatedAt, afterId, bounded);
    }

    @Transactional
    public void adminReconcileSubscription(UUID subscriptionId, CapacityReconcileRequest request, CurrentUser user) {
        requireRole(user, "PLATFORM_ADMIN", "SUBSCRIPTION_ADMIN", "OPERATIONS_ADMIN");
        SubscriptionResponse subscription = subscriptionRepository.findSubscriptionById(subscriptionId)
            .orElseThrow(() -> ApiException.notFound("SUBSCRIPTION_NOT_FOUND", "Subscription was not found"));
        if (subscription.chefIdentityId() == null) {
            throw ApiException.conflict("SUBSCRIPTION_CHEF_REQUIRED", "Subscription has no chef capacity owner");
        }
        if ("ACTIVE".equals(subscription.status())) {
            restoreExistingCommitmentProtected(subscription, request.reason().trim(), user.identityId());
        } else if (Set.of("PAUSED", "CANCELLED", "EXPIRED", "PAYMENT_FAILED").contains(subscription.status())) {
            repository.lockChef(subscription.chefIdentityId());
            repository.releaseEntitlements(subscription.id());
            repository.releaseAllocations(subscription.id(), LocalDate.now(clock));
            audit(subscription.chefIdentityId(), user.identityId(), "ADMIN_CAPACITY_RECONCILE_RELEASE", "SUBSCRIPTION",
                subscription.id().toString(), request.reason().trim(), null, Map.of("subscriptionStatus", subscription.status()));
        } else {
            throw ApiException.conflict("SUBSCRIPTION_NOT_RECONCILABLE", "Pending payment subscriptions are reconciled through their capacity hold lifecycle");
        }
    }

    @Transactional
    public int extendProjectionBatch() {
        repository.expireHolds(clock.instant());
        int processed = 0;
        for (SubscriptionProjectionCandidate candidate : repository.listProjectionCandidates(properties.getProjectionBatchSize())) {
            try {
                repository.lockChef(candidate.chefIdentityId());
                extendProjectionProtected(candidate.subscriptionId(), candidate.planId(), candidate.chefIdentityId(), candidate.startDate());
                processed++;
            } catch (RuntimeException exception) {
                repository.openOrUpdateIncident(
                    candidate.chefIdentityId(), null, null, "PROJECTION", null, "PROJECTION_FAILURE", "P3",
                    0, 0, "Capacity projection failed for subscription " + candidate.subscriptionId() + ": " + safeMessage(exception)
                );
            }
        }
        return processed;
    }

    private ChefCapacitySummary summary(UUID chefIdentityId) {
        CapacityControl control = repository.getControl(chefIdentityId);
        LocalDate today = LocalDate.now(clock);
        LocalDate through = today.plusDays(Math.min(properties.getProjectionHorizonDays(), 365));
        return new ChefCapacitySummary(
            chefIdentityId, control.frozen(), control.freezeReason(), repository.listSlotRules(chefIdentityId),
            repository.listMenuRules(chefIdentityId), repository.listDateOverrides(chefIdentityId, today, through),
            repository.listMenuDateOverrides(chefIdentityId, today, through), repository.countOpenIncidents(chefIdentityId)
        );
    }

    private void validateRecurringCapacity(
        UUID chefIdentityId, PlanScheduleResponse schedule, List<Demand> demands, boolean strictForWrite
    ) {
        Map<SlotPattern, Integer> slotDemand = new LinkedHashMap<>();
        Map<ItemPattern, Integer> itemDemand = new LinkedHashMap<>();
        for (Demand demand : demands) {
            slotDemand.merge(new SlotPattern(demand.isoDayOfWeek(), demand.dayOfMonth(), demand.mealSlotCode()), demand.units(), Integer::sum);
            itemDemand.merge(new ItemPattern(demand.menuItemId(), demand.isoDayOfWeek(), demand.dayOfMonth(), demand.mealSlotCode()), demand.units(), Integer::sum);
        }

        if ("WEEKLY".equals(schedule.recurrenceType())) {
            for (Map.Entry<SlotPattern, Integer> entry : slotDemand.entrySet()) {
                int day = Objects.requireNonNull(entry.getKey().isoDayOfWeek());
                SlotRuleRow rule = requireSlotRule(chefIdentityId, day, entry.getKey().mealSlotCode());
                if (!rule.salesEnabled()) capacityUnavailable(strictForWrite, "CAPACITY_SALES_DISABLED", "Chef disabled subscription sales for a required meal slot");
                int reserved = repository.activeWeeklyUnits(chefIdentityId, day, rule.mealSlotCode())
                    + repository.maxActiveMonthlyUnits(chefIdentityId, rule.mealSlotCode()) + entry.getValue();
                if (reserved > rule.subscriptionCapacityUnits()) capacityUnavailable(strictForWrite, "SUBSCRIPTION_CAPACITY_UNAVAILABLE", "Subscription capacity is full for a required meal slot");
            }
            for (Map.Entry<ItemPattern, Integer> entry : itemDemand.entrySet()) {
                int day = Objects.requireNonNull(entry.getKey().isoDayOfWeek());
                Optional<MenuRuleRow> rule = repository.findMenuRule(chefIdentityId, entry.getKey().menuItemId(), day, entry.getKey().mealSlotCode());
                if (rule.isEmpty()) continue;
                if (!rule.get().salesEnabled()) capacityUnavailable(strictForWrite, "MENU_ITEM_CAPACITY_DISABLED", "Chef disabled subscription sales for a required menu item");
                int reserved = repository.activeWeeklyItemUnits(chefIdentityId, entry.getKey().menuItemId(), day, entry.getKey().mealSlotCode())
                    + repository.maxActiveMonthlyItemUnits(chefIdentityId, entry.getKey().menuItemId(), entry.getKey().mealSlotCode()) + entry.getValue();
                if (reserved > rule.get().maxSubscriptionUnits()) capacityUnavailable(strictForWrite, "MENU_ITEM_CAPACITY_UNAVAILABLE", "Menu item subscription capacity is full");
            }
            return;
        }

        Map<String, Integer> monthlyMaxAfterBySlot = new HashMap<>();
        for (Map.Entry<SlotPattern, Integer> entry : slotDemand.entrySet()) {
            int dayOfMonth = Objects.requireNonNull(entry.getKey().dayOfMonth());
            int existingAtDay = repository.activeMonthlyUnits(chefIdentityId, dayOfMonth, entry.getKey().mealSlotCode());
            int currentMax = repository.maxActiveMonthlyUnits(chefIdentityId, entry.getKey().mealSlotCode());
            monthlyMaxAfterBySlot.merge(entry.getKey().mealSlotCode(), Math.max(currentMax, existingAtDay + entry.getValue()), Math::max);
        }
        for (Map.Entry<String, Integer> entry : monthlyMaxAfterBySlot.entrySet()) {
            for (int weekday = 1; weekday <= 7; weekday++) {
                SlotRuleRow rule = requireSlotRule(chefIdentityId, weekday, entry.getKey());
                if (!rule.salesEnabled()) capacityUnavailable(strictForWrite, "CAPACITY_SALES_DISABLED", "Monthly subscription requires an enabled capacity rule for every possible weekday");
                int reserved = repository.activeWeeklyUnits(chefIdentityId, weekday, entry.getKey()) + entry.getValue();
                if (reserved > rule.subscriptionCapacityUnits()) capacityUnavailable(strictForWrite, "SUBSCRIPTION_CAPACITY_UNAVAILABLE", "Monthly subscription would exceed capacity on a possible future weekday");
            }
        }

        Map<ItemSlot, Integer> monthlyItemMaxAfter = new HashMap<>();
        for (Map.Entry<ItemPattern, Integer> entry : itemDemand.entrySet()) {
            int dayOfMonth = Objects.requireNonNull(entry.getKey().dayOfMonth());
            ItemSlot key = new ItemSlot(entry.getKey().menuItemId(), entry.getKey().mealSlotCode());
            int existingAtDay = repository.activeMonthlyItemUnits(chefIdentityId, key.menuItemId(), dayOfMonth, key.mealSlotCode());
            int currentMax = repository.maxActiveMonthlyItemUnits(chefIdentityId, key.menuItemId(), key.mealSlotCode());
            monthlyItemMaxAfter.merge(key, Math.max(currentMax, existingAtDay + entry.getValue()), Math::max);
        }
        for (Map.Entry<ItemSlot, Integer> entry : monthlyItemMaxAfter.entrySet()) {
            for (int weekday = 1; weekday <= 7; weekday++) {
                Optional<MenuRuleRow> rule = repository.findMenuRule(chefIdentityId, entry.getKey().menuItemId(), weekday, entry.getKey().mealSlotCode());
                if (rule.isEmpty()) continue;
                if (!rule.get().salesEnabled()) capacityUnavailable(strictForWrite, "MENU_ITEM_CAPACITY_DISABLED", "Monthly subscription uses a menu item disabled for a possible weekday");
                int reserved = repository.activeWeeklyItemUnits(chefIdentityId, entry.getKey().menuItemId(), weekday, entry.getKey().mealSlotCode()) + entry.getValue();
                if (reserved > rule.get().maxSubscriptionUnits()) capacityUnavailable(strictForWrite, "MENU_ITEM_CAPACITY_UNAVAILABLE", "Monthly menu item commitment would exceed capacity on a possible future weekday");
            }
        }
    }

    private void projectNewAdmission(
        UUID subscriptionId, UUID chefIdentityId, LocalDate startDate, PlanScheduleResponse schedule,
        List<Demand> demands, String status, Instant holdExpiresAt
    ) {
        LocalDate through = startDate.plusDays(properties.getProjectionHorizonDays() - 1L);
        for (LocalDate date = startDate; !date.isAfter(through); date = date.plusDays(1)) {
            List<Demand> matching = demandsForDate(schedule.recurrenceType(), demands, date);
            if (matching.isEmpty()) continue;
            validateDateCapacity(chefIdentityId, date, matching, true);
            for (Demand demand : matching) {
                repository.upsertAllocation(
                    subscriptionId, chefIdentityId, date, demand.mealSlotCode(), demand.menuItemId(), demand.units(), status, holdExpiresAt
                );
            }
            refreshBucketsForDate(chefIdentityId, date);
        }
    }

    private void extendProjectionProtected(UUID subscriptionId, UUID planId, UUID chefIdentityId, LocalDate startDate) {
        List<EntitlementRow> entitlements = repository.listActiveEntitlements(subscriptionId).stream()
            .filter(value -> "COMMITTED".equals(value.status())).toList();
        if (entitlements.isEmpty()) return;
        LocalDate today = LocalDate.now(clock);
        LocalDate from = repository.maxAllocatedDate(subscriptionId).map(value -> value.plusDays(1)).orElse(startDate);
        if (from.isBefore(today)) from = today;
        LocalDate through = today.plusDays(properties.getProjectionHorizonDays());
        if (from.isAfter(through)) return;
        for (LocalDate date = from; !date.isAfter(through); date = date.plusDays(1)) {
            LocalDate projectionDate = date;
            List<EntitlementRow> matching = entitlements.stream().filter(value -> matches(value, projectionDate)).toList();
            if (matching.isEmpty()) continue;
            for (EntitlementRow entitlement : matching) {
                repository.upsertAllocation(
                    subscriptionId, chefIdentityId, date, entitlement.mealSlotCode(), entitlement.menuItemId(), entitlement.units(), "COMMITTED", null
                );
            }
            reconcileProtectedDate(chefIdentityId, date, matching);
            refreshBucketsForDate(chefIdentityId, date);
        }
    }

    private void restoreExistingCommitmentProtected(SubscriptionResponse subscription, String reason, UUID actorIdentityId) {
        UUID chef = requireChef(subscription.chefIdentityId());
        repository.lockChef(chef);
        List<EntitlementRow> active = repository.listActiveEntitlements(subscription.id());
        if (active.stream().anyMatch(value -> "COMMITTED".equals(value.status()))) {
            extendProjectionProtected(subscription.id(), subscription.planId(), chef, subscription.startDate());
            return;
        }
        PlanScheduleResponse schedule = requireActiveSchedule(subscription.planId());
        List<Demand> demands = entitlementDemands(schedule);
        for (Demand demand : demands) {
            repository.insertEntitlement(
                subscription.id(), chef, schedule.recurrenceType(), demand.isoDayOfWeek(), demand.dayOfMonth(),
                demand.mealSlotCode(), demand.menuItemId(), demand.units(), "COMMITTED", null
            );
        }
        projectProtected(subscription.id(), chef, subscription.startDate(), schedule, demands);
        reconcileAllRecurringIncidents(chef);
        audit(chef, actorIdentityId, "ADMIN_CAPACITY_RESTORE", "SUBSCRIPTION", subscription.id().toString(), reason,
            null, Map.of("subscriptionStatus", subscription.status()));
    }

    private void projectProtected(
        UUID subscriptionId, UUID chefIdentityId, LocalDate startDate, PlanScheduleResponse schedule, List<Demand> demands
    ) {
        LocalDate through = LocalDate.now(clock).plusDays(properties.getProjectionHorizonDays());
        LocalDate from = startDate.isBefore(LocalDate.now(clock)) ? LocalDate.now(clock) : startDate;
        for (LocalDate date = from; !date.isAfter(through); date = date.plusDays(1)) {
            List<Demand> matching = demandsForDate(schedule.recurrenceType(), demands, date);
            if (matching.isEmpty()) continue;
            for (Demand demand : matching) {
                repository.upsertAllocation(
                    subscriptionId, chefIdentityId, date, demand.mealSlotCode(), demand.menuItemId(), demand.units(), "COMMITTED", null
                );
            }
            reconcileProtectedDateFromDemand(chefIdentityId, date, matching);
            refreshBucketsForDate(chefIdentityId, date);
        }
    }

    private void validateDateCapacity(UUID chefIdentityId, LocalDate date, List<Demand> matching, boolean write) {
        Map<String, Integer> slotDemand = new HashMap<>();
        Map<ItemSlot, Integer> itemDemand = new HashMap<>();
        for (Demand demand : matching) {
            slotDemand.merge(demand.mealSlotCode(), demand.units(), Integer::sum);
            itemDemand.merge(new ItemSlot(demand.menuItemId(), demand.mealSlotCode()), demand.units(), Integer::sum);
        }
        for (Map.Entry<String, Integer> entry : slotDemand.entrySet()) {
            EffectiveSlot effective = effectiveSlot(chefIdentityId, date, entry.getKey());
            if (effective.closed() || !effective.salesEnabled()) capacityUnavailable(write, "CAPACITY_DATE_CLOSED", "Chef closed a required service date or meal slot");
            int existing = repository.currentDateSlotUnits(chefIdentityId, date, entry.getKey());
            if (existing + entry.getValue() > effective.subscriptionCapacityUnits()) {
                capacityUnavailable(write, "SUBSCRIPTION_CAPACITY_UNAVAILABLE", "Subscription capacity is full for a required service date");
            }
        }
        for (Map.Entry<ItemSlot, Integer> entry : itemDemand.entrySet()) {
            Optional<EffectiveItem> effective = effectiveItem(chefIdentityId, entry.getKey().menuItemId(), date, entry.getKey().mealSlotCode());
            if (effective.isEmpty()) continue;
            if (effective.get().closed() || !effective.get().salesEnabled()) capacityUnavailable(write, "MENU_ITEM_CAPACITY_DATE_CLOSED", "Chef closed a required menu item for a service date");
            int existing = repository.currentDateItemUnits(chefIdentityId, entry.getKey().menuItemId(), date, entry.getKey().mealSlotCode());
            if (existing + entry.getValue() > effective.get().maxSubscriptionUnits()) {
                capacityUnavailable(write, "MENU_ITEM_CAPACITY_UNAVAILABLE", "Menu item capacity is full for a required service date");
            }
        }
    }

    private void reconcileProtectedDate(UUID chefIdentityId, LocalDate date, List<EntitlementRow> matching) {
        List<Demand> demand = matching.stream().map(value -> new Demand(
            value.menuItemId(), value.units(), value.isoDayOfWeek(), value.dayOfMonth(), value.mealSlotCode()
        )).toList();
        reconcileProtectedDateFromDemand(chefIdentityId, date, demand);
    }

    private void reconcileProtectedDateFromDemand(UUID chefIdentityId, LocalDate date, List<Demand> matching) {
        Set<String> slots = matching.stream().map(Demand::mealSlotCode).collect(java.util.stream.Collectors.toSet());
        for (String slot : slots) {
            try {
                EffectiveSlot effective = effectiveSlot(chefIdentityId, date, slot);
                int reserved = repository.currentDateSlotUnits(chefIdentityId, date, slot);
                if (effective.closed() || reserved > effective.subscriptionCapacityUnits()) {
                    repository.openOrUpdateIncident(
                        chefIdentityId, date, null, slot, null, "DATE_DEFICIT", "P2", reserved,
                        effective.closed() ? 0 : effective.subscriptionCapacityUnits(),
                        "Existing subscription commitments exceed the chef's current date capacity; existing customers remain protected and new sales are blocked"
                    );
                } else {
                    repository.resolveIncident(chefIdentityId, date, null, slot, null, "DATE_DEFICIT");
                }
            } catch (ApiException exception) {
                repository.openOrUpdateIncident(
                    chefIdentityId, date, null, slot, null, "PROJECTION_FAILURE", "P2",
                    repository.currentDateSlotUnits(chefIdentityId, date, slot), 0,
                    "No effective chef capacity rule exists for an existing subscription commitment"
                );
            }
        }
        for (Demand item : matching) {
            Optional<EffectiveItem> effective = effectiveItem(chefIdentityId, item.menuItemId(), date, item.mealSlotCode());
            if (effective.isEmpty()) continue;
            int reserved = repository.currentDateItemUnits(chefIdentityId, item.menuItemId(), date, item.mealSlotCode());
            if (effective.get().closed() || reserved > effective.get().maxSubscriptionUnits()) {
                repository.openOrUpdateIncident(
                    chefIdentityId, date, null, item.mealSlotCode(), item.menuItemId(), "ITEM_DEFICIT", "P2", reserved,
                    effective.get().closed() ? 0 : effective.get().maxSubscriptionUnits(),
                    "Existing subscription commitments exceed this menu item's current capacity; existing customers remain protected"
                );
            } else {
                repository.resolveIncident(chefIdentityId, date, null, item.mealSlotCode(), item.menuItemId(), "ITEM_DEFICIT");
            }
        }
    }

    private EffectiveSlot effectiveSlot(UUID chefIdentityId, LocalDate date, String mealSlotCode) {
        Optional<DateOverrideRow> override = repository.findDateOverride(chefIdentityId, date, mealSlotCode);
        if (override.isPresent()) {
            DateOverrideRow value = override.get();
            return new EffectiveSlot(
                value.totalCapacityUnits(), value.subscriptionCapacityUnits(), value.closed(), true, "OVERRIDE", 1
            );
        }
        SlotRuleRow rule = requireSlotRule(chefIdentityId, date.getDayOfWeek().getValue(), mealSlotCode);
        return new EffectiveSlot(
            rule.totalCapacityUnits(), rule.subscriptionCapacityUnits(), false, rule.salesEnabled(), "RULE", rule.version()
        );
    }

    private Optional<EffectiveItem> effectiveItem(UUID chefIdentityId, UUID menuItemId, LocalDate date, String mealSlotCode) {
        Optional<MenuDateOverrideRow> override = repository.findMenuDateOverride(chefIdentityId, menuItemId, date, mealSlotCode);
        if (override.isPresent()) {
            MenuDateOverrideRow value = override.get();
            return Optional.of(new EffectiveItem(value.maxSubscriptionUnits(), value.closed(), true, "OVERRIDE", 1));
        }
        return repository.findMenuRule(chefIdentityId, menuItemId, date.getDayOfWeek().getValue(), mealSlotCode)
            .map(rule -> new EffectiveItem(rule.maxSubscriptionUnits(), false, rule.salesEnabled(), "RULE", rule.version()));
    }

    private void refreshSlotBucket(UUID chefIdentityId, LocalDate date, String mealSlotCode) {
        try {
            EffectiveSlot effective = effectiveSlot(chefIdentityId, date, mealSlotCode);
            repository.upsertBucket(
                chefIdentityId, date, mealSlotCode, effective.totalCapacityUnits(), effective.subscriptionCapacityUnits(),
                effective.closed(), effective.source(), effective.version()
            );
        } catch (ApiException ignored) {
            // An incident represents an existing commitment without configured capacity; no synthetic capacity is invented.
        }
    }

    private void refreshMenuBucket(UUID chefIdentityId, UUID menuItemId, LocalDate date, String mealSlotCode) {
        effectiveItem(chefIdentityId, menuItemId, date, mealSlotCode).ifPresent(effective -> repository.upsertMenuBucket(
            chefIdentityId, menuItemId, date, mealSlotCode, effective.maxSubscriptionUnits(), effective.closed(),
            effective.source(), effective.version()
        ));
    }

    private void refreshBucketsForDate(UUID chefIdentityId, LocalDate date) {
        for (SlotRuleRow rule : repository.listSlotRuleRows(chefIdentityId)) {
            if (rule.isoDayOfWeek() == date.getDayOfWeek().getValue()) {
                refreshSlotBucket(chefIdentityId, date, rule.mealSlotCode());
            }
        }
        for (DateOverrideRow override : repository.listDateOverrideRows(chefIdentityId, date, date)) {
            refreshSlotBucket(chefIdentityId, date, override.mealSlotCode());
        }
        for (MenuRuleRow rule : repository.listMenuRuleRows(chefIdentityId)) {
            if (rule.isoDayOfWeek() == date.getDayOfWeek().getValue()) {
                refreshMenuBucket(chefIdentityId, rule.menuItemId(), date, rule.mealSlotCode());
            }
        }
        for (MenuDateOverrideRow override : repository.listMenuDateOverrideRows(chefIdentityId, date, date)) {
            refreshMenuBucket(chefIdentityId, override.menuItemId(), date, override.mealSlotCode());
        }
    }

    private void refreshBucketsForSubscription(List<EntitlementRow> entitlements, LocalDate fromDate) {
        if (entitlements.isEmpty()) return;
        UUID chef = entitlements.getFirst().chefIdentityId();
        LocalDate through = LocalDate.now(clock).plusDays(properties.getProjectionHorizonDays());
        LocalDate from = fromDate.isBefore(LocalDate.now(clock)) ? LocalDate.now(clock) : fromDate;
        for (LocalDate date = from; !date.isAfter(through); date = date.plusDays(1)) {
            LocalDate bucketDate = date;
            if (entitlements.stream().anyMatch(value -> matches(value, bucketDate))) {
                refreshBucketsForDate(chef, date);
            }
        }
    }

    private void reconcileRecurringSlotIncident(SlotRuleRow rule) {
        int reserved = repository.activeWeeklyUnits(rule.chefIdentityId(), rule.isoDayOfWeek(), rule.mealSlotCode())
            + repository.maxActiveMonthlyUnits(rule.chefIdentityId(), rule.mealSlotCode());
        if (!rule.salesEnabled() || reserved <= rule.subscriptionCapacityUnits()) {
            if (reserved <= rule.subscriptionCapacityUnits()) {
                repository.resolveIncident(rule.chefIdentityId(), null, rule.isoDayOfWeek(), rule.mealSlotCode(), null, "RECURRING_DEFICIT");
            }
            return;
        }
        repository.openOrUpdateIncident(
            rule.chefIdentityId(), null, rule.isoDayOfWeek(), rule.mealSlotCode(), null, "RECURRING_DEFICIT", "P2",
            reserved, rule.subscriptionCapacityUnits(),
            "Chef reduced recurring subscription capacity below existing commitments; existing subscribers remain protected and new sales are blocked"
        );
    }

    private void reconcileRecurringMenuIncident(MenuRuleRow rule) {
        int reserved = repository.activeWeeklyItemUnits(rule.chefIdentityId(), rule.menuItemId(), rule.isoDayOfWeek(), rule.mealSlotCode())
            + repository.maxActiveMonthlyItemUnits(rule.chefIdentityId(), rule.menuItemId(), rule.mealSlotCode());
        if (reserved <= rule.maxSubscriptionUnits()) {
            repository.resolveIncident(rule.chefIdentityId(), null, rule.isoDayOfWeek(), rule.mealSlotCode(), rule.menuItemId(), "ITEM_DEFICIT");
            return;
        }
        repository.openOrUpdateIncident(
            rule.chefIdentityId(), null, rule.isoDayOfWeek(), rule.mealSlotCode(), rule.menuItemId(), "ITEM_DEFICIT", "P2",
            reserved, rule.maxSubscriptionUnits(),
            "Chef reduced recurring menu-item capacity below existing commitments; existing subscribers remain protected"
        );
    }

    private void reconcileDateSlotIncident(DateOverrideRow override) {
        int reserved = repository.currentDateSlotUnits(override.chefIdentityId(), override.serviceDate(), override.mealSlotCode());
        int capacity = override.closed() ? 0 : override.subscriptionCapacityUnits();
        if (reserved <= capacity) {
            repository.resolveIncident(override.chefIdentityId(), override.serviceDate(), null, override.mealSlotCode(), null, "DATE_DEFICIT");
        } else {
            repository.openOrUpdateIncident(
                override.chefIdentityId(), override.serviceDate(), null, override.mealSlotCode(), null, "DATE_DEFICIT", "P2",
                reserved, capacity,
                "Chef date override is below existing commitments; existing subscribers remain protected and new sales are blocked"
            );
        }
    }

    private void reconcileDateItemIncident(MenuDateOverrideRow override) {
        int reserved = repository.currentDateItemUnits(
            override.chefIdentityId(), override.menuItemId(), override.serviceDate(), override.mealSlotCode()
        );
        int capacity = override.closed() ? 0 : override.maxSubscriptionUnits();
        if (reserved <= capacity) {
            repository.resolveIncident(override.chefIdentityId(), override.serviceDate(), null, override.mealSlotCode(), override.menuItemId(), "ITEM_DEFICIT");
        } else {
            repository.openOrUpdateIncident(
                override.chefIdentityId(), override.serviceDate(), null, override.mealSlotCode(), override.menuItemId(), "ITEM_DEFICIT", "P2",
                reserved, capacity,
                "Chef menu-item date override is below existing commitments; existing subscribers remain protected"
            );
        }
    }

    private void reconcileAllRecurringIncidents(UUID chefIdentityId) {
        repository.listSlotRuleRows(chefIdentityId).forEach(this::reconcileRecurringSlotIncident);
        repository.listMenuRuleRows(chefIdentityId).forEach(this::reconcileRecurringMenuIncident);
    }

    private PlanScheduleResponse requireActiveSchedule(UUID planId) {
        return scheduleRepository.findActive(planId)
            .orElseThrow(() -> ApiException.conflict("PLAN_CAPACITY_SCHEDULE_REQUIRED", "Active meal schedule is required before capacity can be reserved"));
    }

    private SlotRuleRow requireSlotRule(UUID chefIdentityId, int isoDayOfWeek, String mealSlotCode) {
        return repository.findSlotRule(chefIdentityId, isoDayOfWeek, mealSlotCode)
            .orElseThrow(() -> ApiException.conflict(
                "CHEF_CAPACITY_NOT_CONFIGURED",
                "Chef must configure subscription capacity for every required weekday and meal slot before the plan can be booked"
            ));
    }

    private List<Demand> entitlementDemands(PlanScheduleResponse schedule) {
        Map<DemandKey, Integer> grouped = new LinkedHashMap<>();
        for (ScheduleItemResponse item : schedule.items()) {
            DemandKey key = new DemandKey(
                item.menuItemId(), item.isoDayOfWeek(), item.dayOfMonth(), slot(item.mealSlotCode())
            );
            grouped.merge(key, item.quantity(), Integer::sum);
        }
        return grouped.entrySet().stream()
            .map(entry -> new Demand(
                entry.getKey().menuItemId(), entry.getValue(), entry.getKey().isoDayOfWeek(),
                entry.getKey().dayOfMonth(), entry.getKey().mealSlotCode()
            ))
            .sorted(Comparator.comparing(Demand::mealSlotCode).thenComparing(value -> value.menuItemId().toString()))
            .toList();
    }

    private static List<Demand> demandsForDate(String recurrenceType, List<Demand> demands, LocalDate date) {
        return demands.stream().filter(value -> {
            if ("WEEKLY".equals(recurrenceType)) {
                return value.isoDayOfWeek() != null && value.isoDayOfWeek() == date.getDayOfWeek().getValue();
            }
            return value.dayOfMonth() != null && value.dayOfMonth() == date.getDayOfMonth();
        }).toList();
    }

    private static boolean matches(EntitlementRow value, LocalDate date) {
        if ("WEEKLY".equals(value.recurrenceType())) {
            return value.isoDayOfWeek() != null && value.isoDayOfWeek() == date.getDayOfWeek().getValue();
        }
        return value.dayOfMonth() != null && value.dayOfMonth() == date.getDayOfMonth();
    }

    private static void validateTotalCapacity(int total, int subscription) {
        if (subscription > total) {
            throw ApiException.badRequest(
                "SUBSCRIPTION_CAPACITY_EXCEEDS_TOTAL",
                "subscriptionCapacityUnits cannot exceed totalCapacityUnits"
            );
        }
    }

    private static void capacityUnavailable(boolean write, String code, String message) {
        if (write) throw ApiException.conflict(code, message);
        throw new CapacityUnavailableException(code);
    }

    private static UUID requireChef(UUID chefIdentityId) {
        if (chefIdentityId == null) {
            throw ApiException.conflict("PLAN_CHEF_REQUIRED", "Subscription plan must have an approved chef before capacity can be reserved");
        }
        return chefIdentityId;
    }

    private static String slot(String value) {
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String recurringKey(int day, String slot) {
        return day + ":" + slot;
    }

    private void audit(
        UUID chef, UUID actor, String action, String entityType, String key, String reason, Object before, Object after
    ) {
        repository.audit(chef, actor, action, entityType, key, reason, json(before), json(after));
    }

    private String json(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Capacity audit serialization failed", exception);
        }
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) return exception.getClass().getSimpleName();
        return message.length() > 400 ? message.substring(0, 400) : message;
    }

    private static void requireRole(CurrentUser user, String... roles) {
        if (user != null && user.hasAnyRole(roles)) return;
        throw ApiException.forbidden("ROLE_NOT_ALLOWED", "User does not have the required capacity-management role");
    }

    private record DemandKey(UUID menuItemId, Integer isoDayOfWeek, Integer dayOfMonth, String mealSlotCode) {}
    private record Demand(UUID menuItemId, int units, Integer isoDayOfWeek, Integer dayOfMonth, String mealSlotCode) {}
    private record SlotPattern(Integer isoDayOfWeek, Integer dayOfMonth, String mealSlotCode) {}
    private record ItemPattern(UUID menuItemId, Integer isoDayOfWeek, Integer dayOfMonth, String mealSlotCode) {}
    private record ItemSlot(UUID menuItemId, String mealSlotCode) {}
    private record EffectiveSlot(int totalCapacityUnits, int subscriptionCapacityUnits, boolean closed, boolean salesEnabled, String source, int version) {}
    private record EffectiveItem(int maxSubscriptionUnits, boolean closed, boolean salesEnabled, String source, int version) {}

    private static final class CapacityUnavailableException extends RuntimeException {
        private CapacityUnavailableException(String code) {
            super(code);
        }
    }
}
