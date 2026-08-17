package in.craves.integration.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.integration.config.PaymentProviderProperties;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class CashfreeRequestSafetyTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void normalizesFirebaseIndianPhoneNumber() {
        assertThat(CashfreeRequestSafety.normalizeIndianPhone("+91 98765-43210", false))
            .isEqualTo("9876543210");
    }

    @Test
    void rejectsInvalidPhoneInProduction() {
        assertThatThrownBy(() -> CashfreeRequestSafety.normalizeIndianPhone("12345", false))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
            );
    }

    @Test
    void keepsSandboxConvenientWithoutWeakeningProduction() {
        assertThat(CashfreeRequestSafety.normalizeIndianPhone("", true)).isEqualTo("9999999999");
    }

    @Test
    void productionReturnUrlMustRemainOnConfiguredCravesDomain() {
        PaymentProviderProperties provider = provider("production");

        assertThat(CashfreeRequestSafety.safeReturnUrl(
            provider,
            "https://www.craves.in/checkout/123/payment"
        )).isEqualTo("https://www.craves.in/checkout/123/payment");

        assertThatThrownBy(() -> CashfreeRequestSafety.safeReturnUrl(
            provider,
            "https://attacker.example/payment-complete"
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception ->
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST)
        );
    }

    @Test
    void rejectsProviderMoneyMismatch() {
        assertThatThrownBy(() -> CashfreeRequestSafety.requireMoney(
            new BigDecimal("199.00"),
            "INR",
            new BigDecimal("198.00"),
            "INR",
            "Cashfree payment"
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception ->
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT)
        );
    }

    @Test
    void validatesCreateOrderIdentityAmountAndCurrency() throws Exception {
        CashfreeRequestSafety.requireCreateOrderResponse(
            objectMapper.readTree("""
                {
                  "order_id":"CRV_123",
                  "cf_order_id":"987654",
                  "payment_session_id":"session-1",
                  "order_amount":199.00,
                  "order_currency":"INR"
                }
                """),
            "CRV_123",
            new BigDecimal("199.00"),
            "INR"
        );
    }

    @Test
    void rejectsIncompleteCreateOrderResponse() throws Exception {
        assertThatThrownBy(() -> CashfreeRequestSafety.requireCreateOrderResponse(
            objectMapper.readTree("{\"order_id\":\"CRV_123\"}"),
            "CRV_123",
            new BigDecimal("199.00"),
            "INR"
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception ->
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY)
        );
    }

    private static PaymentProviderProperties provider(String environment) {
        return new PaymentProviderProperties(
            environment,
            "production".equalsIgnoreCase(environment),
            false,
            "2025-01-01",
            "client-id",
            "client-secret",
            "https://sandbox.cashfree.com",
            "https://api.cashfree.com",
            "https://craves.in/payment/return",
            "https://api.craves.in/api/v1/payments/webhooks/cashfree",
            "",
            300,
            "2025-01-01,2023-08-01"
        );
    }
}
