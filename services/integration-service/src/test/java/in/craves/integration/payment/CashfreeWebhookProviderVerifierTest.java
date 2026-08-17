package in.craves.integration.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.integration.config.PaymentProviderProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

class CashfreeWebhookProviderVerifierTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void verifiesSuccessfulWebhookAgainstCashfreePaymentApi() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CashfreeWebhookProviderVerifier verifier = new CashfreeWebhookProviderVerifier(provider(), builder);

        server.expect(requestTo("https://sandbox.cashfree.com/pg/orders/CRV_ORDER_1/payments/123456"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("x-client-id", "client-id"))
            .andExpect(header("x-client-secret", "client-secret"))
            .andExpect(header("x-api-version", "2025-01-01"))
            .andRespond(withSuccess(
                providerPayment("SUCCESS", "125.50", "INR", "125.50", "INR"),
                MediaType.APPLICATION_JSON
            ));

        verifier.verifySuccessfulPayment(objectMapper.readTree(successWebhook("125.50", "INR")));

        server.verify();
    }

    @Test
    void rejectsWebhookWhenProviderDoesNotConfirmSuccess() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CashfreeWebhookProviderVerifier verifier = new CashfreeWebhookProviderVerifier(provider(), builder);

        server.expect(requestTo("https://sandbox.cashfree.com/pg/orders/CRV_ORDER_1/payments/123456"))
            .andRespond(withSuccess(
                providerPayment("FAILED", "125.50", "INR", "125.50", "INR"),
                MediaType.APPLICATION_JSON
            ));

        assertThatThrownBy(() -> verifier.verifySuccessfulPayment(
            objectMapper.readTree(successWebhook("125.50", "INR"))
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception ->
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT)
        );

        server.verify();
    }

    @Test
    void rejectsVerifiedPaymentAmountMismatch() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CashfreeWebhookProviderVerifier verifier = new CashfreeWebhookProviderVerifier(provider(), builder);

        server.expect(requestTo("https://sandbox.cashfree.com/pg/orders/CRV_ORDER_1/payments/123456"))
            .andRespond(withSuccess(
                providerPayment("SUCCESS", "100.00", "INR", "125.50", "INR"),
                MediaType.APPLICATION_JSON
            ));

        assertThatThrownBy(() -> verifier.verifySuccessfulPayment(
            objectMapper.readTree(successWebhook("125.50", "INR"))
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception ->
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT)
        );

        server.verify();
    }

    @Test
    void rejectsProviderPaymentThatDoesNotMatchProviderOrderTotal() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CashfreeWebhookProviderVerifier verifier = new CashfreeWebhookProviderVerifier(provider(), builder);

        server.expect(requestTo("https://sandbox.cashfree.com/pg/orders/CRV_ORDER_1/payments/123456"))
            .andRespond(withSuccess(
                providerPayment("SUCCESS", "100.00", "INR", "125.50", "INR"),
                MediaType.APPLICATION_JSON
            ));

        assertThatThrownBy(() -> verifier.verifySuccessfulPayment(
            objectMapper.readTree(successWebhook("100.00", "INR"))
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception ->
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT)
        );

        server.verify();
    }

    @Test
    void doesNotCallProviderForNonSuccessWebhook() throws Exception {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CashfreeWebhookProviderVerifier verifier = new CashfreeWebhookProviderVerifier(provider(), builder);

        verifier.verifySuccessfulPayment(objectMapper.readTree(
            successWebhook("125.50", "INR").replace("SUCCESS", "FAILED")
        ));

        server.verify();
    }

    private static String successWebhook(String amount, String currency) {
        return """
            {
              "data": {
                "order": {"order_id":"CRV_ORDER_1"},
                "payment": {
                  "cf_payment_id":"123456",
                  "payment_status":"SUCCESS",
                  "payment_amount":%s,
                  "payment_currency":"%s"
                }
              }
            }
            """.formatted(amount, currency);
    }

    private static String providerPayment(
        String paymentStatus,
        String paymentAmount,
        String paymentCurrency,
        String orderAmount,
        String orderCurrency
    ) {
        return """
            {
              "order_id":"CRV_ORDER_1",
              "cf_payment_id":"123456",
              "payment_status":"%s",
              "payment_amount":%s,
              "payment_currency":"%s",
              "order_amount":%s,
              "order_currency":"%s"
            }
            """.formatted(paymentStatus, paymentAmount, paymentCurrency, orderAmount, orderCurrency);
    }

    private static PaymentProviderProperties provider() {
        return new PaymentProviderProperties(
            "sandbox",
            false,
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
