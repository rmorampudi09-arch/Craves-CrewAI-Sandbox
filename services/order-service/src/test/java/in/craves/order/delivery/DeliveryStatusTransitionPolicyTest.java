package in.craves.order.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import in.craves.order.delivery.DeliveryStatusTransitionPolicy.Decision;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class DeliveryStatusTransitionPolicyTest {
    private final DeliveryStatusTransitionPolicy policy = new DeliveryStatusTransitionPolicy();

    @Test
    void appliesFirstAndNewerChanges() {
        assertThat(policy.decide(
            null,
            null,
            null,
            "COURIER_ASSIGNED",
            null,
            Instant.parse("2026-07-28T08:00:00Z")
        )).isEqualTo(Decision.APPLY);

        assertThat(policy.decide(
            "COURIER_ASSIGNED",
            null,
            Instant.parse("2026-07-28T08:00:00Z"),
            "IN_TRANSIT",
            "https://track.example/1",
            Instant.parse("2026-07-28T08:05:00Z")
        )).isEqualTo(Decision.APPLY);
    }

    @Test
    void rejectsStaleAndProtectsTerminalStatus() {
        assertThat(policy.decide(
            "IN_TRANSIT",
            null,
            Instant.parse("2026-07-28T08:05:00Z"),
            "PICKED_UP",
            null,
            Instant.parse("2026-07-28T08:04:59Z")
        )).isEqualTo(Decision.STALE);

        assertThat(policy.decide(
            "DELIVERED",
            null,
            Instant.parse("2026-07-28T08:05:00Z"),
            "IN_TRANSIT",
            null,
            Instant.parse("2026-07-28T08:06:00Z")
        )).isEqualTo(Decision.TERMINAL_PROTECTED);
    }

    @Test
    void identifiesNoStateChange() {
        assertThat(policy.decide(
            "IN_TRANSIT",
            "https://track.example/1",
            Instant.parse("2026-07-28T08:05:00Z"),
            "IN_TRANSIT",
            "https://track.example/1",
            Instant.parse("2026-07-28T08:06:00Z")
        )).isEqualTo(Decision.NO_CHANGE);
    }
}
