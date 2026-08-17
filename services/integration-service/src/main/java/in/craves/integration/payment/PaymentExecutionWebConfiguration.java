package in.craves.integration.payment;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class PaymentExecutionWebConfiguration implements WebMvcConfigurer {
    private final PaymentExecutionInterceptor interceptor;

    public PaymentExecutionWebConfiguration(PaymentExecutionInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor).addPathPatterns(
            "/api/v1/payments/orders/**",
            "/api/v1/subscription-payments/invoices/**"
        );
    }
}
