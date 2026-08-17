package in.craves.integration.web;

import in.craves.integration.delivery.InternalRequestAuthorizer;
import in.craves.integration.delivery.command.DeliveryCommandModels.ChefAcceptedOrderData;
import in.craves.integration.delivery.command.DeliveryCommandModels.EventEnvelope;
import in.craves.integration.delivery.command.DeliveryCommandScheduler;
import in.craves.integration.delivery.command.DeliveryCommandScheduler.ScheduleReceipt;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/delivery-orchestration")
@ConditionalOnProperty(prefix = "craves.delivery-command", name = "enabled", havingValue = "true")
public class DeliveryOrchestrationInternalController {
    private static final String INTERNAL_HEADER = "X-Craves-Internal-Secret";

    private final DeliveryCommandScheduler scheduler;
    private final InternalRequestAuthorizer authorizer;

    public DeliveryOrchestrationInternalController(DeliveryCommandScheduler scheduler,
                                                   InternalRequestAuthorizer authorizer) {
        this.scheduler = scheduler;
        this.authorizer = authorizer;
    }

    @PostMapping("/chef-accepted")
    public ScheduleReceipt chefAccepted(
        @RequestHeader(INTERNAL_HEADER) String internalKey,
        @RequestBody EventEnvelope<ChefAcceptedOrderData> event
    ) {
        authorizer.requireValid(internalKey);
        return scheduler.schedule(event);
    }
}
