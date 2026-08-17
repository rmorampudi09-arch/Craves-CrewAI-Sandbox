package in.craves.integration.delivery.command;

import in.craves.integration.delivery.command.DeliveryCommandModels.ChefAcceptedOrderData;
import in.craves.integration.delivery.command.DeliveryCommandModels.DeliveryCommandMessage;
import in.craves.integration.delivery.command.DeliveryCommandModels.EventEnvelope;
import in.craves.integration.delivery.command.DeliveryCommandRepository.CommandRecord;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.QuoteRequest;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.Stop;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@ConditionalOnProperty(prefix = "craves.delivery-command", name = "enabled", havingValue = "true")
public class DeliveryCommandScheduler {
    private static final ZoneId DELIVERY_CONTEXT_ZONE = ZoneId.of("Asia/Kolkata");
    private static final double EARTH_RADIUS_KM = 6371.0088;

    private final DeliveryCommandRepository repository;
    private final DeliveryServiceBusPublisher publisher;
    private final DeliveryCommandProperties properties;
    private final Clock clock;

    @Autowired
    public DeliveryCommandScheduler(DeliveryCommandRepository repository,
                                    DeliveryServiceBusPublisher publisher,
                                    DeliveryCommandProperties properties) {
        this(repository, publisher, properties, Clock.systemUTC());
    }

    DeliveryCommandScheduler(DeliveryCommandRepository repository,
                             DeliveryServiceBusPublisher publisher,
                             DeliveryCommandProperties properties,
                             Clock clock) {
        this.repository = repository;
        this.publisher = publisher;
        this.properties = properties;
        this.clock = clock;
    }

