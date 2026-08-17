package in.craves.subscription.plan;

import in.craves.subscription.capacity.CapacityRepository;
import in.craves.subscription.capacity.CapacityService;
import in.craves.subscription.exception.ApiException;
import in.craves.subscription.plan.ChefPlanModels.ChefPlanInput;
import in.craves.subscription.plan.ChefPlanModels.ChefPlanResponse;
import in.craves.subscription.plan.ChefPlanModels.ReviewChefPlanRequest;
import in.craves.subscription.policy.DefaultSubscriptionPolicyService;
import in.craves.subscription.repository.SubscriptionRepository;
import in.craves.subscription.schedule.PlanScheduleModels.PlanScheduleResponse;
import in.craves.subscription.schedule.PlanScheduleModels.ScheduleItemResponse;
import in.craves.subscription.schedule.PlanScheduleRepository;
import in.craves.subscription.security.CurrentUser;
import in.craves.subscription.web.ApiDtos.PlanResponse;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ChefPlanWorkflowService {
    private static final Set<String> BILLING_PERIODS = Set.of("WEEKLY", "MONTHLY");
    private static final Set<String> REVIEW_DECISIONS = Set.of("APPROVE", "REJECT");
    private static final int DEFAULT_DISH_SUBSCRIPTION_UNITS = 5;

    private final ChefPlanRepository repository;
    private final ChefPlanScheduleService scheduleService;
    private final DefaultSubscriptionPolicyService defaultPolicyService;
    private final SubscriptionRepository subscriptionRepository;
    private final CapacityService capacityService;

    private CapacityRepository capacityRepository;
    private PlanScheduleRepository planScheduleRepository;

    public ChefPlanWorkflowService(
        ChefPlanRepository repository,
        ChefPlanScheduleService scheduleService,
        DefaultSubscriptionPolicyService defaultPolicyService,
        SubscriptionRepository subscriptionRepository,
        CapacityService capacityService
    ) {
        this.repository = repository;
        this.scheduleService = scheduleService;
        this.defaultPolicyService = defaultPolicyService;
        this.subscriptionRepository = subscriptionRepository;
        this.capacityService = capacityService;
    }

    @Autowired
    void configureAutomaticCapacity(
        CapacityRepository capacityRepository,
        PlanScheduleRepository planScheduleRepository
    ) {
        this.capacityRepository = capacityRepository;
        this.planScheduleRepository = planScheduleRepository;
    }

    @Transactional
    public ChefPlanResponse create(ChefPlanInput input, CurrentUser user) {
        requireChef(user);
        ChefPlanInput normalized = normalize(input);
        String planCode = "MEAL-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase(Locale.ROOT);
        ChefPlanResponse created = repository.create(user.identityId(), planCode, normalized);
        defaultPolicyService.ensureActiveDefault(created.id(), user.identityId());
        return created;
    }

    public List<ChefPlanResponse> listMine(CurrentUser user) {
        requireChef(user);
        return repository.listOwned(user.identityId());
    }

    public ChefPlanResponse getMine(UUID planId, CurrentUser user) {
        requireChef(user);
        return repository.requireOwned(planId, user.identityId());
    }

    public ChefPlanResponse update(UUID planId, ChefPlanInput input, CurrentUser user) {
        requireChef(user);
        return repository.update(planId, user.identityId(), normalize(input));
    }

    @Transactional
    public ChefPlanResponse submit(UUID planId, String note, CurrentUser user) {
        requireChef(user);
        ChefPlanResponse plan = repository.requireOwned(planId, user.identityId());
        if (!Set.of("DRAFT", "REJECTED").contains(plan.status())) {
            throw ApiException.conflict("PLAN_NOT_SUBMITTABLE", "Only draft or rejected plans can be submitted for approval");
        }
        scheduleService.requireReadyForSubmission(planId, user);
        ensureMissingCapacityDefaults(planId, user.identityId());
        return repository.submit(planId, user.identityId(), note);
    }

    @Transactional
    public ChefPlanResponse review(UUID planId, ReviewChefPlanRequest request, CurrentUser admin) {
        requireAdmin(admin);
        String decision = normalizeText(request.decision(), "decision");
        if (!REVIEW_DECISIONS.contains(decision)) {
            throw ApiException.badRequest("INVALID_REVIEW_DECISION", "decision must be APPROVE or REJECT");
        }
        ChefPlanResponse plan = repository.find(planId)
            .orElseThrow(() -> ApiException.notFound("PLAN_NOT_FOUND", "Subscription meal plan was not found"));
        if (!"PENDING_APPROVAL".equals(plan.status())) {
            throw ApiException.conflict("PLAN_NOT_PENDING_APPROVAL", "Only submitted meal plans can be reviewed");
        }
        if ("REJECT".equals(decision)) {
            return repository.review(planId, admin.identityId(), false, request.reason());
        }

        // Older pending plans may have been submitted before server-side defaults existed.
        // Fill only missing rules. Any Chef-configured limit/closure remains authoritative.
        ensureMissingCapacityDefaults(planId, null);

        defaultPolicyService.ensureActiveDefault(planId, admin.identityId());
        scheduleService.activateSubmittedDraft(planId, admin);
        ChefPlanResponse approved = repository.review(planId, admin.identityId(), true, request.reason());

        PlanResponse active = subscriptionRepository.findPlanById(planId)
            .orElseThrow(() -> ApiException.notFound("PLAN_NOT_FOUND", "Subscription meal plan was not found after approval"));
        if (!capacityService.isPlanBookable(active)) {
            throw ApiException.conflict(
                "PLAN_CAPACITY_NOT_READY",
                "This plan exceeds an explicit Chef capacity limit or a Chef-closed subscription slot. Review the capacity comparison before approving."
            );
        }
        return approved;
    }

    private void ensureMissingCapacityDefaults(UUID planId, UUID knownChefIdentityId) {
        if (capacityRepository == null || planScheduleRepository == null) return;

        PlanScheduleResponse schedule = planScheduleRepository.find(planId).orElse(null);
        if (schedule == null || schedule.items() == null || schedule.items().isEmpty()) return;

        UUID chefIdentityId = knownChefIdentityId;
        if (chefIdentityId == null) {
            chefIdentityId = planScheduleRepository.findPlanOwner(planId)
                .map(PlanScheduleRepository.PlanOwner::chefIdentityId)
                .orElse(null);
        }
        if (chefIdentityId == null) return;

        capacityRepository.lockChef(chefIdentityId);
        Map<DaySlot, Set<UUID>> dishesByDaySlot = new LinkedHashMap<>();

        for (ScheduleItemResponse item : schedule.items()) {
            String slot = item.mealSlotCode().trim().toUpperCase(Locale.ROOT);
            for (int weekday : requiredWeekdays(schedule.recurrenceType(), item)) {
                DaySlot daySlot = new DaySlot(weekday, slot);
                dishesByDaySlot.computeIfAbsent(daySlot, ignored -> new HashSet<>()).add(item.menuItemId());

                if (capacityRepository.findMenuRule(
                    chefIdentityId,
                    item.menuItemId(),
                    weekday,
                    slot
                ).isEmpty()) {
                    capacityRepository.upsertMenuRule(
                        chefIdentityId,
                        item.menuItemId(),
                        weekday,
                        slot,
                        DEFAULT_DISH_SUBSCRIPTION_UNITS,
                        true,
                        chefIdentityId
                    );
                }
            }
        }

        for (Map.Entry<DaySlot, Set<UUID>> entry : dishesByDaySlot.entrySet()) {
            DaySlot key = entry.getKey();
            if (capacityRepository.findSlotRule(chefIdentityId, key.weekday(), key.slot()).isPresent()) {
                continue;
            }
            int defaultSlotUnits = Math.max(
                DEFAULT_DISH_SUBSCRIPTION_UNITS,
                entry.getValue().size() * DEFAULT_DISH_SUBSCRIPTION_UNITS
            );
            capacityRepository.upsertSlotRule(
                chefIdentityId,
                key.weekday(),
                key.slot(),
                defaultSlotUnits,
                defaultSlotUnits,
                true,
                chefIdentityId
            );
        }
    }

    private static List<Integer> requiredWeekdays(String recurrenceType, ScheduleItemResponse item) {
        if ("MONTHLY".equals(recurrenceType)) {
            return List.of(1, 2, 3, 4, 5, 6, 7);
        }
        return item.isoDayOfWeek() == null ? List.of() : List.of(item.isoDayOfWeek());
    }

    private static ChefPlanInput normalize(ChefPlanInput input) {
        if (input == null) {
            throw ApiException.badRequest("PLAN_INPUT_REQUIRED", "Meal plan details are required");
        }
        String billing = normalizeText(input.billingPeriod(), "billingPeriod");
        if (!BILLING_PERIODS.contains(billing)) {
            throw ApiException.badRequest("INVALID_BILLING_PERIOD", "billingPeriod must be WEEKLY or MONTHLY");
        }
        BigDecimal amount = input.amount();
        if (amount == null || amount.signum() < 0) {
            throw ApiException.badRequest("INVALID_AMOUNT", "amount must be zero or greater");
        }
        String currency = StringUtils.hasText(input.currency()) ? input.currency().trim().toUpperCase(Locale.ROOT) : "INR";
        if (currency.length() != 3) {
            throw ApiException.badRequest("INVALID_CURRENCY", "currency must use a 3-letter code");
        }
        if (!StringUtils.hasText(input.name())) {
            throw ApiException.badRequest("PLAN_NAME_REQUIRED", "name is required");
        }
        return new ChefPlanInput(input.name().trim(), trim(input.description()), billing, amount, currency);
    }

    private static String normalizeText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw ApiException.badRequest("INVALID_" + field.toUpperCase(Locale.ROOT), field + " is required");
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String trim(String value) {
        if (value == null) return null;
        String result = value.trim();
        return result.isEmpty() ? null : result;
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

    private record DaySlot(int weekday, String slot) {}
}
