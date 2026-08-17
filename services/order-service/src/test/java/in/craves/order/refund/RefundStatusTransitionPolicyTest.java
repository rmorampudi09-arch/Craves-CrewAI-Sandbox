package in.craves.order.refund;

import static org.assertj.core.api.Assertions.assertThat;

import in.craves.order.refund.RefundStatusTransitionPolicy.Decision;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RefundStatusTransitionPolicyTest {
    private final RefundStatusTransitionPolicy policy = new RefundStatusTransitionPolicy();

    @Test
    void appliesNewerRefundStatus() {
        Decision decision = policy.decide(
            "REFUND_PENDING",
            Instant.parse("2026-07-16T10:00:00Z"),
            "REFUNDED",
            Instant.parse("2026-07-16T10:05:00Z")
        );

        assertThat(decision).isEqualTo(Decision.APPLY);
    }

    @Test
    void ignoresOlderOrDuplicateTimestamp() {
        Decision decision = policy.decide(
            "REFUND_PENDING",
            Instant.parse("2026-07-16T10:05:00Z"),
            "REFUNDED",
            Instant.parse("2026-07-16T10:05:00Z")
        );

        assertThat(decision).isEqualTo(Decision.STALE);
    }

    @Test
    void neverDowngradesRefundedOrder() {
        Decision decision = policy.decide(
            "REFUNDED",
            Instant.parse("2026-07-16T10:00:00Z"),
            "REFUND_PENDING",
            Instant.parse("2026-07-16T10:05:00Z")
        );

        assertThat(decision).isEqualTo(Decision.STALE);
    }
}
