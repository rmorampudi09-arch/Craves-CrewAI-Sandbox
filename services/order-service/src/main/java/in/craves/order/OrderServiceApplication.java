package in.craves.order;

import in.craves.order.config.CatalogClientProperties;
import in.craves.order.config.ChefAcceptanceWindowProperties;
import in.craves.order.config.CravesJwtProperties;
import in.craves.order.config.DeliveryStatusConsumerProperties;
import in.craves.order.config.DomainEventOutboxProperties;
import in.craves.order.config.NotificationClientProperties;
import in.craves.order.config.NotificationOutboxDispatcherProperties;
import in.craves.order.config.RefundStatusConsumerProperties;
import in.craves.order.config.ServiceBusDomainEventProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({
    CravesJwtProperties.class,
    CatalogClientProperties.class,
    NotificationClientProperties.class,
    NotificationOutboxDispatcherProperties.class,
    DomainEventOutboxProperties.class,
    ServiceBusDomainEventProperties.class,
    ChefAcceptanceWindowProperties.class,
    RefundStatusConsumerProperties.class,
    DeliveryStatusConsumerProperties.class
})
public class OrderServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
