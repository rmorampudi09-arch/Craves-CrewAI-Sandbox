package in.craves.integration.delivery;

import in.craves.integration.config.DeliveryIntelligenceProperties;
import in.craves.integration.delivery.DeliveryIntelligenceModels.DeliveryOutcomeRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class DeliveryOutcomeScorer {
    private static final double COMPLETION_WEIGHT = 0.30;
    private static final double PICKUP_TIMELINESS_WEIGHT = 0.15;
    private static final double DELIVERY_TIMELINESS_WEIGHT = 0.20;
    private static final double CUSTOMER_RATING_WEIGHT = 0.25;
    private static final double COST_EFFICIENCY_WEIGHT = 0.10;
    private static final double COMPLAINT_DEDUCTION = 5.0;

    private final DeliveryIntelligenceProperties properties;

    public DeliveryOutcomeScorer(DeliveryIntelligenceProperties properties) {
        this.properties = properties;
    }

    public ScoredOutcome score(DeliveryOutcomeRequest outcome) {
        boolean completed = outcome.status() == DeliveryIntelligenceModels.OutcomeStatus.DELIVERED;
        double completion = completed ? 100.0 : 0.0;
        double pickupTimeliness = completed
            ? timelinessScore(outcome.promisedPickupMinutes(), outcome.actualPickupMinutes()) : 0.0;
        double deliveryTimeliness = completed
            ? timelinessScore(outcome.promisedDeliveryMinutes(), outcome.actualDeliveryMinutes()) : 0.0;
        double costEfficiency = completed
            ? costEfficiencyScore(outcome.quotedCost(), outcome.actualCost()) : 0.0;
        double rating = completed
            ? (outcome.customerRating() == null
                ? properties.getNeutralRatingScore()
                : outcome.customerRating() / 5.0 * 100.0)
            : 0.0;

        double raw = COMPLETION_WEIGHT * completion
            + PICKUP_TIMELINESS_WEIGHT * pickupTimeliness
            + DELIVERY_TIMELINESS_WEIGHT * deliveryTimeliness
            + CUSTOMER_RATING_WEIGHT * rating
            + COST_EFFICIENCY_WEIGHT * costEfficiency;
        double complaintDeduction = outcome.hadComplaint() ? COMPLAINT_DEDUCTION : 0.0;
        double composite = clamp(raw - complaintDeduction);

        Map<String, Double> breakdown = new LinkedHashMap<>();
        breakdown.put("completion", round(completion));
        breakdown.put("pickup_timeliness", round(pickupTimeliness));
        breakdown.put("delivery_timeliness", round(deliveryTimeliness));
        breakdown.put("cost_efficiency", round(costEfficiency));
        breakdown.put("customer_rating_component", round(rating));
        breakdown.put("complaint_deduction", round(complaintDeduction));
        breakdown.put("raw_before_penalty", round(raw));
        return new ScoredOutcome(round(composite), breakdown);
    }

    static double timelinessScore(double promised, Double actual) {
        if (actual == null || promised <= 0) return 0.0;
        double grace = promised * 0.10;
        double delay = actual - promised - grace;
        if (delay <= 0) return 100.0;
        return Math.max(0.0, 100.0 * Math.exp(-delay / Math.max(promised, 1.0)));
    }

    static double costEfficiencyScore(BigDecimal quoted, BigDecimal actual) {
        if (quoted == null || actual == null || quoted.signum() <= 0) return 100.0;
        double deviation = actual.subtract(quoted).abs()
            .divide(quoted, 8, RoundingMode.HALF_UP).doubleValue();
        deviation = Math.max(0.0, deviation - 0.05);
        return Math.max(0.0, 100.0 - deviation * 200.0);
    }

    private static double clamp(double value) { return Math.max(0.0, Math.min(100.0, value)); }
    static double round(double value) { return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue(); }

    public record ScoredOutcome(double compositeScore, Map<String, Double> breakdown) {}
}
