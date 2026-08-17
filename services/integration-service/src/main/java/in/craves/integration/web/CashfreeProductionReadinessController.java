package in.craves.integration.web;

import in.craves.integration.delivery.InternalRequestAuthorizer;
import in.craves.integration.payment.CashfreeProductionReadinessService;
import in.craves.integration.payment.CashfreeProductionReadinessService.ReadinessResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/payment-provider-readiness")
public class CashfreeProductionReadinessController {
    private static final String INTERNAL_HEADER = "X-Craves-Internal-Secret";

    private final CashfreeProductionReadinessService service;
    private final InternalRequestAuthorizer authorizer;

    public CashfreeProductionReadinessController(
        CashfreeProductionReadinessService service,
        InternalRequestAuthorizer authorizer
    ) {
        this.service = service;
        this.authorizer = authorizer;
    }

    @GetMapping
    public ReadinessResponse status(@RequestHeader(INTERNAL_HEADER) String internalKey) {
        authorizer.requireValid(internalKey);
        return service.status();
    }
}