    public ScheduleReceipt schedule(EventEnvelope<ChefAcceptedOrderData> event) {
        validate(event);
        ChefAcceptedOrderData data = event.data();
        RoutingContext routingContext = routingContext(event, data);
        Instant dispatchAt = data.readyAt().minus(properties.getLeadTimeMinutes(), ChronoUnit.MINUTES);
        Instant minimumDispatchAt = clock.instant().plusSeconds(5);
        if (dispatchAt.isBefore(minimumDispatchAt)) {
            dispatchAt = minimumDispatchAt;
        }

        UUID commandId = UUID.nameUUIDFromBytes(
            ("delivery-command:" + data.chefSubOrderId()).getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
        DeliveryCommandMessage candidate = new DeliveryCommandMessage(
            commandId,
            event.eventId(),
            event.correlationId(),
            data.orderId(),
            data.chefSubOrderId(),
            data.readyAt(),
            dispatchAt,
            data.chefSubOrderId().toString(),
            routingContext.distanceKm(),
            routingContext.area(),
            routingContext.orderHour(),
            routingContext.dayOfWeek(),
            data.deliveryRequest()
        );

        CommandRecord command = repository.createOrFind(candidate);
        if ("COMPLETED".equals(command.status()) || command.scheduledSequenceNumber() != null) {
            return new ScheduleReceipt(
                command.id(),
                command.message().dispatchAt(),
                command.scheduledSequenceNumber(),
                true
            );
        }

        DeliveryServiceBusPublisher.ScheduledMessage scheduled = publisher.schedule(command.message());
        boolean recorded = repository.recordScheduled(
            command.id(), scheduled.sequenceNumber(), scheduled.messageId()
        );
        if (!recorded) {
            publisher.cancelScheduled(scheduled.sequenceNumber());
            CommandRecord winner = repository.findById(command.id()).orElse(command);
            return new ScheduleReceipt(
                winner.id(),
                winner.message().dispatchAt(),
                winner.scheduledSequenceNumber(),
                true
            );
        }

        return new ScheduleReceipt(
            command.id(), command.message().dispatchAt(), scheduled.sequenceNumber(), false
        );
    }

    private static RoutingContext routingContext(EventEnvelope<ChefAcceptedOrderData> event,
                                                 ChefAcceptedOrderData data) {
        QuoteRequest request = data.deliveryRequest();
        double distanceKm = resolveDistanceKm(data.distanceKm(), request);
        String area = resolveArea(data.area(), request.pickup());
        ZonedDateTime localOccurredAt = event.occurredAt().atZone(DELIVERY_CONTEXT_ZONE);
        return new RoutingContext(
            distanceKm,
            area,
            localOccurredAt.getHour(),
            localOccurredAt.getDayOfWeek().getValue() - 1
        );
    }

    private static double resolveDistanceKm(Double suppliedDistanceKm, QuoteRequest request) {
        if (suppliedDistanceKm != null) {
            if (!Double.isFinite(suppliedDistanceKm) || suppliedDistanceKm < 0.0) {
                throw new DeliveryMessageValidationException("distanceKm must be a finite non-negative number");
            }
            return suppliedDistanceKm;
        }

        Stop pickup = request.pickup();
        Stop dropoff = request.dropoff();
        if (hasCoordinates(pickup) && hasCoordinates(dropoff)) {
            return haversineKm(
                pickup.latitude(), pickup.longitude(), dropoff.latitude(), dropoff.longitude()
            );
        }
        throw new DeliveryMessageValidationException(
            "distanceKm or pickup/dropoff coordinates are required for intelligent assignment"
        );
    }

    private static String resolveArea(String suppliedArea, Stop pickup) {
        if (StringUtils.hasText(suppliedArea)) {
            return suppliedArea.trim();
        }
        if (pickup != null && StringUtils.hasText(pickup.address())) {
            String firstAddressPart = pickup.address().split(",", 2)[0].trim();
            if (StringUtils.hasText(firstAddressPart)) {
                return firstAddressPart;
            }
        }
        throw new DeliveryMessageValidationException(
            "area or a pickup address with an area prefix is required for intelligent assignment"
        );
    }

    private static boolean hasCoordinates(Stop stop) {
        return stop != null && stop.latitude() != null && stop.longitude() != null;
    }

    private static double haversineKm(BigDecimal firstLatitude,
                                      BigDecimal firstLongitude,
                                      BigDecimal secondLatitude,
                                      BigDecimal secondLongitude) {
        double latitude1 = Math.toRadians(firstLatitude.doubleValue());
        double latitude2 = Math.toRadians(secondLatitude.doubleValue());
        double latitudeDelta = latitude2 - latitude1;
        double longitudeDelta = Math.toRadians(secondLongitude.doubleValue() - firstLongitude.doubleValue());
        double sinLatitude = Math.sin(latitudeDelta / 2.0);
        double sinLongitude = Math.sin(longitudeDelta / 2.0);
        double a = sinLatitude * sinLatitude
            + Math.cos(latitude1) * Math.cos(latitude2) * sinLongitude * sinLongitude;
        double c = 2.0 * Math.atan2(Math.sqrt(a), Math.sqrt(1.0 - a));
        return EARTH_RADIUS_KM * c;
    }

    private static void validate(EventEnvelope<ChefAcceptedOrderData> event) {
        Objects.requireNonNull(event, "event is required");
        if (event.eventId() == null) {
            throw new DeliveryMessageValidationException("eventId is required");
        }
        if (!DeliveryCommandModels.CHEF_ACCEPTED_ORDER.equals(event.eventType())) {
            throw new DeliveryMessageValidationException("eventType must be CHEF_ACCEPTED_ORDER");
        }
        if (!StringUtils.hasText(event.eventVersion())) {
            throw new DeliveryMessageValidationException("eventVersion is required");
        }
        if (event.occurredAt() == null || event.correlationId() == null) {
            throw new DeliveryMessageValidationException("occurredAt and correlationId are required");
        }
        if (!StringUtils.hasText(event.source()) || !StringUtils.hasText(event.subject())) {
            throw new DeliveryMessageValidationException("source and subject are required");
        }
        ChefAcceptedOrderData data = Objects.requireNonNull(event.data(), "event data is required");
        if (data.orderId() == null || data.chefSubOrderId() == null || data.readyAt() == null) {
            throw new DeliveryMessageValidationException(
                "orderId, chefSubOrderId and readyAt are required"
            );
        }
        if (data.deliveryRequest() == null) {
            throw new DeliveryMessageValidationException("deliveryRequest is required");
        }
        if (data.deliveryRequest().pickup() == null || data.deliveryRequest().dropoff() == null) {
            throw new DeliveryMessageValidationException("pickup and dropoff are required");
        }
    }

    private record RoutingContext(double distanceKm, String area, int orderHour, int dayOfWeek) {}

    public record ScheduleReceipt(
        UUID commandId,
        Instant dispatchAt,
        Long scheduledSequenceNumber,
        boolean duplicate
    ) {}

    public static class DeliveryMessageValidationException extends RuntimeException {
        public DeliveryMessageValidationException(String message) {
            super(message);
        }
    }
}
