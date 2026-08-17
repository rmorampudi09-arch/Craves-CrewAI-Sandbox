package in.craves.subscription.order;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnExpression(
    "${craves.subscription.order-dispatch.request-worker-enabled:false} || "
        + "${craves.subscription.order-dispatch.publisher-enabled:false}"
)
public class OccurrenceOrderSchedulingConfiguration {
}
