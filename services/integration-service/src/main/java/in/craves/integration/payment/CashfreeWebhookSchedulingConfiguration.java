package in.craves.integration.payment;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "craves.cashfree.webhook", name = "worker-enabled", havingValue = "true")
public class CashfreeWebhookSchedulingConfiguration {
}
