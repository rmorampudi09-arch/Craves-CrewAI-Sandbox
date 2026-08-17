package in.craves.subscription.occurrence;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
    prefix = "craves.subscription.occurrence-generator",
    name = "enabled",
    havingValue = "true"
)
public class OccurrenceSchedulingConfiguration {
}
