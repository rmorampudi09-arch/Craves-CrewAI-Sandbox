package in.craves.integration.config;

import jakarta.annotation.PostConstruct;
import java.util.Locale;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PaymentRoutingProperties {
    private static final Set<String> SUPPORTED_PROVIDERS = Set.of("RAZORPAY");

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
        if (!SUPPORTED_PROVIDERS.contains(provider())) {
            throw new IllegalStateException("PAYMENT_PROVIDER_NAME must be RAZORPAY for the active production runtime");
        }
        if (cashfreeEnabled) {
            throw new IllegalStateException("CASHFREE_API_ENABLED must remain false because Cashfree is dormant for launch");
        }
        if (!razorpayEnabled) {
            throw new IllegalStateException("RAZORPAY_API_ENABLED must be true when Razorpay is the active provider");
        }
    }

    public String provider() {
        return activeProvider == null ? "" : activeProvider.trim().toUpperCase(Locale.ROOT);
    }

    public boolean razorpay() { return "RAZORPAY".equals(provider()); }
    public boolean cashfree() { return false; }
    public boolean cashfreeEnabled() { return cashfreeEnabled; }
    public boolean razorpayEnabled() { return razorpayEnabled; }
}
