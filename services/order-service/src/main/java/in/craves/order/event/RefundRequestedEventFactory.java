package in.craves.order.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RefundRequestedEventFactory {
    public static final String EVENT_TYPE = "REFUND_REQUESTED";
    public static final String EVENT_VERSION = "1.0";
    public static final String SOURCE = "order-service";

    private final ObjectMapper objectMapper;

    public RefundRequestedEventFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public SerializedDomainEvent create(
        RefundRequestedEventSource source,
        UUID requestedCorrelationId,
        String idempotencyKey
    ) {
        validate(source);

        UUID eventId = UUID.randomUUID();
        UUID correlationId = requestedCorrelationId == null ? source.checkoutId() : requestedCorrelationId;
        UUID causationId = causationId(source, idempotencyKey);
        RefundRequestedEventData data = new RefundRequestedEventData(
            source.checkoutId(),
            source.chefSubOrderId(),
            source.customerIdentityId(),
            source.refundAmount(),
            source.currency().trim().toUpperCase(),
            source.reason(),
            source.requestedAt()
        );
        DomainEventEnvelope<RefundRequestedEventData> envelope = new DomainEventEnvelope<>(
            eventId,
            EVENT_TYPE,
            EVENT_VERSION,
            source.requestedAt(),
            correlationId,
            causationId,
            SOURCE,
            source.chefSubOrderId().toString(),
            data
        );

        try {
            return new SerializedDomainEvent(
                eventId,
                EVENT_TYPE,
                EVENT_VERSION,
                source.requestedAt(),
                correlationId,
                causationId,
                SOURCE,
                source.chefSubOrderId().toString(),
                objectMapper.writeValueAsString(envelope)
            );
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("REFUND_REQUESTED serialization failed", exception);
        }
    }

    private static UUID causationId(RefundRequestedEventSource source, String idempotencyKey) {
        String stableInput = StringUtils.hasText(idempotencyKey)
            ? idempotencyKey.trim()
            : source.reason() + ":" + source.chefSubOrderId();
        return UUID.nameUUIDFromBytes(
            ("refund-request:" + stableInput).getBytes(StandardCharsets.UTF_8)
        );
    }

    private static void validate(RefundRequestedEventSource source) {
        Objects.requireNonNull(source, "event source is required");
        Objects.requireNonNull(source.checkoutId(), "checkoutId is required");
        Objects.requireNonNull(source.chefSubOrderId(), "chefSubOrderId is required");
        Objects.requireNonNull(source.customerIdentityId(), "customerIdentityId is required");
        Objects.requireNonNull(source.refundAmount(), "refundAmount is required");
        Objects.requireNonNull(source.requestedAt(), "requestedAt is required");
        if (source.refundAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("refundAmount must be positive");
        }
        if (!StringUtils.hasText(source.currency())) {
            throw new IllegalArgumentException("currency is required");
        }
        if (!"CHEF_DECLINED".equals(source.reason())
            && !"CHEF_ACCEPTANCE_TIMEOUT".equals(source.reason())) {
            throw new IllegalArgumentException("Unsupported refund reason");
        }
    }
}
