package in.craves.integration.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import in.craves.integration.config.DeliveryIntelligenceProperties;
import in.craves.integration.delivery.DeliveryIntelligenceModels.DeliveryOutcomeRequest;
import in.craves.integration.delivery.DeliveryIntelligenceModels.OutcomeStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeliveryOutcomeScorerTest {
    private final DeliveryOutcomeScorer scorer = new DeliveryOutcomeScorer(new DeliveryIntelligenceProperties());

    @Test
    void scoresPerfectDeliveryAtOneHundred() {
        var result = scorer.score(outcome(OutcomeStatus.DELIVERED, 10.0, 19.0, 25.0, 4.5, false));
        assertThat(result.compositeScore()).isBetween(96.0, 100.0);
        assertThat(result.breakdown()).containsEntry("completion", 100.0);
    }

    @Test
    void failedDeliveryScoresZero() {
        var result = scorer.score(outcome(OutcomeStatus.FAILED, null, null, 25.0, null, true));
        assertThat(result.compositeScore()).isZero();
    }

    @Test
    void complaintCreatesAbsoluteFivePointDeduction() {
        var clean = scorer.score(outcome(OutcomeStatus.DELIVERED, 10.0, 19.0, 25.0, 5.0, false));
        var complained = scorer.score(outcome(OutcomeStatus.DELIVERED, 10.0, 19.0, 25.0, 5.0, true));
        assertThat(clean.compositeScore() - complained.compositeScore()).isEqualTo(5.0);
    }

    private static DeliveryOutcomeRequest outcome(OutcomeStatus status, Double pickup, Double delivery,
                                                   double actualCost, Double rating, boolean complaint) {
        return new DeliveryOutcomeRequest(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), "borzo",
            status, 10.0, pickup, 20.0, delivery, BigDecimal.valueOf(25), BigDecimal.valueOf(actualCost),
            rating, complaint, 4.0, "Madhapur", 19, 1, Instant.parse("2026-07-14T12:00:00Z"));
    }
}
