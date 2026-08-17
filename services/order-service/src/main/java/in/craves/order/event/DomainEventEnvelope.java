package in.craves.order.event;

import java.time.Instant;
import java.util.UUID;

public record DomainEventEnvelope<T>(
    UUID eventId,
    String eventType,
    String eventVersion,
    Instant occurredAt,
    UUID correlationId,
    UUID causationId,
    String source,
    String subject,
    T data
) {
}
