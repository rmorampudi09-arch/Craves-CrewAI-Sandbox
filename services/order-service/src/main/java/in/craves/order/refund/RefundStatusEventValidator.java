package in.craves.order.refund;

import in.craves.order.refund.RefundStatusModels.EventEnvelope;
import in.craves.order.refund.RefundStatusModels.RefundStatusChangedData;
import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RefundStatusEventValidator {
    private static final Set<String> REASONS = Set.of(
        "CHEF_DECLINED",
        "CHEF_ACCEPTANCE_TIMEOUT"
    );
    private static final Map<String, Set<String>> PROVIDER_STATUSES = Map.of(
        "REFUND_PENDING", Set.of("PENDING", "ONHOLD"),
        "REFUNDED", Set.of("SUCCESS"),
        "REFUND_FAILED", Set.of("FAILED", "CANCELLED")
    );

    public void validate(EventEnvelope<RefundStatusChangedData> event) {
        if (event == null || event.eventId() == null || event.data() == null) {
            throw new RefundStatusValidationException("Refund status event envelope is incomplete");
        }
        if (!"REFUND_STATUS_CHANGED".equals(event.eventType())) {
            throw new RefundStatusValidationException("Unexpected refund status event type");
        }
        if (!"1.0".equals(event.eventVersion())) {
            throw new RefundStatusValidationException("Unsupported refund status event version");
        }
        if (!"integration-service".equals(event.source())) {
            throw new RefundStatusValidationException("Unexpected refund status event source");
        }
        if (event.occurredAt() == null || event.correlationId() == null || event.causationId() == null) {
            throw new RefundStatusValidationException("Refund status event tracing fields are required");
        }

        RefundStatusChangedData data = event.data();
        if (data.refundId() == null
            || data.checkoutId() == null
            || data.chefSubOrderId() == null
            || data.customerIdentityId() == null) {
            throw new RefundStatusValidationException("Refund status business identifiers are required");
        }
        if (!StringUtils.hasText(event.subject())
            || !data.chefSubOrderId().toString().equals(event.subject())) {
            throw new RefundStatusValidationException("Refund status subject does not match chef sub-order");
        }
        if (!event.correlationId().equals(data.checkoutId())) {
            throw new RefundStatusValidationException("Refund status correlation ID does not match checkout");
        }
        if (!StringUtils.hasText(data.refundReference())
            || data.refundReference().length() < 3
            || data.refundReference().length() > 40) {
            throw new RefundStatusValidationException("Refund reference is invalid");
        }
        if (data.refundAmount() == null || data.refundAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RefundStatusValidationException("Refund amount must be positive");
        }
        if (!"INR".equals(data.currency())) {
            throw new RefundStatusValidationException("Only INR refund status events are supported");
        }
        if (!REASONS.contains(data.reason())) {
            throw new RefundStatusValidationException("Unsupported refund status reason");
        }
        Set<String> expectedProviderStatuses = PROVIDER_STATUSES.get(data.status());
        if (expectedProviderStatuses == null) {
            throw new RefundStatusValidationException("Unsupported normalized refund status");
        }
        if (!expectedProviderStatuses.contains(data.providerStatus())) {
            throw new RefundStatusValidationException("Provider status does not match normalized refund status");
        }
        if (data.updatedAt() == null) {
            throw new RefundStatusValidationException("Refund status update timestamp is required");
        }
    }

    public static class RefundStatusValidationException extends RuntimeException {
        public RefundStatusValidationException(String message) {
            super(message);
        }
    }
}
