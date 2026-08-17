package in.craves.order.web;

import in.craves.order.security.CravesPrincipal;
import in.craves.order.service.OrderService;
import in.craves.order.web.ApiDtos.ChargePolicyRequest;
import in.craves.order.web.ApiDtos.ChargePolicyResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/charge-policy")
public class AdminChargePolicyController {
    private final OrderService orderService;

    public AdminChargePolicyController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping("/current")
    public ChargePolicyResponse current() {
        return orderService.currentChargePolicy();
    }

    @PostMapping
    public ChargePolicyResponse create(@AuthenticationPrincipal CravesPrincipal principal, @Valid @RequestBody ChargePolicyRequest request) {
        return orderService.createChargePolicy(principal, request);
    }
}
