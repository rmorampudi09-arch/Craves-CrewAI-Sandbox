package in.craves.integration.refund;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import in.craves.integration.refund.RefundModels.ProviderRefundResult;
import in.craves.integration.refund.RefundModels.RefundWorkItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class RefundStatusEventFactory {
    private static final String EVENT_TYPE = "REFUND_STATUS_CHANGED";
    private static final String EVENT_VERSION = "1.0";
    private static final String SOURCE = "integration-service";

    private final ObjectMapper objectMapper;

    public RefundStatusEventFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper.copy()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public SerializedRefundStatusEvent create(
        RefundWorkItem workItem,
        String normalizedStatus,
        ProviderRefundResult providerResult,
        Instant occurredAt
    ) {
        UUID eventId = UUID.randomUUID();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("refundId", workItem.refundId());
        data.put("checkoutId", workItem.checkoutId());
        data.put("chefSubOrderId", workItem.chefSubOrderId());
        data.put("customerIdentityId", workItem.customerIdentityId());
        data.put("refundReference", workItem.refundReference());
        data.put("refundAmount", scale(workItem.amount()));
        data.put("currency", workItem.currency());
        data.put("reason", workItem.reason());
        data.put("status", normalizedStatus);
        data.put("provider", workItem.provider());
        data.put("providerStatus", providerResult.providerStatus());
        data.put("providerRefundId", providerResult.cfRefundId());
        data.put("cfRefundId", "CASHFREE".equalsIgnoreCase(workItem.provider()) ? providerResult.cfRefundId() : null);
        data.put("updatedAt", occurredAt);

        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", EVENT_TYPE);
        envelope.put("eventVersion", EVENT_VERSION);
        envelope.put("occurredAt", occurredAt);
        envelope.put("correlationId", workItem.checkoutId());
        envelope.put("causationId", workItem.requestEventId());
        envelope.put("source", SOURCE);
        envelope.put("subject", workItem.chefSubOrderId());
        envelope.put("data", data);

        return new SerializedRefundStatusEvent(
            eventId,
            EVENT_TYPE,
            EVENT_VERSION,
            occurredAt,
            workItem.checkoutId(),
            workItem.requestEventId(),
            workItem.chefSubOrderId(),
            serialize(envelope),
            EVENT_TYPE + ":" + workItem.refundId() + ":" + normalizedStatus + ":" + providerResult.providerStatus()
        );
    }

    private String serialize(Map<String, Object> envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Refund status event could not be serialized", exception);
        }
    }

    private static BigDecimal scale(BigDecimal amount) {
        return amount == null ? null : amount.setScale(2);
    }

    public record SerializedRefundStatusEvent(
        UUID eventId,
        String eventType,
        String eventVersion,
        Instant occurredAt,
        UUID correlationId,
        UUID causationId,
        UUID subject,
        String payloadJson,
        String eventKey
    ) {
    }
}
