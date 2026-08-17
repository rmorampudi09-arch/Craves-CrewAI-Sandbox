package in.craves.order.delivery;

import in.craves.order.delivery.DeliveryStatusModels.DeliveryStatusChangedData;
import in.craves.order.delivery.DeliveryStatusModels.EventEnvelope;
import java.net.URI;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class DeliveryStatusEventValidator {
    private static final Set<String> STATUSES = Set.of(
        "PENDING",
        "SEARCHING",
        "COURIER_ASSIGNED",
        "COURIER_TO_PICKUP",
        "AT_PICKUP",
        "PICKED_UP",
        "IN_TRANSIT",
        "AT_DROPOFF",
        "DELIVERED",
        "CANCELLED",
        "DELAYED",
        "RETURNING",
        "RETURNED",
        "FAILED"
    );

    public void validate(EventEnvelope<DeliveryStatusChangedData> event) {
        if (event == null || event.eventId() == null || event.data() == null) {
            throw new DeliveryStatusValidationException("Delivery status event envelope is incomplete");
        }
        if (!"DELIVERY_STATUS_CHANGED".equals(event.eventType())) {
            throw new DeliveryStatusValidationException("Unexpected delivery status event type");
        }
        if (!"1.0".equals(event.eventVersion())) {
            throw new DeliveryStatusValidationException("Unsupported delivery status event version");
        }
        if (!"integration-service".equals(event.source())) {
            throw new DeliveryStatusValidationException("Unexpected delivery status event source");
        }
        if (event.occurredAt() == null || event.correlationId() == null) {
            throw new DeliveryStatusValidationException("Delivery status tracing fields are required");
        }

        DeliveryStatusChangedData data = event.data();
        if (data.deliveryJobId() == null || data.orderId() == null || data.chefSubOrderId() == null) {
            throw new DeliveryStatusValidationException("Delivery status business identifiers are required");
        }
        if (!event.correlationId().equals(data.orderId())) {
            throw new DeliveryStatusValidationException("Delivery status correlation ID does not match checkout");
        }
        String expectedSubject = "delivery-job/" + data.deliveryJobId();
        if (!expectedSubject.equals(event.subject())) {
            throw new DeliveryStatusValidationException("Delivery status subject does not match delivery job");
        }
        requireText(data.providerId(), "providerId");
        requireText(data.providerDeliveryId(), "providerDeliveryId");
        if (!STATUSES.contains(data.status())) {
            throw new DeliveryStatusValidationException("Unsupported normalized delivery status");
        }
        if (data.observedAt() == null) {
            throw new DeliveryStatusValidationException("Delivery status observation timestamp is required");
        }
        validateTrackingUrl(data.trackingUrl());
    }

    private static void validateTrackingUrl(String trackingUrl) {
        if (!StringUtils.hasText(trackingUrl)) {
            return;
        }
        final URI uri;
        try {
            uri = URI.create(trackingUrl);
        } catch (IllegalArgumentException exception) {
            throw new DeliveryStatusValidationException("Delivery tracking URL is invalid");
        }
        if (!uri.isAbsolute() || !("https".equalsIgnoreCase(uri.getScheme())
            || "http".equalsIgnoreCase(uri.getScheme()))) {
            throw new DeliveryStatusValidationException("Delivery tracking URL must be HTTP or HTTPS");
        }
    }

    private static void requireText(String value, String field) {
        if (!StringUtils.hasText(value)) {
            throw new DeliveryStatusValidationException("Delivery status " + field + " is required");
        }
    }

    public static class DeliveryStatusValidationException extends RuntimeException {
        public DeliveryStatusValidationException(String message) {
            super(message);
        }
    }
}
