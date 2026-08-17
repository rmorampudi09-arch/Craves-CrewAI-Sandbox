package in.craves.order.refund;

import in.craves.order.refund.RefundStatusModels.RefundStatusChangedData;
import in.craves.order.service.NotificationOutboxEvent;
import in.craves.order.service.NotificationOutboxRepository;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class RefundStatusCustomerNotificationService {
    private final NotificationOutboxRepository notificationOutboxRepository;

    public RefundStatusCustomerNotificationService(
        NotificationOutboxRepository notificationOutboxRepository
    ) {
        this.notificationOutboxRepository = notificationOutboxRepository;
    }

    public boolean record(
        UUID eventId,
        UUID checkoutId,
        UUID customerIdentityId,
        RefundStatusChangedData data
    ) {
        NotificationCopy copy = copyFor(data.status());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", eventId.toString());
        payload.put("orderId", data.chefSubOrderId().toString());
        payload.put("checkoutId", checkoutId.toString());
        payload.put("refundId", data.refundId().toString());
        payload.put("refundReference", data.refundReference());
        payload.put("refundAmount", data.refundAmount().toPlainString());
        payload.put("currency", data.currency());
        payload.put("status", data.status());
        payload.put("providerStatus", data.providerStatus());
        payload.put("updatedAt", data.updatedAt().toString());
        if (data.cfRefundId() != null) {
            payload.put("cfRefundId", data.cfRefundId());
        }

        return notificationOutboxRepository.savePendingIfAbsent(new NotificationOutboxEvent(
            "refund-status-" + eventId,
            copy.eventType(),
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
            case "REFUND_PENDING" -> new NotificationCopy(
                "REFUND_PENDING",
                "REFUND_PENDING_IN_APP",
                "Refund processing",
                "Your refund for this kitchen order is being processed."
            );
            case "REFUNDED" -> new NotificationCopy(
                "REFUNDED",
                "REFUND_COMPLETED_IN_APP",
                "Refund completed",
                "Your refund for this kitchen order has been completed."
            );
            case "REFUND_FAILED" -> new NotificationCopy(
                "REFUND_FAILED",
                "REFUND_FAILED_IN_APP",
                "Refund update required",
                "We could not confirm completion of this refund. Please contact Craves support if the status does not change."
            );
            default -> throw new IllegalArgumentException(
                "Unsupported customer refund notification status " + status
            );
        };
    }

    private record NotificationCopy(
        String eventType,
        String templateCode,
        String title,
        String body
    ) {
    }
}
