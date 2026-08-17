package in.craves.order.web;

import in.craves.order.security.CravesPrincipal;
import in.craves.order.service.OrderService;
import in.craves.order.web.ApiDtos.AddCartItemRequest;
import in.craves.order.web.ApiDtos.CartResponse;
import in.craves.order.web.ApiDtos.UpdateCartItemRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/cart")
public class CartController {
    private final OrderService orderService;

    public CartController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public CartResponse getCart(@AuthenticationPrincipal CravesPrincipal principal) {
        return orderService.getCart(principal);
    }

    @PostMapping("/items")
    public CartResponse addCartItem(@AuthenticationPrincipal CravesPrincipal principal, @Valid @RequestBody AddCartItemRequest request) {
        return orderService.addCartItem(principal, request);
    }

    @PutMapping("/items/{cartItemId}")
    public CartResponse updateCartItem(@AuthenticationPrincipal CravesPrincipal principal, @PathVariable UUID cartItemId, @Valid @RequestBody UpdateCartItemRequest request) {
        return orderService.updateCartItem(principal, cartItemId, request);
    }

    @DeleteMapping("/items/{cartItemId}")
    public CartResponse removeCartItem(@AuthenticationPrincipal CravesPrincipal principal, @PathVariable UUID cartItemId) {
        return orderService.removeCartItem(principal, cartItemId);
    }

    @DeleteMapping
    public CartResponse clearCart(@AuthenticationPrincipal CravesPrincipal principal) {
        return orderService.clearCart(principal);
    }

    @PostMapping("/validate")
    public CartResponse validateCart(@AuthenticationPrincipal CravesPrincipal principal) {
        return orderService.validateCart(principal);
    }

    @PostMapping("/reorder/{orderId}")
    public CartResponse reorder(
        @AuthenticationPrincipal CravesPrincipal principal,
        @PathVariable UUID orderId
    ) {
        return orderService.replaceCartFromOrder(principal, orderId);
    }
}
