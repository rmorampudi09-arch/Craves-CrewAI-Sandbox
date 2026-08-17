package in.craves.order.web;

import in.craves.order.delivery.DeliveryStatusQueryService;
import in.craves.order.security.CravesPrincipal;
import in.craves.order.web.DeliveryStatusDtos.DeliveryStatusResponse;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders")
public class DeliveryStatusController {
    private final DeliveryStatusQueryService queryService;

    public DeliveryStatusController(DeliveryStatusQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{orderId}/delivery-status")
    public DeliveryStatusResponse getDeliveryStatus(
        @AuthenticationPrincipal CravesPrincipal principal,
        @PathVariable UUID orderId
    ) {
        return queryService.getForCustomer(principal, orderId);
    }
}
