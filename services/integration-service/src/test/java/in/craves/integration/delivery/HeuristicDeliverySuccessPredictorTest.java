package in.craves.integration.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import in.craves.integration.delivery.DeliveryIntelligenceModels.Momentum;
import in.craves.integration.delivery.DeliveryIntelligenceModels.PartnerMetrics;
import java.util.Map;
import org.junit.jupiter.api.Test;

class HeuristicDeliverySuccessPredictorTest {
    private final HeuristicDeliverySuccessPredictor predictor = new HeuristicDeliverySuccessPredictor();

    @Test
    void improvingPartnerGetsHigherProbabilityThanDecliningPartner() {
        PartnerMetrics improving = metrics(92.0, 85.0, 7.0, Momentum.IMPROVING);
        PartnerMetrics declining = metrics(78.0, 85.0, -7.0, Momentum.DECLINING);
        double improvingProbability = predictor.predict(4.0, 18, 2, improving);
        double decliningProbability = predictor.predict(4.0, 18, 2, declining);
        assertThat(improvingProbability).isGreaterThan(decliningProbability);
    }

    @Test
    void outputIsAlwaysBounded() {
        PartnerMetrics metrics = metrics(100.0, 100.0, 50.0, Momentum.IMPROVING);
        assertThat(predictor.predict(0.0, 10, 0, metrics)).isBetween(0.0, 1.0);
    }

    private static PartnerMetrics metrics(double live, double stored, double delta, Momentum momentum) {
        return new PartnerMetrics("provider", live, 20, Map.of(), stored, 50, Map.of(),
            90, 0.67, momentum, delta, 3, 2);
    }
}
