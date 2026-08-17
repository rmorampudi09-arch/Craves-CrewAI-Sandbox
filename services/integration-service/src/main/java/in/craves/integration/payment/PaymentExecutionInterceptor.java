package in.craves.integration.payment;

import in.craves.integration.config.PaymentProviderProperties;
import in.craves.integration.config.PaymentRoutingProperties;
import in.craves.integration.config.RazorpayProviderProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class PaymentExecutionInterceptor implements HandlerInterceptor {
    private final PaymentProviderProperties cashfree;
    private final PaymentRoutingProperties routing;
    private final RazorpayProviderProperties razorpay;

    public PaymentExecutionInterceptor(
        PaymentProviderProperties cashfree,
        PaymentRoutingProperties routing,
        RazorpayProviderProperties razorpay
    ) {
        this.cashfree = cashfree;
        this.routing = routing;
        this.razorpay = razorpay;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        boolean executionAllowed = routing.razorpay()
            ? razorpay.paymentExecutionAllowed()
            : cashfree.paymentExecutionAllowed();
        if (!(handler instanceof HandlerMethod) || executionAllowed) {
            return true;
        }
        String method = request.getMethod();
        String uri = request.getRequestURI();
        boolean createsCheckoutOrder = "POST".equalsIgnoreCase(method)
            && "/api/v1/payments/orders".equals(uri);
        boolean verifiesCheckoutOrder = "POST".equalsIgnoreCase(method)
            && uri.matches("/api/v1/payments/orders/[0-9a-fA-F-]{36}/verify");
        boolean createsSubscriptionOrder = "POST".equalsIgnoreCase(method)
            && uri.matches("/api/v1/subscription-payments/invoices/[0-9a-fA-F-]{36}/orders");
        boolean verifiesSubscriptionOrder = "POST".equalsIgnoreCase(method)
            && uri.matches("/api/v1/subscription-payments/invoices/[0-9a-fA-F-]{36}/verify");
        if (createsCheckoutOrder || verifiesCheckoutOrder || createsSubscriptionOrder || verifiesSubscriptionOrder) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Production payment execution is not enabled"
            );
        }
        return true;
    }
}
