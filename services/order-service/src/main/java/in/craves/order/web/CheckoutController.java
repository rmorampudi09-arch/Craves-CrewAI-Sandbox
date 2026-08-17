package in.craves.order.web;

import in.craves.order.security.CravesPrincipal;
import in.craves.order.service.OrderService;
import in.craves.order.web.ApiDtos.CheckoutRequest;
import in.craves.order.web.ApiDtos.CheckoutResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/checkout")
public class CheckoutController {
    private final OrderService orderService;

    public CheckoutController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public CheckoutResponse checkout(
        @AuthenticationPrincipal CravesPrincipal principal,
        @Valid @RequestBody(required = false) CheckoutRequest request
    ) {
        return orderService.checkout(principal, request == null ? new CheckoutRequest(null, null) : request);
    }

    @GetMapping("/{checkoutId}")
    public CheckoutResponse getCheckout(@AuthenticationPrincipal CravesPrincipal principal, @PathVariable UUID checkoutId) {
        return orderService.getCheckout(principal, checkoutId);
    }
}
