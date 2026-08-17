package in.craves.order.subscription;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.order.service.CatalogClient;
import in.craves.order.service.CatalogClient.CatalogKitchen;
import in.craves.order.service.CatalogClient.CatalogMenuItem;
import in.craves.order.service.CheckoutSnapshotFactory;
import in.craves.order.service.CustomerAddressClient;
import in.craves.order.subscription.SubscriptionOrderModels.CreatedResult;
import in.craves.order.subscription.SubscriptionOrderModels.EventEnvelope;
import in.craves.order.subscription.SubscriptionOrderModels.RequestedData;
import in.craves.order.subscription.SubscriptionOrderModels.RequestedItem;
import in.craves.order.subscription.SubscriptionOrderRepository.CreatedOrder;
import in.craves.order.subscription.SubscriptionOrderRepository.ValidatedItem;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SubscriptionOrderService {
    private final ObjectMapper objectMapper;
    private final CatalogClient catalogClient;
    private final CustomerAddressClient addressClient;
    private final CheckoutSnapshotFactory snapshotFactory;
    private final SubscriptionOrderRepository repository;

    public SubscriptionOrderService(
        ObjectMapper objectMapper,
        CatalogClient catalogClient,
        CustomerAddressClient addressClient,
        CheckoutSnapshotFactory snapshotFactory,
        SubscriptionOrderRepository repository
    ) {
        this.objectMapper = objectMapper;
        this.catalogClient = catalogClient;
        this.addressClient = addressClient;
        this.snapshotFactory = snapshotFactory;
        this.repository = repository;
    }

    public CreatedResult accept(String rawPayload) {
        ParsedEvent parsed = parse(rawPayload);
        EventEnvelope<RequestedData> event = parsed.event();
        RequestedData data = event.data();

        var existing = repository.findOrderByOccurrence(data.occurrenceId());
        if (existing.isPresent()) {
            return new CreatedResult(data.occurrenceId(), existing.get(), false);
        }

        CatalogKitchen kitchen = catalogClient.getKitchen(resolveKitchen(data));
        if (kitchen.identityId() == null || !kitchen.identityId().equals(data.chefIdentityId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Subscription chef does not own the selected kitchen");
        }
        var pickup = snapshotFactory.kitchenPickup(kitchen);
        var address = addressClient.getActiveOwnedAddress(data.customerIdentityId(), data.deliveryAddressId());
        var dropoff = snapshotFactory.customerDropoff(address);

        List<ValidatedItem> items = new ArrayList<>();
        int packageWeight = 0;
        boolean thermobox = false;
        Set<UUID> menuIds = new HashSet<>();
        List<RequestedItem> requestedItems = data.items().stream()
            .sorted(Comparator.comparingInt(RequestedItem::sequenceNumber))
            .toList();
        for (RequestedItem requested : requestedItems) {
            if (!menuIds.add(requested.menuItemId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Subscription occurrence contains duplicate menu items");
            }
            CatalogMenuItem item = catalogClient.getActiveMenuItem(requested.menuItemId());
            if (!kitchen.id().equals(item.kitchenId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Subscription menu item belongs to a different kitchen");
            }
            int weight = Math.multiplyExact(item.unitPackageWeightGrams(), requested.quantity());
            packageWeight = Math.addExact(packageWeight, weight);
            thermobox = thermobox || Boolean.TRUE.equals(item.thermoboxRequired());
            items.add(new ValidatedItem(
                item.id(),
                required(item.itemName(), "Menu item name is missing"),
                item.category(),
                item.foodType(),
                item.unitPackageWeightGrams(),
                Boolean.TRUE.equals(item.thermoboxRequired()),
                requested.quantity()
            ));
        }
        if (packageWeight <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Subscription order package weight is invalid");
        }

        CreatedOrder result = repository.create(
            event,
            parsed.raw(),
            kitchen.id(),
            pickup.kitchenName(),
            dropoff,
            pickup,
            List.copyOf(items),
            packageWeight,
            thermobox
        );
        return new CreatedResult(data.occurrenceId(), result.orderId(), result.created());
    }

    private UUID resolveKitchen(RequestedData data) {
        CatalogMenuItem first = catalogClient.getActiveMenuItem(data.items().getFirst().menuItemId());
        return first.kitchenId();
    }

    private ParsedEvent parse(String rawPayload) {
        try {
            JsonNode raw = objectMapper.readTree(rawPayload);
            JavaType type = objectMapper.getTypeFactory().constructParametricType(
                EventEnvelope.class,
                RequestedData.class
            );
            EventEnvelope<RequestedData> event = objectMapper.readerFor(type).readValue(raw);
            validate(event);
            return new ParsedEvent(event, raw);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Subscription order event is invalid", exception);
        }
    }

    static void validate(EventEnvelope<RequestedData> event) {
        if (event == null || event.eventId() == null || event.data() == null
            || !SubscriptionOrderModels.EVENT_TYPE.equals(event.eventType())
            || !"v1".equals(event.eventVersion())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported subscription order event");
        }
        RequestedData data = event.data();
        if (data.occurrenceId() == null || data.subscriptionId() == null || data.planId() == null
            || data.customerIdentityId() == null || data.chefIdentityId() == null
            || data.deliveryAddressId() == null || data.scheduledServiceAt() == null
            || data.items() == null || data.items().isEmpty() || data.items().size() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Subscription order event data is incomplete");
        }
        for (RequestedItem item : data.items()) {
            if (item == null || item.menuItemId() == null || item.quantity() < 1 || item.quantity() > 100
                || item.sequenceNumber() < 1 || item.sequenceNumber() > 100) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Subscription order item is invalid");
            }
        }
    }

    private static String required(String value, String message) {
        if (!StringUtils.hasText(value)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, message);
        }
        return value.trim();
    }

    private record ParsedEvent(EventEnvelope<RequestedData> event, JsonNode raw) {
    }
}
