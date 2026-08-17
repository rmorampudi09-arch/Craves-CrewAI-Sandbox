package in.craves.subscription.billing;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnExpression(
    "${craves.subscription.billing.generator-enabled:false} || "
        + "${craves.subscription.billing.publisher-enabled:false}"
)
public class SubscriptionBillingSchedulingConfiguration {
}
