package in.craves.subscription.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.craves.subscription.order.OccurrenceOrderModels.OrderItem;
import in.craves.subscription.order.OccurrenceOrderModels.OrderRequestedData;
import in.craves.subscription.order.OccurrenceOrderRepository.OccurrenceClaim;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OccurrenceOrderService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OccurrenceOrderService.class);

    private final OccurrenceOrderProperties properties;
    private final OccurrenceOrderRepository repository;
    private final ObjectMapper objectMapper;

    public OccurrenceOrderService(
        OccurrenceOrderProperties properties,
        OccurrenceOrderRepository repository,
        ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    public DispatchSummary queueDueOccurrences() {
        List<OccurrenceClaim> claims = repository.claimReady(
            properties.getLeadHours(),
            properties.getStaleLockMinutes(),
            properties.getBatchSize()
        );
        int queued = 0;
        int skipped = 0;
        int failed = 0;
        for (OccurrenceClaim claim : claims) {
            try {
                validate(claim);
                UUID outboxId = UUID.randomUUID();
                boolean created = repository.createRequest(claim, outboxId, event(outboxId, claim));
                if (created) {
                    queued++;
                } else {
                    skipped++;
                }
            } catch (RuntimeException exception) {
                failed++;
                repository.releaseClaim(claim);
                LOGGER.error(
                    "Subscription order request failed occurrenceId={} subscriptionId={}",
                    claim.occurrenceId(), claim.subscriptionId(), exception
                );
            }
        }
        return new DispatchSummary(claims.size(), queued, skipped, failed);
    }

    private ObjectNode event(UUID eventId, OccurrenceClaim claim) {
        List<OrderItem> items = claim.items().stream()
            .map(item -> new OrderItem(item.menuItemId(), item.quantity(), item.sequenceNumber()))
            .toList();
        OrderRequestedData data = new OrderRequestedData(
            claim.occurrenceId(), claim.subscriptionId(), claim.planId(),
            claim.customerIdentityId(), claim.chefIdentityId(), claim.deliveryAddressId(),
            claim.scheduledServiceAt(), items
        );
        ObjectNode event = objectMapper.createObjectNode();
        event.put("eventId", eventId.toString());
        event.put("eventType", OccurrenceOrderModels.EVENT_TYPE);
        event.put("eventVersion", "v1");
        event.put("occurredAt", Instant.now().toString());
        event.put("correlationId", claim.occurrenceId().toString());
        event.put("causationId", claim.subscriptionId().toString());
        event.put("subject", claim.occurrenceId().toString());
        event.set("data", objectMapper.valueToTree(data));
        return event;
    }

    private static void validate(OccurrenceClaim claim) {
        if (claim.occurrenceId() == null || claim.subscriptionId() == null || claim.planId() == null
            || claim.customerIdentityId() == null || claim.chefIdentityId() == null
            || claim.deliveryAddressId() == null || claim.scheduledServiceAt() == null
            || claim.items() == null || claim.items().isEmpty()) {
            throw new IllegalStateException("Subscription occurrence is incomplete for order creation");
        }
        for (var item : claim.items()) {
            if (item.menuItemId() == null || item.quantity() < 1 || item.quantity() > 100
                || item.sequenceNumber() < 1 || item.sequenceNumber() > 100) {
                throw new IllegalStateException("Subscription occurrence contains invalid items");
            }
        }
    }

    public record DispatchSummary(int claimed, int queued, int skipped, int failed) {
    }
}
