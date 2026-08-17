package in.craves.order.subscription;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "craves.subscription-orders", name = "callback-worker-enabled", havingValue = "true")
public class SubscriptionOrderCallbackSchedulingConfiguration {
}
