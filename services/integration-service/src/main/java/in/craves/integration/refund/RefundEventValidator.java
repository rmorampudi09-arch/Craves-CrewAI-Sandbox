package in.craves.integration.refund;

import in.craves.integration.refund.RefundModels.EventEnvelope;
import in.craves.integration.refund.RefundModels.RefundRequestedData;
import java.math.BigDecimal;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RefundEventValidator {
    private static final Set<String> REASONS = Set.of(
        "CHEF_DECLINED",
        "CHEF_ACCEPTANCE_TIMEOUT"
    );

    public void validate(EventEnvelope<RefundRequestedData> event) {
        if (event == null || event.eventId() == null || event.data() == null) {
            throw new RefundMessageValidationException("Refund event envelope is incomplete");
        }
        if (!"REFUND_REQUESTED".equals(event.eventType())) {
            throw new RefundMessageValidationException("Unexpected event type");
        }
        if (!"1.0".equals(event.eventVersion())) {
            throw new RefundMessageValidationException("Unsupported refund event version");
        }
        if (!"order-service".equals(event.source())) {
            throw new RefundMessageValidationException("Unexpected refund event source");
        }
        if (event.correlationId() == null || event.causationId() == null) {
            throw new RefundMessageValidationException("Refund event tracing identifiers are required");
        }

        RefundRequestedData data = event.data();
        if (data.checkoutId() == null || data.chefSubOrderId() == null || data.customerIdentityId() == null) {
            throw new RefundMessageValidationException("Refund business identifiers are required");
        }
        if (!data.chefSubOrderId().toString().equals(event.subject())) {
            throw new RefundMessageValidationException("Refund event subject does not match chef sub-order");
        }
        if (data.refundAmount() == null || data.refundAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new RefundMessageValidationException("Refund amount must be positive");
        }
        if (!"INR".equals(data.currency())) {
            throw new RefundMessageValidationException("Only INR refunds are supported");
        }
        if (!REASONS.contains(data.reason())) {
            throw new RefundMessageValidationException("Unsupported refund reason");
        }
        if (data.requestedAt() == null || !StringUtils.hasText(event.subject())) {
            throw new RefundMessageValidationException("Refund timestamps and subject are required");
        }
    }

    public static class RefundMessageValidationException extends RuntimeException {
        public RefundMessageValidationException(String message) {
            super(message);
        }
    }
}
