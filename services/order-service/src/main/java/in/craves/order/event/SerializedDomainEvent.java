package in.craves.order.event;

import java.time.Instant;
import java.util.UUID;

public record SerializedDomainEvent(
    UUID eventId,
    String eventType,
    String eventVersion,
    Instant occurredAt,
    UUID correlationId,
    UUID causationId,
    String source,
    String subject,
    String payloadJson
) {
    public String eventKey() {
        return eventType + ":" + subject;
    }
}
