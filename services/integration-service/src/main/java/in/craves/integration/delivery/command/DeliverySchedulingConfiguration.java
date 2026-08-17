package in.craves.integration.delivery.command;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnExpression(
    "${craves.delivery-command.enabled:false} || "
        + "${craves.delivery-command.reconciliation-enabled:false} || "
        + "${craves.delivery-command.webhook-processing-enabled:false} || "
        + "${craves.delivery-command.tracking-reconciliation-enabled:false} || "
        + "${craves.delivery-command.status-publisher-enabled:false}"
)
public class DeliverySchedulingConfiguration {
}
