package in.craves.subscription.capacity;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
    prefix = "craves.subscription.capacity",
    name = "projection-scheduler-enabled",
    havingValue = "true"
)
public class CapacitySchedulingConfiguration {
}
