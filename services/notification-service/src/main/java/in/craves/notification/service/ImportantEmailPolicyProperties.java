package in.craves.notification.service;

import in.craves.notification.api.CreateNotificationRequest;
import in.craves.notification.domain.NotificationChannel;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "craves.notification.important-email")
public class ImportantEmailPolicyProperties {
    private boolean enabled = false;
    private Set<String> eventTypes = new LinkedHashSet<>(List.of(
        "ORDER_CREATED",
        "PAYMENT_SUCCEEDED",
        "CHEF_ACCEPTED_ORDER",
        "CHEF_REJECTED",
        "CHEF_ACCEPTANCE_TIMEOUT",
        "REFUND_PENDING",
        "REFUNDED",
        "REFUND_FAILED",
        "DELIVERY_COURIER_ASSIGNED",
        "DELIVERY_PICKED_UP",
        "DELIVERY_IN_TRANSIT",
        "DELIVERY_DELIVERED",
        "DELIVERY_DELAYED",
        "DELIVERY_CANCELLED",
        "DELIVERY_FAILED",
        "DELIVERY_RETURNING",
        "DELIVERY_RETURNED",
        "CHEF_APPLICATION_SUBMITTED",
        "CHEF_APPLICATION_APPROVED",
        "CHEF_APPLICATION_REJECTED"
    ));

    public boolean shouldFanOut(CreateNotificationRequest request, NotificationChannel channel) {
        if (!enabled || channel != NotificationChannel.IN_APP || request.eventType() == null) {
            return false;
        }
        String eventType = request.eventType().trim().toUpperCase(Locale.ROOT);
        return eventTypes.stream()
            .map(value -> value == null ? "" : value.trim().toUpperCase(Locale.ROOT))
            .anyMatch(eventType::equals);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Set<String> getEventTypes() {
        return eventTypes;
    }

    public void setEventTypes(Set<String> eventTypes) {
        this.eventTypes = eventTypes == null ? Set.of() : new LinkedHashSet<>(eventTypes);
    }
}
