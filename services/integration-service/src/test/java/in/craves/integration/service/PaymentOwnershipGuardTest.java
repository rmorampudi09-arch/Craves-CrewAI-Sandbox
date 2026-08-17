package in.craves.integration.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class PaymentOwnershipGuardTest {
    private static final UUID CUSTOMER = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID OTHER_CUSTOMER = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID CHECKOUT = UUID.fromString("33333333-3333-4333-8333-333333333333");

    @Test
    void requiresBearerAuthorization() {
        assertThatThrownBy(() -> PaymentService.requireAuthorization(null))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> org.assertj.core.api.Assertions.assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));
        assertThatThrownBy(() -> PaymentService.requireAuthorization("Basic abc"))
            .isInstanceOf(ResponseStatusException.class);
        assertThatCode(() -> PaymentService.requireAuthorization("Bearer customer-token")).doesNotThrowAnyException();
    }

    @Test
    void hidesPaymentWhenCheckoutCustomerDoesNotMatch() {
        PaymentService.CheckoutResponse owned = new PaymentService.CheckoutResponse(CHECKOUT, CUSTOMER, "PAYMENT_PENDING", "INR", new BigDecimal("100.00"));
        PaymentService.CheckoutResponse other = new PaymentService.CheckoutResponse(CHECKOUT, OTHER_CUSTOMER, "PAYMENT_PENDING", "INR", new BigDecimal("100.00"));

        assertThatCode(() -> PaymentService.requireMatchingCustomer(CUSTOMER, owned)).doesNotThrowAnyException();
        assertThatThrownBy(() -> PaymentService.requireMatchingCustomer(CUSTOMER, other))
            .isInstanceOf(ResponseStatusException.class)
            .satisfies(error -> org.assertj.core.api.Assertions.assertThat(((ResponseStatusException) error).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }
}
