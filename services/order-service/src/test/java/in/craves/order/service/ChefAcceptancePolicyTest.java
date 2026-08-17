package in.craves.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.craves.order.exception.OrderApiException;
import in.craves.order.service.ChefAcceptancePolicy.Decision;
import in.craves.order.web.ApiDtos.OrderStatus;
import org.junit.jupiter.api.Test;

class ChefAcceptancePolicyTest {
    @Test
    void acceptsOnlyFromChefAcceptancePending() {
        Decision decision = ChefAcceptancePolicy.decide(
            OrderStatus.CHEF_ACCEPTANCE_PENDING,
            null,
            35
        );

        assertThat(decision).isEqualTo(Decision.ACCEPT);
    }

    @Test
    void returnsIdempotentSuccessForSamePreparationTime() {
        Decision decision = ChefAcceptancePolicy.decide(
            OrderStatus.CHEF_ACCEPTED,
            35,
            35
        );

        assertThat(decision).isEqualTo(Decision.IDEMPOTENT_SUCCESS);
    }

    @Test
    void rejectsRepeatedAcceptanceWithDifferentPreparationTime() {
        assertThatThrownBy(() -> ChefAcceptancePolicy.decide(
            OrderStatus.CHEF_ACCEPTED,
            35,
            45
        ))
            .isInstanceOf(OrderApiException.class)
            .satisfies(exception -> assertThat(((OrderApiException) exception).code())
                .isEqualTo("ORDER_ALREADY_ACCEPTED"));
    }

    @Test
    void rejectsPaymentPendingAndPaidStates() {
        assertNotWaiting(OrderStatus.PAYMENT_PENDING);
        assertNotWaiting(OrderStatus.PAID);
    }

    @Test
    void rejectsNonPositivePreparationTime() {
        assertThatThrownBy(() -> ChefAcceptancePolicy.decide(
            OrderStatus.CHEF_ACCEPTANCE_PENDING,
            null,
            0
        ))
            .isInstanceOf(OrderApiException.class)
            .satisfies(exception -> assertThat(((OrderApiException) exception).code())
                .isEqualTo("PREPARATION_TIME_REQUIRED"));
    }

    private static void assertNotWaiting(OrderStatus status) {
        assertThatThrownBy(() -> ChefAcceptancePolicy.decide(status, null, 35))
            .isInstanceOf(OrderApiException.class)
            .satisfies(exception -> assertThat(((OrderApiException) exception).code())
                .isEqualTo("ORDER_NOT_WAITING_FOR_CHEF_ACCEPTANCE"));
    }
}
