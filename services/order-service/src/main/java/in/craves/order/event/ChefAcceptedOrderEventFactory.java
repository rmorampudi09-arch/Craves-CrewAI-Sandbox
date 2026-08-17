package in.craves.order.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import in.craves.order.event.ChefAcceptedOrderEventData.DeliveryItemData;
import in.craves.order.event.ChefAcceptedOrderEventData.DeliveryRequestData;
import in.craves.order.event.ChefAcceptedOrderEventData.DeliveryStopData;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ChefAcceptedOrderEventFactory {
    public static final String EVENT_TYPE = "CHEF_ACCEPTED_ORDER";
    public static final String EVENT_VERSION = "1.0";
    public static final String SOURCE = "order-service";
    private static final String DEFAULT_COUNTRY = "India";

    private final ObjectMapper objectMapper;

    public ChefAcceptedOrderEventFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public SerializedDomainEvent create(
        ChefAcceptedOrderEventSource source,
        UUID requestedCorrelationId,
        String idempotencyKey
    ) {
        validate(source);

        UUID eventId = UUID.randomUUID();
        UUID correlationId = requestedCorrelationId == null ? eventId : requestedCorrelationId;
        UUID causationId = causationId(idempotencyKey);

        DeliveryStopData pickup = new DeliveryStopData(
            address(
                source.pickupAddressLine1(),
                source.pickupAddressLine2(),
                source.pickupLandmark(),
                source.pickupAreaName(),
                source.pickupCity(),
                source.pickupState(),
                source.pickupPostalCode()
            ),
            source.kitchenName(),
            source.pickupPhoneNumber(),
            source.pickupLatitude(),
            source.pickupLongitude(),
            null,
            null,
            null,
            source.pickupAddressLine1(),
            source.pickupAddressLine2(),
            source.pickupLandmark(),
            source.pickupAreaName(),
            source.pickupCity(),
            source.pickupState(),
            source.pickupPostalCode(),
            DEFAULT_COUNTRY
        );

        DeliveryStopData dropoff = new DeliveryStopData(
            address(
                source.dropoffAddressLine1(),
                source.dropoffAddressLine2(),
                source.dropoffLandmark(),
                source.dropoffAreaName(),
                source.dropoffCity(),
                source.dropoffState(),
                source.dropoffPostalCode()
            ),
            source.dropoffRecipientName(),
            source.dropoffPhoneNumber(),
            source.dropoffLatitude(),
            source.dropoffLongitude(),
            null,
            null,
            null,
            source.dropoffAddressLine1(),
            source.dropoffAddressLine2(),
            source.dropoffLandmark(),
            source.dropoffAreaName(),
            source.dropoffCity(),
            source.dropoffState(),
            source.dropoffPostalCode(),
            DEFAULT_COUNTRY
        );

        List<DeliveryItemData> items = source.deliveryItems() == null
            ? List.of()
            : source.deliveryItems().stream()
                .map(item -> new DeliveryItemData(
                    item.menuItemId(),
                    item.itemName(),
                    item.unitPrice(),
                    item.quantity(),
                    item.lineTotal()
                ))
                .toList();

        ChefAcceptedOrderEventData data = new ChefAcceptedOrderEventData(
            source.checkoutId(),
            source.orderId(),
            source.readyAt(),
            null,
            source.pickupAreaName(),
            new DeliveryRequestData(
                "Food order " + source.orderId(),
                source.totalPackageWeightGrams(),
                source.thermoboxRequired(),
                pickup,
                dropoff,
                items,
                source.declaredGoodsValue(),
                source.paymentCollectionMode(),
                source.pickupLocationReference()
            )
        );

        DomainEventEnvelope<ChefAcceptedOrderEventData> envelope = new DomainEventEnvelope<>(
            eventId,
            EVENT_TYPE,
            EVENT_VERSION,
            source.acceptedAt(),
            correlationId,
            causationId,
            SOURCE,
            source.orderId().toString(),
            data
        );

        try {
            return new SerializedDomainEvent(
                eventId,
                EVENT_TYPE,
                EVENT_VERSION,
                source.acceptedAt(),
                correlationId,
                causationId,
                SOURCE,
                source.orderId().toString(),
                objectMapper.writeValueAsString(envelope)
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("CHEF_ACCEPTED_ORDER serialization failed", exception);
        }
    }

    private static UUID causationId(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return null;
        }
        return UUID.nameUUIDFromBytes(
            ("chef-order-accept:" + idempotencyKey.trim()).getBytes(StandardCharsets.UTF_8)
        );
    }

    private static String address(String... parts) {
        return Stream.of(parts)
            .filter(StringUtils::hasText)
            .map(String::trim)
            .collect(Collectors.joining(", "));
    }

    private static void validate(ChefAcceptedOrderEventSource source) {
        Objects.requireNonNull(source, "event source is required");
        Objects.requireNonNull(source.orderId(), "orderId is required");
        Objects.requireNonNull(source.checkoutId(), "checkoutId is required");
        Objects.requireNonNull(source.acceptedAt(), "acceptedAt is required");
        Objects.requireNonNull(source.readyAt(), "readyAt is required");
        Objects.requireNonNull(source.pickupLatitude(), "pickup latitude is required");
        Objects.requireNonNull(source.pickupLongitude(), "pickup longitude is required");
        Objects.requireNonNull(source.dropoffLatitude(), "dropoff latitude is required");
        Objects.requireNonNull(source.dropoffLongitude(), "dropoff longitude is required");
        if (source.totalPackageWeightGrams() <= 0) {
            throw new IllegalArgumentException("totalPackageWeightGrams must be positive");
        }
        requireText(source.kitchenName(), "kitchenName");
        requireText(source.pickupPhoneNumber(), "pickupPhoneNumber");
        requireText(source.pickupAddressLine1(), "pickupAddressLine1");
        requireText(source.pickupAreaName(), "pickupAreaName");
        requireText(source.dropoffRecipientName(), "dropoffRecipientName");
        requireText(source.dropoffPhoneNumber(), "dropoffPhoneNumber");
        requireText(source.dropoffAddressLine1(), "dropoffAddressLine1");
    }

    private static void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
