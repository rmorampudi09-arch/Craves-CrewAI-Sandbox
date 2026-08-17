package in.craves.integration.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class PaymentApiProperties {
    private final boolean orderExecutionEnabled;
    private final boolean cashfreeWebhookIngressEnabled;
    private final boolean razorpayWebhookIngressEnabled;

    public PaymentApiProperties(
        @Value("${CRAVES_PAYMENT_ORDER_API_ENABLED:false}") boolean orderExecutionEnabled,
        @Value("${CRAVES_CASHFREE_WEBHOOK_INGRESS_ENABLED:false}") boolean cashfreeWebhookIngressEnabled,
        @Value("${CRAVES_RAZORPAY_WEBHOOK_INGRESS_ENABLED:false}") boolean razorpayWebhookIngressEnabled
    ) {
        this.orderExecutionEnabled = orderExecutionEnabled;
        this.cashfreeWebhookIngressEnabled = cashfreeWebhookIngressEnabled;
        this.razorpayWebhookIngressEnabled = razorpayWebhookIngressEnabled;
    }

    public boolean orderExecutionEnabled() {
        return orderExecutionEnabled;
    }

    public boolean webhookIngressEnabled() {
        return cashfreeWebhookIngressEnabled || razorpayWebhookIngressEnabled;
    }

    public void requireOrderExecutionEnabled() {
        if (!orderExecutionEnabled) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Payment order execution is not enabled"
            );
        }
    }

    public void requireCashfreeWebhookIngressEnabled() {
        if (!cashfreeWebhookIngressEnabled) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Cashfree webhook ingress is not enabled"
            );
        }
    }

    public void requireRazorpayWebhookIngressEnabled() {
        if (!razorpayWebhookIngressEnabled) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Razorpay webhook ingress is not enabled"
            );
        }
    }
}
