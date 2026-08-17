package in.craves.order.outbox;

import java.time.Instant;
import java.util.UUID;

public record OrderDomainOutboxRecord(
    UUID id,
    String eventKey,
    UUID aggregateId,
    String eventType,
    String eventVersion,
    Instant occurredAt,
    UUID correlationId,
    UUID causationId,
    String source,
    String subject,
    String payloadJson,
    int attempts,
    UUID lockToken
) {
}
