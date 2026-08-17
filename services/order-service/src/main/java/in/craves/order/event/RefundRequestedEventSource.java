package in.craves.order.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundRequestedEventSource(
    UUID checkoutId,
    UUID chefSubOrderId,
    UUID customerIdentityId,
    BigDecimal refundAmount,
    String currency,
    String reason,
    Instant requestedAt
) {
}
