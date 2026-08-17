package in.craves.integration.config;

import jakarta.annotation.PostConstruct;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PaymentRoutingProperties {
    private final String activeProvider;
    private final boolean cashfreeEnabled;
    private final boolean razorpayEnabled;

    public PaymentRoutingProperties(
        @Value("${PAYMENT_PROVIDER_NAME:RAZORPAY}") String activeProvider,
        @Value("${CASHFREE_API_ENABLED:false}") boolean cashfreeEnabled,
        @Value("${RAZORPAY_API_ENABLED:true}") boolean razorpayEnabled
    ) {
        this.activeProvider = activeProvider;
        this.cashfreeEnabled = cashfreeEnabled;
        this.razorpayEnabled = razorpayEnabled;
    }

    @PostConstruct
    void validate() {
        if (!Set.of("CASHFREE", "RAZORPAY").contains(provider())) {
            throw new IllegalStateException("PAYMENT_PROVIDER_NAME must be CASHFREE or RAZORPAY");
        }
        if ("CASHFREE".equals(provider()) && !cashfreeEnabled) {
            throw new IllegalStateException("CASHFREE_API_ENABLED must be true when Cashfree is active");
        }
        if ("RAZORPAY".equals(provider()) && !razorpayEnabled) {
            throw new IllegalStateException("RAZORPAY_API_ENABLED must be true when Razorpay is active");
        }
    }

    public String provider() {
        return activeProvider == null ? "" : activeProvider.trim().toUpperCase(Locale.ROOT);
    }

    public boolean razorpay() { return "RAZORPAY".equals(provider()); }
    public boolean cashfree() { return "CASHFREE".equals(provider()); }
    public boolean cashfreeEnabled() { return cashfreeEnabled; }
    public boolean razorpayEnabled() { return razorpayEnabled; }
}
