package in.craves.integration.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "craves.delivery-intelligence")
public class DeliveryIntelligenceProperties {
    private boolean enabled = true;
    private int liveWindowDays = 7;
    private double globalPrior = 100.0;
    private double momentumThreshold = 3.0;
    private double livePullK = 10.0;
    private double fadeFactor = 0.965;
    private double maxStoredWeight = 400.0;
    private double mlWeight = 0.55;
    private double rollingScoreWeight = 0.35;
    private double explorationWeight = 0.10;
    private double proximityWeight = 0.60;
    private double qualityWeight = 0.40;
    private double searchRadiusKm = 5.0;
    private double maxPickupEtaMinutes = 30.0;
    private double unknownProximityScore = 50.0;
    private double softmaxTemperature = 10.0;
    private double successThreshold = 70.0;
    private double neutralRatingScore = 70.0;
    private String defaultStrategy = "STOCHASTIC";
    private String maintenanceCron = "0 0 2 * * *";
    private String maintenanceZone = "Asia/Kolkata";

    @PostConstruct
    void validate() {
        validateUnitSum("quality scoring weights", mlWeight + rollingScoreWeight + explorationWeight);
        validateUnitSum("candidate ranking weights", proximityWeight + qualityWeight);
        if (liveWindowDays < 1) throw new IllegalStateException("liveWindowDays must be at least 1");
        if (livePullK <= 0) throw new IllegalStateException("livePullK must be positive");
        if (fadeFactor <= 0 || fadeFactor > 1) throw new IllegalStateException("fadeFactor must be in (0,1]");
        if (searchRadiusKm <= 0) throw new IllegalStateException("searchRadiusKm must be positive");
        if (maxPickupEtaMinutes <= 0) throw new IllegalStateException("maxPickupEtaMinutes must be positive");
        if (unknownProximityScore < 0 || unknownProximityScore > 100) {
            throw new IllegalStateException("unknownProximityScore must be between 0 and 100");
        }
        if (softmaxTemperature <= 0) throw new IllegalStateException("softmaxTemperature must be positive");
        try {
            in.craves.integration.delivery.DeliveryIntelligenceModels.AssignmentStrategy.valueOf(
                defaultStrategy.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (Exception ex) {
            throw new IllegalStateException("defaultStrategy must be GREEDY or STOCHASTIC", ex);
        }
    }

    private static void validateUnitSum(String name, double value) {
        if (Math.abs(value - 1.0) > 0.000001) {
            throw new IllegalStateException(name + " must sum to 1.0, got " + value);
        }
    }

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public int getLiveWindowDays() { return liveWindowDays; }
    public void setLiveWindowDays(int liveWindowDays) { this.liveWindowDays = liveWindowDays; }
    public double getGlobalPrior() { return globalPrior; }
    public void setGlobalPrior(double globalPrior) { this.globalPrior = globalPrior; }
    public double getMomentumThreshold() { return momentumThreshold; }
    public void setMomentumThreshold(double momentumThreshold) { this.momentumThreshold = momentumThreshold; }
    public double getLivePullK() { return livePullK; }
    public void setLivePullK(double livePullK) { this.livePullK = livePullK; }
    public double getFadeFactor() { return fadeFactor; }
    public void setFadeFactor(double fadeFactor) { this.fadeFactor = fadeFactor; }
    public double getMaxStoredWeight() { return maxStoredWeight; }
    public void setMaxStoredWeight(double maxStoredWeight) { this.maxStoredWeight = maxStoredWeight; }
    public double getMlWeight() { return mlWeight; }
    public void setMlWeight(double mlWeight) { this.mlWeight = mlWeight; }
    public double getRollingScoreWeight() { return rollingScoreWeight; }
    public void setRollingScoreWeight(double rollingScoreWeight) { this.rollingScoreWeight = rollingScoreWeight; }
    public double getExplorationWeight() { return explorationWeight; }
    public void setExplorationWeight(double explorationWeight) { this.explorationWeight = explorationWeight; }
    public double getProximityWeight() { return proximityWeight; }
    public void setProximityWeight(double proximityWeight) { this.proximityWeight = proximityWeight; }
    public double getQualityWeight() { return qualityWeight; }
    public void setQualityWeight(double qualityWeight) { this.qualityWeight = qualityWeight; }
    public double getSearchRadiusKm() { return searchRadiusKm; }
    public void setSearchRadiusKm(double searchRadiusKm) { this.searchRadiusKm = searchRadiusKm; }
    public double getMaxPickupEtaMinutes() { return maxPickupEtaMinutes; }
    public void setMaxPickupEtaMinutes(double maxPickupEtaMinutes) { this.maxPickupEtaMinutes = maxPickupEtaMinutes; }
    public double getUnknownProximityScore() { return unknownProximityScore; }
    public void setUnknownProximityScore(double unknownProximityScore) {
        this.unknownProximityScore = unknownProximityScore;
    }
    public double getSoftmaxTemperature() { return softmaxTemperature; }
    public void setSoftmaxTemperature(double softmaxTemperature) { this.softmaxTemperature = softmaxTemperature; }
    public double getSuccessThreshold() { return successThreshold; }
    public void setSuccessThreshold(double successThreshold) { this.successThreshold = successThreshold; }
    public double getNeutralRatingScore() { return neutralRatingScore; }
    public void setNeutralRatingScore(double neutralRatingScore) { this.neutralRatingScore = neutralRatingScore; }
    public String getDefaultStrategy() { return defaultStrategy; }
    public void setDefaultStrategy(String defaultStrategy) { this.defaultStrategy = defaultStrategy; }
    public String getMaintenanceCron() { return maintenanceCron; }
    public void setMaintenanceCron(String maintenanceCron) { this.maintenanceCron = maintenanceCron; }
    public String getMaintenanceZone() { return maintenanceZone; }
    public void setMaintenanceZone(String maintenanceZone) { this.maintenanceZone = maintenanceZone; }
}
