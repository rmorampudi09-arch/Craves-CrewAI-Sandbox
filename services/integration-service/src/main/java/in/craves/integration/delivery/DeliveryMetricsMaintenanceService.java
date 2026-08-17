package in.craves.integration.delivery;

import in.craves.integration.config.DeliveryIntelligenceProperties;
import in.craves.integration.delivery.DeliveryMetricsRepository.RollingState;
import in.craves.integration.delivery.DeliveryMetricsRepository.ScoreRow;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeliveryMetricsMaintenanceService {
    private static final List<String> TRACKED_PARAMETERS = List.of(
        "completion", "pickup_timeliness", "delivery_timeliness",
        "cost_efficiency", "customer_rating_component"
    );

    private final DeliveryMetricsRepository repository;
    private final DeliveryIntelligenceProperties properties;
    private final Clock clock;

    @Autowired
    public DeliveryMetricsMaintenanceService(DeliveryMetricsRepository repository,
                                             DeliveryIntelligenceProperties properties) {
        this(repository, properties, Clock.systemUTC());
    }

    DeliveryMetricsMaintenanceService(DeliveryMetricsRepository repository,
                                      DeliveryIntelligenceProperties properties,
                                      Clock clock) {
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public MaintenanceReport archiveAgedScores() {
        if (!repository.tryMaintenanceLock()) return new MaintenanceReport(false, 0, 0, 0);
        Instant now = clock.instant();
        Instant cutoff = now.minusSeconds(properties.getLiveWindowDays() * 86_400L);
        List<ScoreRow> aged = repository.findScoresOlderThan(cutoff);
        if (aged.isEmpty()) return new MaintenanceReport(true, 0, 0, 0);

        ZoneId businessZone = ZoneId.of(properties.getMaintenanceZone());
        Map<String, Map<LocalDate, List<ScoreRow>>> groups = new LinkedHashMap<>();
        for (ScoreRow row : aged) {
            LocalDate date = row.occurredAt().atZone(businessZone).toLocalDate();
            groups.computeIfAbsent(row.providerId(), ignored -> new LinkedHashMap<>())
                .computeIfAbsent(date, ignored -> new ArrayList<>()).add(row);
        }

        int foldedDays = 0;
        for (Map.Entry<String, Map<LocalDate, List<ScoreRow>>> providerEntry : groups.entrySet()) {
            String providerId = providerEntry.getKey();
            List<LocalDate> dates = providerEntry.getValue().keySet().stream().sorted().toList();
            for (LocalDate date : dates) {
                foldOneDay(providerId, date, providerEntry.getValue().get(date), now);
                foldedDays++;
            }
        }
        repository.deleteScoresOlderThan(cutoff);
        return new MaintenanceReport(true, aged.size(), groups.size(), foldedDays);
    }

    private void foldOneDay(String providerId, LocalDate date, List<ScoreRow> rows, Instant foldedAt) {
        double dayAverage = rows.stream().mapToDouble(ScoreRow::compositeScore).average().orElse(properties.getGlobalPrior());
        Map<String, Double> dayBreakdown = averageBreakdown(rows);
        RollingState state = repository.loadRollingState(providerId, properties.getGlobalPrior());
        double oldWeight = Math.min(state.weight(), properties.getMaxStoredWeight());
        double fadedWeight = oldWeight * properties.getFadeFactor();
        double newWeightUncapped = fadedWeight + rows.size();
        double newAverage = newWeightUncapped == 0.0 ? state.average()
            : (fadedWeight * state.average() + rows.size() * dayAverage) / newWeightUncapped;

        Map<String, Double> newBreakdown = new LinkedHashMap<>();
        for (String parameter : TRACKED_PARAMETERS) {
            double oldValue = state.breakdown().getOrDefault(parameter, properties.getGlobalPrior());
            Double dayValue = dayBreakdown.get(parameter);
            double newValue = dayValue == null || newWeightUncapped == 0.0 ? oldValue
                : (fadedWeight * oldValue + rows.size() * dayValue) / newWeightUncapped;
            newBreakdown.put(parameter, DeliveryOutcomeScorer.round(newValue));
        }
        double storedWeight = Math.min(newWeightUncapped, properties.getMaxStoredWeight());
        repository.upsertRollingState(providerId, newAverage, storedWeight, newBreakdown, rows.size(), foldedAt);
        repository.upsertDailyArchive(providerId, date, dayAverage, dayBreakdown, rows.size(), newAverage);
    }

    private static Map<String, Double> averageBreakdown(List<ScoreRow> rows) {
        Map<String, Double> result = new LinkedHashMap<>();
        for (String parameter : TRACKED_PARAMETERS) {
            double average = rows.stream().map(ScoreRow::breakdown).map(map -> map.get(parameter))
                .filter(java.util.Objects::nonNull).mapToDouble(Double::doubleValue).average().orElse(Double.NaN);
            if (!Double.isNaN(average)) result.put(parameter, DeliveryOutcomeScorer.round(average));
        }
        return result;
    }

    public record MaintenanceReport(boolean lockAcquired, int archivedScores,
                                    int providersProcessed, int providerDaysFolded) {}
}
