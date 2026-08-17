package in.craves.subscription.plan;

import in.craves.subscription.exception.ApiException;
import in.craves.subscription.schedule.PlanCatalogClient;
import in.craves.subscription.schedule.PlanCatalogClient.MenuItem;
import in.craves.subscription.schedule.PlanScheduleModels.PlanScheduleResponse;
import in.craves.subscription.schedule.PlanScheduleModels.PutScheduleRequest;
import in.craves.subscription.schedule.PlanScheduleModels.ScheduleItemRequest;
import in.craves.subscription.schedule.PlanScheduleRepository;
import in.craves.subscription.schedule.PlanScheduleRepository.PlanOwner;
import in.craves.subscription.schedule.PlanScheduleRepository.PreparedScheduleItem;
import in.craves.subscription.security.CurrentUser;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class ChefPlanScheduleService {
    private static final Set<String> RECURRENCES = Set.of("WEEKLY", "MONTHLY");

    private final PlanScheduleRepository repository;
    private final PlanCatalogClient catalogClient;

    public ChefPlanScheduleService(PlanScheduleRepository repository, PlanCatalogClient catalogClient) {
        this.repository = repository;
        this.catalogClient = catalogClient;
    }

    public PlanScheduleResponse getOwned(UUID planId, CurrentUser user) {
        PlanOwner plan = requireChefOwnedPlan(planId, user);
        return repository.find(plan.planId())
            .orElseThrow(() -> ApiException.notFound("PLAN_SCHEDULE_NOT_FOUND", "Meal schedule has not been created yet"));
    }

    public PlanScheduleResponse putOwned(UUID planId, PutScheduleRequest request, CurrentUser user) {
        PlanOwner plan = requireChefOwnedPlan(planId, user);
        if (!Set.of("DRAFT", "REJECTED").contains(plan.status())) {
            throw ApiException.conflict("PLAN_NOT_EDITABLE", "Meal schedule can only be changed while the plan is draft or rejected");
        }
        String recurrence = request.recurrenceType().trim().toUpperCase(Locale.ROOT);
        if (!RECURRENCES.contains(recurrence)) {
            throw ApiException.badRequest("INVALID_RECURRENCE", "recurrenceType must be WEEKLY or MONTHLY");
        }
        if (!recurrence.equals(plan.billingPeriod())) {
            throw ApiException.conflict("RECURRENCE_BILLING_PERIOD_MISMATCH", "Meal schedule frequency must match the plan billing period");
        }
        requireTimezone(request.timezone());
        List<PreparedScheduleItem> prepared = validateAndPrepareItems(recurrence, request.items(), user.identityId());
        try {
            return repository.replaceDraft(
                planId,
                recurrence,
                request.timezone().trim(),
                request.generationLeadHours(),
                prepared,
                user.identityId()
            );
        } catch (IllegalArgumentException | IllegalStateException exception) {
            throw ApiException.conflict("PLAN_SCHEDULE_CONFLICT", exception.getMessage());
        }
    }

    public PlanScheduleResponse requireReadyForSubmission(UUID planId, CurrentUser user) {
        PlanOwner plan = requireChefOwnedPlan(planId, user);
        PlanScheduleResponse schedule = repository.find(plan.planId())
            .filter(value -> "DRAFT".equals(value.status()))
            .orElseThrow(() -> ApiException.conflict("PLAN_SCHEDULE_REQUIRED", "Add at least one meal to the plan before submitting it"));
        if (schedule.items().isEmpty()) {
            throw ApiException.conflict("PLAN_SCHEDULE_EMPTY", "Add at least one meal to the plan before submitting it");
        }
        requireScheduleMatchesPlan(plan, schedule);
        validateAndPrepareItems(
            schedule.recurrenceType(),
            schedule.items().stream().map(item -> new ScheduleItemRequest(
                item.menuItemId(), item.quantity(), item.isoDayOfWeek(), item.dayOfMonth(),
                item.mealSlotCode(), item.serviceTime(), item.sequenceNumber()
            )).toList(),
            user.identityId()
        );
        return schedule;
    }

    public PlanScheduleResponse activateSubmittedDraft(UUID planId, CurrentUser admin) {
        requireAdmin(admin);
        PlanOwner plan = repository.findPlanOwner(planId)
            .orElseThrow(() -> ApiException.notFound("PLAN_NOT_FOUND", "Subscription meal plan was not found"));
        if (!"PENDING_APPROVAL".equals(plan.status())) {
            throw ApiException.conflict("PLAN_NOT_PENDING_APPROVAL", "Only submitted meal plans can be approved");
        }
        PlanScheduleResponse schedule = repository.find(planId)
            .filter(value -> "DRAFT".equals(value.status()))
            .orElseThrow(() -> ApiException.conflict("PLAN_SCHEDULE_REQUIRED", "Submitted plan has no reviewable meal schedule"));
        if (schedule.items().isEmpty()) {
            throw ApiException.conflict("PLAN_SCHEDULE_EMPTY", "Submitted plan has no meal items");
        }
        requireScheduleMatchesPlan(plan, schedule);
        validateAndPrepareItems(
            schedule.recurrenceType(),
            schedule.items().stream().map(item -> new ScheduleItemRequest(
                item.menuItemId(), item.quantity(), item.isoDayOfWeek(), item.dayOfMonth(),
                item.mealSlotCode(), item.serviceTime(), item.sequenceNumber()
            )).toList(),
            plan.chefIdentityId()
        );
        return repository.activate(planId, admin.identityId(), "Meal schedule activated by administrator plan approval");
    }

    private PlanOwner requireChefOwnedPlan(UUID planId, CurrentUser user) {
        requireChef(user);
        PlanOwner plan = repository.findPlanOwner(planId)
            .orElseThrow(() -> ApiException.notFound("PLAN_NOT_FOUND", "Subscription meal plan was not found"));
        if (plan.chefIdentityId() == null || !plan.chefIdentityId().equals(user.identityId())) {
            throw ApiException.forbidden("PLAN_ACCESS_DENIED", "You cannot manage another Chef's meal plan");
        }
        return plan;
    }

    private List<PreparedScheduleItem> validateAndPrepareItems(
        String recurrence,
        List<ScheduleItemRequest> items,
        UUID chefIdentityId
    ) {
        if (chefIdentityId == null) {
            throw ApiException.conflict("PLAN_CHEF_REQUIRED", "Meal plan must belong to an approved Chef");
        }
        if (items == null || items.isEmpty()) {
            throw ApiException.badRequest("PLAN_SCHEDULE_EMPTY", "Select at least one available menu item");
        }
        Set<String> uniqueness = new HashSet<>();
        Map<String, LocalTime> slotTimes = new HashMap<>();
        List<PreparedScheduleItem> prepared = new ArrayList<>();
        for (ScheduleItemRequest item : items) {
            if ("WEEKLY".equals(recurrence)) {
                if (item.isoDayOfWeek() == null || item.dayOfMonth() != null) {
                    throw ApiException.badRequest("INVALID_WEEKLY_SCHEDULE_ITEM", "Weekly meals require a weekday");
                }
            } else if (item.dayOfMonth() == null || item.isoDayOfWeek() != null) {
                throw ApiException.badRequest("INVALID_MONTHLY_SCHEDULE_ITEM", "Monthly meals require a day of month");
            }
            String slot = item.mealSlotCode().trim().toUpperCase(Locale.ROOT);
            String day = item.isoDayOfWeek() != null ? "W:" + item.isoDayOfWeek() : "M:" + item.dayOfMonth();
            if (!uniqueness.add(day + ":" + slot + ":" + item.menuItemId())) {
                throw ApiException.badRequest("DUPLICATE_SCHEDULE_ITEM", "The same menu item cannot be added twice to one meal slot");
            }
            String slotKey = day + ":" + slot;
            LocalTime previousTime = slotTimes.putIfAbsent(slotKey, item.serviceTime());
            if (previousTime != null && !previousTime.equals(item.serviceTime())) {
                throw ApiException.badRequest("INCONSISTENT_MEAL_SLOT_TIME", "Meals in one slot must use the same service time");
            }
            try {
                MenuItem menuItem = catalogClient.requireSellableOwnedItem(item.menuItemId(), chefIdentityId);
                prepared.add(new PreparedScheduleItem(
                    item,
                    menuItem.itemName().trim(),
                    trim(menuItem.category()),
                    trim(menuItem.foodType()),
                    menuItem.price(),
                    menuItem.currency().trim().toUpperCase(Locale.ROOT)
                ));
            } catch (org.springframework.web.server.ResponseStatusException exception) {
                throw ApiException.conflict("MENU_ITEM_NOT_AVAILABLE", exception.getReason() == null ? "Menu item is not available for this Chef" : exception.getReason());
            }
        }
        return List.copyOf(prepared);
    }

    private static void requireScheduleMatchesPlan(PlanOwner plan, PlanScheduleResponse schedule) {
        if (!plan.billingPeriod().equals(schedule.recurrenceType())) {
            throw ApiException.conflict(
                "RECURRENCE_BILLING_PERIOD_MISMATCH",
                "Meal schedule frequency no longer matches the plan. Save the meal schedule again before submitting or approving."
            );
        }
    }

    private static void requireTimezone(String timezone) {
        try {
            ZoneId.of(timezone.trim());
        } catch (DateTimeException | NullPointerException exception) {
            throw ApiException.badRequest("INVALID_TIMEZONE", "timezone must be a valid IANA timezone");
        }
    }

    private static String trim(String value) {
        if (value == null) return null;
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private static void requireChef(CurrentUser user) {
        if (user == null || !user.hasRole("CHEF")) {
            throw ApiException.forbidden("ROLE_NOT_ALLOWED", "CHEF role is required to manage meal plans");
        }
    }

    private static void requireAdmin(CurrentUser user) {
        if (user == null || !user.hasAnyRole("PLATFORM_ADMIN", "SUBSCRIPTION_ADMIN")) {
            throw ApiException.forbidden("ROLE_NOT_ALLOWED", "Subscription administrator role is required to review meal plans");
        }
    }
}
