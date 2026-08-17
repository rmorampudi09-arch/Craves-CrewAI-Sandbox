package in.craves.auth.admin;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(
    prefix = "craves.admin-account-intervention",
    name = "firebase-worker-enabled",
    havingValue = "true"
)
public class AdminAccountInterventionSchedulingConfiguration {
}
