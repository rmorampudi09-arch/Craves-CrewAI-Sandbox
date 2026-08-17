package in.craves.subscription.schedule;

import in.craves.subscription.exception.ApiException;
import in.craves.subscription.schedule.PlanScheduleModels.PlanScheduleResponse;
import in.craves.subscription.schedule.PlanScheduleModels.PublicPlanScheduleResponse;
import in.craves.subscription.schedule.PlanScheduleModels.PublicScheduleItemResponse;
import in.craves.subscription.schedule.PlanScheduleRepository.PlanOwner;
import in.craves.subscription.security.CurrentUser;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class PlanScheduleService {
    private final PlanScheduleRepository repository;

    public PlanScheduleService(PlanScheduleRepository repository) {
        this.repository = repository;
    }

    public PublicPlanScheduleResponse getPublicActive(UUID planId) {
        PlanOwner plan = repository.findPlanOwner(planId)
            .orElseThrow(() -> ApiException.notFound("PLAN_NOT_FOUND", "Subscription plan was not found"));
        if (!"ACTIVE".equals(plan.status())) {
            throw ApiException.notFound("PLAN_NOT_FOUND", "Subscription plan was not found");
        }
        PlanScheduleResponse schedule = repository.findActive(planId)
            .orElseThrow(() -> ApiException.notFound("PLAN_SCHEDULE_NOT_FOUND", "Active meal schedule was not found"));
        return new PublicPlanScheduleResponse(
            schedule.planId(),
            schedule.recurrenceType(),
            schedule.timezone(),
            schedule.items().stream().map(item -> new PublicScheduleItemResponse(
                item.menuItemId(),
                item.menuItemName(),
                item.menuItemCategory(),
                item.menuItemFoodType(),
                item.menuItemPrice(),
                item.menuItemCurrency(),
                item.quantity(),
                item.isoDayOfWeek(),
                item.dayOfMonth(),
                item.mealSlotCode(),
                item.serviceTime(),
                item.sequenceNumber()
            )).toList()
        );
    }

    public PlanScheduleResponse get(UUID planId, CurrentUser user) {
        PlanOwner plan = requireAdminManagedPlan(planId, user);
        return repository.find(plan.planId())
            .orElseThrow(() -> ApiException.notFound("PLAN_SCHEDULE_NOT_FOUND", "Plan schedule was not found"));
    }

    private PlanOwner requireAdminManagedPlan(UUID planId, CurrentUser user) {
        requireAdmin(user);
        return repository.findPlanOwner(planId)
            .orElseThrow(() -> ApiException.notFound("PLAN_NOT_FOUND", "Subscription plan was not found"));
    }

    private static void requireAdmin(CurrentUser user) {
        if (user == null || !user.hasAnyRole("PLATFORM_ADMIN", "SUBSCRIPTION_ADMIN")) {
            throw ApiException.forbidden("ROLE_NOT_ALLOWED", "Subscription administration role is required");
        }
    }
}
