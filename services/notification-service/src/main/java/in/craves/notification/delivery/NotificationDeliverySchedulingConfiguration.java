package in.craves.notification.delivery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(prefix = "craves.notification.delivery", name = "worker-enabled", havingValue = "true")
public class NotificationDeliverySchedulingConfiguration {
}
