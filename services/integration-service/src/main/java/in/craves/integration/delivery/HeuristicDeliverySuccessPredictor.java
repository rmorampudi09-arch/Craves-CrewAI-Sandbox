package in.craves.integration.delivery;

import in.craves.integration.delivery.DeliveryIntelligenceModels.PartnerMetrics;
import org.springframework.stereotype.Component;

@Component
public class HeuristicDeliverySuccessPredictor implements DeliverySuccessPredictor {
    @Override
    public String version() { return "HEURISTIC_V1"; }

    @Override
    public double predict(double distanceKm, int orderHour, int dayOfWeek, PartnerMetrics metrics) {
        double live = metrics.liveAverage() == null ? metrics.storedAverage() : metrics.liveAverage();
        double blended = metrics.liveShare() * live + (1.0 - metrics.liveShare()) * metrics.storedAverage();
        double trendAdjustment = (metrics.momentumDelta() == null ? 0.0 : metrics.momentumDelta()) / 400.0;
        return Math.max(0.0, Math.min(1.0, blended / 100.0 + trendAdjustment));
    }
}
