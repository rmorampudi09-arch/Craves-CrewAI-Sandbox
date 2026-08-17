package in.craves.integration.web;

import in.craves.integration.config.PaymentApiProperties;
import in.craves.integration.payment.CashfreeWebhookInboxService;
import in.craves.integration.service.PaymentService;
import in.craves.integration.web.PaymentDtos.CreatePaymentOrderRequest;
import in.craves.integration.web.PaymentDtos.CreatePaymentOrderResponse;
import in.craves.integration.web.PaymentDtos.PaymentOrderResponse;
import in.craves.integration.web.PaymentDtos.VerifyPaymentResponse;
import in.craves.integration.web.PaymentDtos.VerifyPaymentRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {
    private final PaymentService paymentService;
    private final CashfreeWebhookInboxService cashfreeWebhookInboxService;
    private final PaymentApiProperties apiProperties;

    public PaymentController(
        PaymentService paymentService,
        CashfreeWebhookInboxService cashfreeWebhookInboxService,
        PaymentApiProperties apiProperties
    ) {
        this.paymentService = paymentService;
        this.cashfreeWebhookInboxService = cashfreeWebhookInboxService;
        this.apiProperties = apiProperties;
    }

    @PostMapping("/orders")
    public CreatePaymentOrderResponse createPaymentOrder(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @Valid @RequestBody CreatePaymentOrderRequest request
    ) {
        apiProperties.requireOrderExecutionEnabled();
        return paymentService.createPaymentOrder(authorization, request);
    }

    @GetMapping("/orders/{paymentOrderId}")
    public PaymentOrderResponse getPaymentOrder(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @PathVariable UUID paymentOrderId
    ) {
        return paymentService.getPaymentOrder(authorization, paymentOrderId);
    }

    @PostMapping("/orders/{paymentOrderId}/verify")
    public VerifyPaymentResponse verifyPayment(
        @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
        @PathVariable UUID paymentOrderId,
        @RequestBody(required = false) VerifyPaymentRequest request
    ) {
        apiProperties.requireOrderExecutionEnabled();
        return paymentService.verifyPayment(authorization, paymentOrderId, request);
    }

    @PostMapping("/webhooks/cashfree")
    public ResponseEntity<Void> cashfreeWebhook(
        @RequestHeader(name = "x-webhook-timestamp", required = false) String timestamp,
        @RequestHeader(name = "x-webhook-signature", required = false) String signature,
        @RequestHeader(name = "x-webhook-version", required = false) String version,
        @RequestHeader(name = "x-idempotency-key", required = false) String idempotencyKey,
        @RequestBody String rawBody
    ) {
        apiProperties.requireCashfreeWebhookIngressEnabled();
        cashfreeWebhookInboxService.accept(timestamp, signature, version, idempotencyKey, rawBody);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/webhooks/razorpay")
    public ResponseEntity<Void> razorpayWebhook(
        @RequestHeader(name = "X-Razorpay-Signature", required = false) String signature,
        @RequestHeader(name = "X-Razorpay-Event-Id", required = false) String eventId,
        @RequestBody String rawBody
    ) {
        apiProperties.requireRazorpayWebhookIngressEnabled();
        paymentService.handleRazorpayWebhook(signature, eventId, rawBody);
        return ResponseEntity.ok().build();
    }
}
