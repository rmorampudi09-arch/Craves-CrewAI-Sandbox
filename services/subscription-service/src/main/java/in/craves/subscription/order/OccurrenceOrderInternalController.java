package in.craves.subscription.order;

import in.craves.subscription.order.OccurrenceOrderModels.OrderCreatedRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/v1/subscription-occurrences")
public class OccurrenceOrderInternalController {
    private static final String INTERNAL_HEADER = "X-Craves-Internal-Secret";

    private final OccurrenceOrderProperties properties;
    private final OccurrenceOrderRepository repository;

    public OccurrenceOrderInternalController(
        OccurrenceOrderProperties properties,
        OccurrenceOrderRepository repository
    ) {
        this.properties = properties;
        this.repository = repository;
    }

    @PostMapping("/{occurrenceId}/order-created")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void orderCreated(
        @RequestHeader(INTERNAL_HEADER) String internalAccess,
        @PathVariable UUID occurrenceId,
        @Valid @RequestBody OrderCreatedRequest request
    ) {
        if (!properties.validInternalAccess(internalAccess)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal service credential");
        }
        try {
            repository.markOrderCreated(occurrenceId, request.orderId());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
    }
}
