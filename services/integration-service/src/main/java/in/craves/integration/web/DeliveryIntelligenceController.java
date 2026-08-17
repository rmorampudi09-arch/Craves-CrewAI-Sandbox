package in.craves.integration.web;

import in.craves.integration.delivery.DeliveryIntelligenceModels.AssignmentRequest;
import in.craves.integration.delivery.DeliveryIntelligenceModels.AssignmentResponse;
import in.craves.integration.delivery.DeliveryIntelligenceModels.DeliveryOutcomeRequest;
import in.craves.integration.delivery.DeliveryIntelligenceModels.DeliveryScoreResponse;
import in.craves.integration.delivery.DeliveryIntelligenceModels.ProviderRegistrationRequest;
import in.craves.integration.delivery.DeliveryIntelligenceModels.ProviderResponse;
import in.craves.integration.delivery.DeliveryIntelligenceService;
import in.craves.integration.delivery.InternalRequestAuthorizer;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/delivery-intelligence")
public class DeliveryIntelligenceController {
    private static final String INTERNAL_HEADER = "X-Craves-Internal-Secret";
    private final DeliveryIntelligenceService service;
    private final InternalRequestAuthorizer authorizer;

    public DeliveryIntelligenceController(DeliveryIntelligenceService service,
                                          InternalRequestAuthorizer authorizer) {
        this.service = service;
        this.authorizer = authorizer;
    }

    @PostMapping("/providers")
    public ProviderResponse registerProvider(
        @RequestHeader(INTERNAL_HEADER) String internalKey,
        @Valid @RequestBody ProviderRegistrationRequest request
    ) {
        authorizer.requireValid(internalKey);
        return service.registerProvider(request);
    }

    @PostMapping("/assignments")
    public AssignmentResponse assign(
        @RequestHeader(INTERNAL_HEADER) String internalKey,
        @Valid @RequestBody AssignmentRequest request
    ) {
        authorizer.requireValid(internalKey);
        return service.assign(request);
    }

    @GetMapping("/assignments/{assignmentId}")
    public AssignmentResponse getAssignment(
        @RequestHeader(INTERNAL_HEADER) String internalKey,
        @PathVariable UUID assignmentId
    ) {
        authorizer.requireValid(internalKey);
        return service.getAssignment(assignmentId);
    }

    @PostMapping("/outcomes")
    public DeliveryScoreResponse recordOutcome(
        @RequestHeader(INTERNAL_HEADER) String internalKey,
        @Valid @RequestBody DeliveryOutcomeRequest request
    ) {
        authorizer.requireValid(internalKey);
        return service.recordOutcome(request);
    }
}
