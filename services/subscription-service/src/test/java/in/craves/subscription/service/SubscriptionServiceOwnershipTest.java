package in.craves.subscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import in.craves.subscription.capacity.CapacityService;
import in.craves.subscription.exception.ApiException;
import in.craves.subscription.repository.SubscriptionRepository;
import in.craves.subscription.security.CurrentUser;
import in.craves.subscription.web.ApiDtos.PlanResponse;
import in.craves.subscription.web.ApiDtos.SubscriptionResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SubscriptionServiceOwnershipTest {
    private static final UUID CHEF_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID CUSTOMER_ID = UUID.fromString("33333333-3333-4333-8333-333333333333");

    private final SubscriptionRepository repository = mock(SubscriptionRepository.class);
    private final CapacityService capacityService = mock(CapacityService.class);
    private final SubscriptionService service = new SubscriptionService(repository, capacityService);

    @Test
    void chefCannotUseAdministratorPlanListing() {
        CurrentUser chef = new CurrentUser(CHEF_ID, "firebase-chef", "+919999999999", List.of("CHEF"));

        assertThatThrownBy(() -> service.listAllPlans(chef))
            .isInstanceOf(ApiException.class)
            .extracting(error -> ((ApiException) error).getCode())
            .isEqualTo("ROLE_NOT_ALLOWED");
    }

    @Test
    void publicPlanDoesNotExposeChefIdentityWhenCapacityIsBookable() {
        UUID planId = UUID.fromString("44444444-4444-4444-8444-444444444444");
        PlanResponse stored = plan(CHEF_ID, "ACTIVE");
        when(repository.findActivePlanById(planId)).thenReturn(Optional.of(stored));
        when(capacityService.isPlanBookable(stored)).thenReturn(true);

        Object publicPlan = service.getPlan(planId);

        assertThat(publicPlan).hasNoNullFieldsOrPropertiesExcept("description");
        assertThat(publicPlan.toString()).doesNotContain(CHEF_ID.toString());
    }

    @Test
    void soldOutPlanIsHiddenFromPublicDetail() {
        UUID planId = UUID.fromString("44444444-4444-4444-8444-444444444444");
        PlanResponse stored = plan(CHEF_ID, "ACTIVE");
        when(repository.findActivePlanById(planId)).thenReturn(Optional.of(stored));
        when(capacityService.isPlanBookable(stored)).thenReturn(false);

        assertThatThrownBy(() -> service.getPlan(planId))
            .isInstanceOf(ApiException.class)
            .extracting(error -> ((ApiException) error).getCode())
            .isEqualTo("PLAN_NOT_BOOKABLE");
    }

    @Test
    void customerSubscriptionResponseDoesNotExposeIdentityIds() {
        CurrentUser customer = new CurrentUser(CUSTOMER_ID, "firebase-customer", "+918888888888", List.of("CUSTOMER"));
        UUID subscriptionId = UUID.fromString("55555555-5555-4555-8555-555555555555");
        SubscriptionResponse stored = new SubscriptionResponse(
            subscriptionId,
            CUSTOMER_ID,
            UUID.fromString("44444444-4444-4444-8444-444444444444"),
            CHEF_ID,
            "ACTIVE",
            LocalDate.now(),
            null,
            LocalDate.now().plusDays(1),
            UUID.fromString("66666666-6666-4666-8666-666666666666"),
            null,
            Instant.parse("2026-07-30T00:00:00Z"),
            Instant.parse("2026-07-30T00:00:00Z")
        );
        when(repository.findSubscriptionById(subscriptionId)).thenReturn(Optional.of(stored));

        Object response = service.getMine(subscriptionId, customer);

        assertThat(response.toString()).doesNotContain(CUSTOMER_ID.toString(), CHEF_ID.toString());
    }

    private static PlanResponse plan(UUID chefIdentityId, String status) {
        return new PlanResponse(
            UUID.fromString("44444444-4444-4444-8444-444444444444"),
            "WEEKLY-01",
            chefIdentityId,
            "Weekly meals",
            "Chef plan",
            "WEEKLY",
            new BigDecimal("1200.00"),
            "INR",
            status,
            Instant.parse("2026-07-30T00:00:00Z"),
            Instant.parse("2026-07-30T00:00:00Z")
        );
    }
}
