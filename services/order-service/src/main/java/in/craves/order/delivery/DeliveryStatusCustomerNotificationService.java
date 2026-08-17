package in.craves.order.delivery;

import in.craves.order.delivery.DeliveryStatusModels.DeliveryStatusChangedData;
import in.craves.order.service.NotificationOutboxEvent;
import in.craves.order.service.NotificationOutboxRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class DeliveryStatusCustomerNotificationService {
    private final NotificationOutboxRepository notificationOutboxRepository;

    public DeliveryStatusCustomerNotificationService(
        NotificationOutboxRepository notificationOutboxRepository
    ) {
        this.notificationOutboxRepository = notificationOutboxRepository;
    }

    public boolean record(
        UUID eventId,
        UUID checkoutId,
        UUID customerIdentityId,
        DeliveryStatusChangedData data
    ) {
        NotificationCopy copy = copyFor(data.status());
        if (copy == null) {
            return false;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId.toString());
        payload.put("checkoutId", checkoutId.toString());
        payload.put("orderId", data.chefSubOrderId().toString());
        payload.put("deliveryJobId", data.deliveryJobId().toString());
        payload.put("providerId", data.providerId());
        payload.put("status", data.status());
        payload.put("observedAt", data.observedAt().toString());
        if (data.trackingUrl() != null) {
            payload.put("trackingUrl", data.trackingUrl());
        }

        return notificationOutboxRepository.savePendingIfAbsent(new NotificationOutboxEvent(
            "delivery-status-" + eventId,
            "DELIVERY_" + data.status(),
            "ORDER",
            data.chefSubOrderId(),
            customerIdentityId,
            "CUSTOMER",
            "IN_APP",
            copy.templateCode(),
            copy.title(),
            copy.body(),
            "ORDER",
            data.chefSubOrderId(),
            payload
        ));
    }

    private static NotificationCopy copyFor(String status) {
        return switch (status) {
            case "COURIER_ASSIGNED" -> new NotificationCopy(
                "DELIVERY_COURIER_ASSIGNED_IN_APP",
                "Delivery partner assigned",
                "A delivery partner has been assigned to your kitchen order."
            );
            case "AT_PICKUP" -> new NotificationCopy(
                "DELIVERY_AT_PICKUP_IN_APP",
                "Delivery partner at pickup",
                "The delivery partner has reached the chef's pickup location."
            );
            case "PICKED_UP" -> new NotificationCopy(
                "DELIVERY_PICKED_UP_IN_APP",
                "Order picked up",
                "Your food has been picked up and will be on the way shortly."
            );
            case "IN_TRANSIT" -> new NotificationCopy(
                "DELIVERY_IN_TRANSIT_IN_APP",
                "Order on the way",
                "Your kitchen order is on the way."
            );
            case "AT_DROPOFF" -> new NotificationCopy(
                "DELIVERY_AT_DROPOFF_IN_APP",
                "Delivery partner has arrived",
                "The delivery partner has reached your delivery location."
            );
            case "DELIVERED" -> new NotificationCopy(
                "DELIVERY_COMPLETED_IN_APP",
                "Order delivered",
                "Your kitchen order has been delivered."
            );
            case "DELAYED" -> new NotificationCopy(
                "DELIVERY_DELAYED_IN_APP",
                "Delivery delayed",
                "Your delivery is taking longer than expected. We are tracking it."
            );
            case "CANCELLED" -> new NotificationCopy(
                "DELIVERY_CANCELLED_IN_APP",
                "Delivery cancelled",
                "The delivery was cancelled. Craves support will update you about the next action."
            );
            case "FAILED" -> new NotificationCopy(
                "DELIVERY_FAILED_IN_APP",
                "Delivery needs attention",
                "The delivery could not be completed. Craves support will review it."
            );
            case "RETURNING" -> new NotificationCopy(
                "DELIVERY_RETURNING_IN_APP",
                "Order returning to chef",
                "The delivery is being returned to the chef. Craves support will update you."
            );
            case "RETURNED" -> new NotificationCopy(
                "DELIVERY_RETURNED_IN_APP",
                "Order returned",
                "The delivery was returned to the chef. Craves support will update you."
            );
            default -> null;
        };
    }

    private record NotificationCopy(String templateCode, String title, String body) {
    }
}
