package in.craves.subscription.plan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.craves.subscription.capacity.CapacityService;
import in.craves.subscription.exception.ApiException;
import in.craves.subscription.plan.ChefPlanModels.ChefPlanInput;
import in.craves.subscription.plan.ChefPlanModels.ChefPlanResponse;
import in.craves.subscription.plan.ChefPlanModels.ReviewChefPlanRequest;
import in.craves.subscription.policy.DefaultSubscriptionPolicyService;
import in.craves.subscription.repository.SubscriptionRepository;
import in.craves.subscription.schedule.PlanScheduleModels.PlanScheduleResponse;
import in.craves.subscription.security.CurrentUser;
import in.craves.subscription.web.ApiDtos.PlanResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ChefPlanWorkflowServiceTest {
    private static final UUID CHEF_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID PLAN_ID = UUID.fromString("44444444-4444-4444-8444-444444444444");
    private static final UUID ADMIN_ID = UUID.fromString("77777777-7777-4777-8777-777777777777");

    private final ChefPlanRepository repository = mock(ChefPlanRepository.class);
    private final ChefPlanScheduleService schedules = mock(ChefPlanScheduleService.class);
    private final DefaultSubscriptionPolicyService defaultPolicy = mock(DefaultSubscriptionPolicyService.class);
    private final SubscriptionRepository subscriptionRepository = mock(SubscriptionRepository.class);
    private final CapacityService capacity = mock(CapacityService.class);
    private final ChefPlanWorkflowService service = new ChefPlanWorkflowService(repository, schedules, defaultPolicy, subscriptionRepository, capacity);

    @Test
    void chefCreatesPlanForOwnIdentityAndServerGeneratesCode() {
        CurrentUser chef = new CurrentUser(CHEF_ID, "firebase-chef", "+919999999999", List.of("CHEF"));
        ChefPlanInput input = new ChefPlanInput("Weekly lunches", "Home lunches", "WEEKLY", new BigDecimal("1200.00"), "INR");
        when(repository.create(eq(CHEF_ID), any(String.class), any(ChefPlanInput.class)))
            .thenAnswer(invocation -> plan("DRAFT", invocation.getArgument(1)));

        ChefPlanResponse response = service.create(input, chef);

        assertThat(response.planCode()).startsWith("MEAL-");
        verify(repository).create(eq(CHEF_ID), any(String.class), any(ChefPlanInput.class));
        verify(defaultPolicy).ensureActiveDefault(PLAN_ID, CHEF_ID);
    }

    @Test
    void nonChefCannotCreateMealPlan() {
        CurrentUser customer = new CurrentUser(CHEF_ID, "firebase-customer", "+919999999999", List.of("CUSTOMER"));
        ChefPlanInput input = new ChefPlanInput("Weekly lunches", null, "WEEKLY", new BigDecimal("1200.00"), "INR");

        assertThatThrownBy(() -> service.create(input, customer))
            .isInstanceOf(ApiException.class)
            .extracting(error -> ((ApiException) error).getCode())
            .isEqualTo("ROLE_NOT_ALLOWED");
    }

    @Test
    void submitRequiresChefOwnedReadySchedule() {
        CurrentUser chef = new CurrentUser(CHEF_ID, "firebase-chef", "+919999999999", List.of("CHEF"));
        when(repository.requireOwned(PLAN_ID, CHEF_ID)).thenReturn(plan("DRAFT", "MEAL-ABC"));
        when(repository.submit(PLAN_ID, CHEF_ID, "Ready")).thenReturn(plan("PENDING_APPROVAL", "MEAL-ABC"));
        when(schedules.requireReadyForSubmission(PLAN_ID, chef)).thenReturn(mock(PlanScheduleResponse.class));

        ChefPlanResponse response = service.submit(PLAN_ID, "Ready", chef);

        assertThat(response.status()).isEqualTo("PENDING_APPROVAL");
        verify(schedules).requireReadyForSubmission(PLAN_ID, chef);
        verify(repository).submit(PLAN_ID, CHEF_ID, "Ready");
    }

    @Test
    void adminRejectsSubmittedPlanWithoutAuthoringMeals() {
        CurrentUser admin = new CurrentUser(ADMIN_ID, "firebase-admin", "+919000000000", List.of("SUBSCRIPTION_ADMIN"));
        when(repository.find(PLAN_ID)).thenReturn(Optional.of(plan("PENDING_APPROVAL", "MEAL-ABC")));
        when(repository.review(PLAN_ID, ADMIN_ID, false, "Change lunch timing")).thenReturn(plan("REJECTED", "MEAL-ABC"));

        ChefPlanResponse response = service.review(PLAN_ID, new ReviewChefPlanRequest("REJECT", "Change lunch timing"), admin);

        assertThat(response.status()).isEqualTo("REJECTED");
        verify(repository).review(PLAN_ID, ADMIN_ID, false, "Change lunch timing");
    }

    @Test
    void adminApprovalAutomaticallyEnsuresSafePlatformPolicy() {
        CurrentUser admin = new CurrentUser(ADMIN_ID, "firebase-admin", "+919000000000", List.of("PLATFORM_ADMIN"));
        when(repository.find(PLAN_ID)).thenReturn(Optional.of(plan("PENDING_APPROVAL", "MEAL-ABC")));
        when(schedules.activateSubmittedDraft(PLAN_ID, admin)).thenReturn(mock(PlanScheduleResponse.class));
        when(repository.review(PLAN_ID, ADMIN_ID, true, "Approved for launch")).thenReturn(plan("ACTIVE", "MEAL-ABC"));
        PlanResponse active = activePlan();
        when(subscriptionRepository.findPlanById(PLAN_ID)).thenReturn(Optional.of(active));
        when(capacity.isPlanBookable(active)).thenReturn(true);

        ChefPlanResponse response = service.review(PLAN_ID, new ReviewChefPlanRequest("APPROVE", "Approved for launch"), admin);

        assertThat(response.status()).isEqualTo("ACTIVE");
        verify(defaultPolicy).ensureActiveDefault(PLAN_ID, ADMIN_ID);
        verify(schedules).activateSubmittedDraft(PLAN_ID, admin);
    }

    @Test
    void approvalRequiresBookableChefCapacityAfterMealRevalidation() {
        CurrentUser admin = new CurrentUser(ADMIN_ID, "firebase-admin", "+919000000000", List.of("SUBSCRIPTION_ADMIN"));
        when(repository.find(PLAN_ID)).thenReturn(Optional.of(plan("PENDING_APPROVAL", "MEAL-ABC")));
        when(schedules.activateSubmittedDraft(PLAN_ID, admin)).thenReturn(mock(PlanScheduleResponse.class));
        when(repository.review(PLAN_ID, ADMIN_ID, true, "Approved for launch")).thenReturn(plan("ACTIVE", "MEAL-ABC"));
        PlanResponse active = activePlan();
        when(subscriptionRepository.findPlanById(PLAN_ID)).thenReturn(Optional.of(active));
        when(capacity.isPlanBookable(active)).thenReturn(false);

        assertThatThrownBy(() -> service.review(PLAN_ID, new ReviewChefPlanRequest("APPROVE", "Approved for launch"), admin))
            .isInstanceOf(ApiException.class)
            .extracting(error -> ((ApiException) error).getCode())
            .isEqualTo("PLAN_CAPACITY_NOT_READY");

        verify(defaultPolicy).ensureActiveDefault(PLAN_ID, ADMIN_ID);
        verify(schedules).activateSubmittedDraft(PLAN_ID, admin);
    }

    private static PlanResponse activePlan() {
        return new PlanResponse(
            PLAN_ID, "MEAL-ABC", CHEF_ID, "Weekly lunches", null, "WEEKLY",
            new BigDecimal("1200.00"), "INR", "ACTIVE", Instant.now(), Instant.now()
        );
    }

    private static ChefPlanResponse plan(String status, String code) {
        return new ChefPlanResponse(
            PLAN_ID, code, "Weekly lunches", "Home lunches", "WEEKLY", new BigDecimal("1200.00"), "INR",
            status, null, null, null, Instant.parse("2026-08-13T00:00:00Z"), Instant.parse("2026-08-13T00:00:00Z")
        );
    }
}
