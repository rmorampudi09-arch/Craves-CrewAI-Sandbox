package in.craves.integration.web;

import in.craves.integration.delivery.InternalRequestAuthorizer;
import in.craves.integration.delivery.production.DeliveryProviderReadinessService;
import in.craves.integration.delivery.production.DeliveryProviderReadinessService.ReadinessResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/delivery-provider-readiness")
public class DeliveryProviderReadinessController {
    private static final String INTERNAL_HEADER = "X-Craves-Internal-Secret";

    private final DeliveryProviderReadinessService service;
    private final InternalRequestAuthorizer authorizer;

    public DeliveryProviderReadinessController(
        DeliveryProviderReadinessService service,
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
