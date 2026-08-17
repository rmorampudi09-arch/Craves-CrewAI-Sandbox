package in.craves.integration.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.integration.config.PaymentProviderProperties;
import in.craves.integration.config.PaymentRoutingProperties;
import in.craves.integration.payment.RazorpayPaymentClient;
import in.craves.integration.subscription.SubscriptionPaymentModels.CreateSubscriptionPaymentOrderRequest;
import in.craves.integration.subscription.SubscriptionPaymentModels.SubscriptionPaymentResponse;
import in.craves.integration.subscription.SubscriptionPaymentRepository.PaymentIntent;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SubscriptionPaymentServiceOwnershipTest {
    private static final UUID SUBSCRIPTION_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String AUTHORIZATION = "Bearer customer-token";

    @Mock
    private SubscriptionPaymentRepository repository;
    @Mock
    private RazorpayPaymentClient razorpayPaymentClient;

    private MockRestServiceServer server;
    private SubscriptionPaymentService service;

    @BeforeEach
    void setUp() {
        SubscriptionPaymentProperties paymentProperties = new SubscriptionPaymentProperties();
        paymentProperties.setSubscriptionServiceBaseUrl("https://subscription.test");

        PaymentProviderProperties providerProperties = new PaymentProviderProperties(
            "sandbox",
            false,
            false,
            "2025-01-01",
            "sandbox-client-id",
            "sandbox-client-key",
            "https://sandbox.cashfree.com",
            "https://api.cashfree.com",
            "https://craves.in/payment/return",
            "https://api.craves.in/api/v1/payments/webhooks/cashfree",
            "",
            300,
            "2025-01-01"
        );

        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new SubscriptionPaymentService(
            repository,
            paymentProperties,
            providerProperties,
            new PaymentRoutingProperties("CASHFREE", true, false),
            razorpayPaymentClient,
            new ObjectMapper().findAndRegisterModules(),
            builder
        );
    }

    @Test
    void latestPaymentRequiresBearerBeforeRepositoryLookup() {
        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.getLatestOwned(null, SUBSCRIPTION_ID)
        );

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verifyNoInteractions(repository);
    }

    @Test
    void latestPaymentValidatesSubscriptionOwnershipBeforeDisclosingInvoiceExistence() {
        server.expect(requestTo("https://subscription.test/api/v1/subscriptions/" + SUBSCRIPTION_ID))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", AUTHORIZATION))
            .andRespond(withStatus(HttpStatus.NOT_FOUND));

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.getLatestOwned(AUTHORIZATION, SUBSCRIPTION_ID)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(repository, never()).findLatestBySubscription(SUBSCRIPTION_ID);
        server.verify();
    }

    @Test
    void latestPaymentReturnsNotFoundWhileAsynchronousInvoiceIsNotReady() {
        server.expect(requestTo("https://subscription.test/api/v1/subscriptions/" + SUBSCRIPTION_ID))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", AUTHORIZATION))
            .andRespond(withSuccess());
        when(repository.findLatestBySubscription(SUBSCRIPTION_ID)).thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> service.getLatestOwned(AUTHORIZATION, SUBSCRIPTION_ID)
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
        verify(repository).findLatestBySubscription(SUBSCRIPTION_ID);
        server.verify();
    }

    @Test
    void latestPaymentReturnsOwnedLatestIntent() {
        UUID intentId = UUID.fromString("22222222-2222-4222-8222-222222222222");
        UUID invoiceId = UUID.fromString("33333333-3333-4333-8333-333333333333");
        PaymentIntent intent = paymentIntent(intentId, invoiceId, "PAYMENT_REQUESTED", null, null);
        SubscriptionPaymentResponse response = paymentResponse(intentId, invoiceId, "PAYMENT_REQUESTED", null);

        server.expect(requestTo("https://subscription.test/api/v1/subscriptions/" + SUBSCRIPTION_ID))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", AUTHORIZATION))
            .andRespond(withSuccess());
        when(repository.findLatestBySubscription(SUBSCRIPTION_ID)).thenReturn(Optional.of(intent));
        when(repository.response(intent)).thenReturn(response);

        SubscriptionPaymentResponse actual = service.getLatestOwned(AUTHORIZATION, SUBSCRIPTION_ID);

        assertSame(response, actual);
        server.verify();
    }

    @Test
    void pendingPaymentReconcilesSuccessfulCashfreePayment() {
        UUID intentId = UUID.fromString("22222222-2222-4222-8222-222222222222");
        UUID invoiceId = UUID.fromString("33333333-3333-4333-8333-333333333333");
        String orderId = "CRVSUB_33333333333343338333333333333333";
        PaymentIntent pending = paymentIntent(intentId, invoiceId, "PAYMENT_PENDING", orderId, "session_pending");
        PaymentIntent paid = paymentIntent(intentId, invoiceId, "PAID", orderId, "session_pending");
        SubscriptionPaymentResponse response = paymentResponse(intentId, invoiceId, "PAID", "session_pending");

        server.expect(requestTo("https://subscription.test/api/v1/subscriptions/" + SUBSCRIPTION_ID))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", AUTHORIZATION))
            .andRespond(withSuccess());
        server.expect(requestTo("https://sandbox.cashfree.com/pg/orders/" + orderId + "/payments"))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("x-client-id", "sandbox-client-id"))
            .andExpect(header("x-client-secret", "sandbox-client-key"))
            .andExpect(header("x-api-version", "2025-01-01"))
            .andRespond(withSuccess(
                "[{\"cf_payment_id\":\"12376123\",\"payment_status\":\"SUCCESS\",\"payment_amount\":1499.00,\"payment_currency\":\"INR\"}]",
                MediaType.APPLICATION_JSON
            ));

        when(repository.findByInvoice(invoiceId)).thenReturn(Optional.of(pending), Optional.of(paid));
        when(repository.applyProviderStatus(
            eq(pending),
            eq("PAID"),
            eq("SUCCESS"),
            eq("12376123"),
            any(JsonNode.class)
        )).thenReturn(true);
        when(repository.response(paid)).thenReturn(response);

        SubscriptionPaymentResponse actual = service.getOwned(AUTHORIZATION, invoiceId);

        assertSame(response, actual);
        verify(repository).applyProviderStatus(
            eq(pending),
            eq("PAID"),
            eq("SUCCESS"),
            eq("12376123"),
            any(JsonNode.class)
        );
        server.verify();
    }

    @Test
    void failedPaymentReusesExistingCashfreeSessionForCustomerRetry() {
        UUID intentId = UUID.fromString("66666666-6666-4666-8666-666666666666");
        UUID invoiceId = UUID.fromString("77777777-7777-4777-8777-777777777777");
        PaymentIntent intent = paymentIntent(
            intentId,
            invoiceId,
            "FAILED",
            "CRVSUB_77777777777747778777777777777777",
            "session_retry_existing_order"
        );
        SubscriptionPaymentResponse response = paymentResponse(
            intentId,
            invoiceId,
            "FAILED",
            "session_retry_existing_order"
        );

        server.expect(requestTo("https://subscription.test/api/v1/subscriptions/" + SUBSCRIPTION_ID))
            .andExpect(method(HttpMethod.GET))
            .andExpect(header("Authorization", AUTHORIZATION))
            .andRespond(withSuccess());
        when(repository.findByInvoice(invoiceId)).thenReturn(Optional.of(intent));
        when(repository.response(intent)).thenReturn(response);

        SubscriptionPaymentResponse actual = service.createProviderOrder(
            AUTHORIZATION,
            invoiceId,
            new CreateSubscriptionPaymentOrderRequest(
                "Craves Customer",
                "9876543210",
                "customer@example.com",
                "https://craves.in/subscriptions/" + SUBSCRIPTION_ID + "/payment"
            )
        );

        assertSame(response, actual);
        server.verify();
    }

    private static PaymentIntent paymentIntent(
        UUID intentId,
        UUID invoiceId,
        String status,
        String cashfreeOrderId,
        String paymentSessionId
    ) {
        return new PaymentIntent(
            intentId,
            invoiceId,
            SUBSCRIPTION_ID,
            UUID.fromString("44444444-4444-4444-8444-444444444444"),
            UUID.fromString("55555555-5555-4555-8555-555555555555"),
            null,
            LocalDate.of(2026, 8, 12),
            LocalDate.of(2026, 9, 12),
            new BigDecimal("1499.00"),
            "INR",
            status,
            cashfreeOrderId,
            null,
            paymentSessionId,
            status.equals("FAILED") ? "FAILED" : status.equals("PAID") ? "SUCCESS" : null,
            Instant.parse("2026-08-12T06:30:00Z"),
            Instant.parse("2026-08-12T06:30:00Z"),
            status.equals("PAID") ? Instant.parse("2026-08-12T06:31:00Z") : null
        );
    }

    private static SubscriptionPaymentResponse paymentResponse(
        UUID intentId,
        UUID invoiceId,
        String status,
        String paymentSessionId
    ) {
        return new SubscriptionPaymentResponse(
            intentId,
            invoiceId,
            SUBSCRIPTION_ID,
            LocalDate.of(2026, 8, 12),
            LocalDate.of(2026, 9, 12),
            new BigDecimal("1499.00"),
            "INR",
            status,
            paymentSessionId,
            status.equals("FAILED") ? "FAILED" : status.equals("PAID") ? "SUCCESS" : null,
            Instant.parse("2026-08-12T06:30:00Z"),
            Instant.parse("2026-08-12T06:30:00Z"),
            status.equals("PAID") ? Instant.parse("2026-08-12T06:31:00Z") : null
        );
    }
}
