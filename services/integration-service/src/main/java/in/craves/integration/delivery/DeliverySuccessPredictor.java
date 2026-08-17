package in.craves.integration.delivery;

import in.craves.integration.delivery.DeliveryIntelligenceModels.PartnerMetrics;

public interface DeliverySuccessPredictor {
    String version();
    double predict(double distanceKm, int orderHour, int dayOfWeek, PartnerMetrics metrics);
}
