package in.craves.integration.refund;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
    prefix = "craves.refund",
    name = "status-publisher-enabled",
    havingValue = "true"
)
public class RefundStatusPublisherSchedulingConfiguration {
}
