package in.craves.integration.delivery.borzo;

import com.fasterxml.jackson.databind.JsonNode;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.DeliveryStatus;
import java.util.Locale;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class BorzoStatusMapper {

    public DeliveryStatus fromOrder(JsonNode order) {
        if (order == null || order.isMissingNode() || order.isNull()) {
            return DeliveryStatus.UNKNOWN;
        }
        JsonNode points = order.path("points");
        if (points.isArray()) {
            for (int index = points.size() - 1; index >= 0; index--) {
                String deliveryStatus = points.path(index).path("delivery").path("status").asText(null);
                if (StringUtils.hasText(deliveryStatus)) {
                    return fromDeliveryStatus(deliveryStatus);
                }
            }
        }
        return fromOrderStatus(order.path("status").asText(null));
    }

    public DeliveryStatus fromOrderStatus(String status) {
        return switch (normalize(status)) {
            case "new", "draft" -> DeliveryStatus.PENDING;
            case "available", "reactivated" -> DeliveryStatus.SEARCHING;
            case "active" -> DeliveryStatus.IN_TRANSIT;
            case "completed" -> DeliveryStatus.DELIVERED;
            case "canceled" -> DeliveryStatus.CANCELLED;
            case "delayed" -> DeliveryStatus.DELAYED;
            default -> DeliveryStatus.UNKNOWN;
        };
    }

    public DeliveryStatus fromDeliveryStatus(String status) {
        return switch (normalize(status)) {
            case "draft" -> DeliveryStatus.PENDING;
            case "planned", "reattempt_planned" -> DeliveryStatus.SEARCHING;
            case "courier_assigned", "reattempt_courier_assigned" -> DeliveryStatus.COURIER_ASSIGNED;
            case "courier_departed", "reattempt_courier_departed" -> DeliveryStatus.COURIER_TO_PICKUP;
            case "courier_at_pickup" -> DeliveryStatus.AT_PICKUP;
            case "parcel_picked_up", "reattempt_courier_picked_up" -> DeliveryStatus.PICKED_UP;
            case "active" -> DeliveryStatus.IN_TRANSIT;
            case "courier_arrived" -> DeliveryStatus.AT_DROPOFF;
            case "finished", "reattempt_finished" -> DeliveryStatus.DELIVERED;
            case "canceled" -> DeliveryStatus.CANCELLED;
            case "delayed" -> DeliveryStatus.DELAYED;
            case "return_planned", "return_courier_assigned", "return_courier_departed",
                 "return_courier_picked_up" -> DeliveryStatus.RETURNING;
            case "return_finished" -> DeliveryStatus.RETURNED;
            case "invalid", "deleted" -> DeliveryStatus.FAILED;
            default -> DeliveryStatus.UNKNOWN;
        };
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
