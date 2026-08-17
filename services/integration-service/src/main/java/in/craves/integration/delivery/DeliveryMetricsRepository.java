package in.craves.integration.delivery;

import in.craves.integration.config.DeliveryIntelligenceProperties;
import in.craves.integration.delivery.DeliveryIntelligenceModels.DeliveryOutcomeRequest;
import in.craves.integration.delivery.DeliveryIntelligenceModels.DeliveryScoreResponse;
import in.craves.integration.delivery.DeliveryIntelligenceModels.Momentum;
import in.craves.integration.delivery.DeliveryIntelligenceModels.PartnerMetrics;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DeliveryMetricsRepository {
    private final JdbcTemplate jdbc;
    private final DeliveryJsonSupport json;

    public DeliveryMetricsRepository(JdbcTemplate jdbc, DeliveryJsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public PartnerMetrics load(String providerId, Instant now, DeliveryIntelligenceProperties properties) {
        String normalizedProvider = DeliveryProviderRepository.normalize(providerId);
        Instant since = now.minusSeconds(properties.getLiveWindowDays() * 86_400L);
        LiveAggregate live = jdbc.query("""
            SELECT COUNT(*) AS live_count,
                   AVG(composite_score) AS live_avg,
                   AVG((breakdown->>'completion')::double precision) AS completion,
                   AVG((breakdown->>'pickup_timeliness')::double precision) AS pickup_timeliness,
                   AVG((breakdown->>'delivery_timeliness')::double precision) AS delivery_timeliness,
                   AVG((breakdown->>'cost_efficiency')::double precision) AS cost_efficiency,
                   AVG((breakdown->>'customer_rating_component')::double precision) AS customer_rating_component
              FROM delivery_schema.delivery_score_hot
             WHERE provider_id = ? AND occurred_at >= ?
            """, rs -> {
                rs.next();
                Map<String, Double> breakdown = new LinkedHashMap<>();
                putNullable(breakdown, "completion", nullableDouble(rs, "completion"));
                putNullable(breakdown, "pickup_timeliness", nullableDouble(rs, "pickup_timeliness"));
                putNullable(breakdown, "delivery_timeliness", nullableDouble(rs, "delivery_timeliness"));
                putNullable(breakdown, "cost_efficiency", nullableDouble(rs, "cost_efficiency"));
                putNullable(breakdown, "customer_rating_component", nullableDouble(rs, "customer_rating_component"));
                return new LiveAggregate(rs.getLong("live_count"), nullableDouble(rs, "live_avg"), breakdown);
            }, normalizedProvider, Timestamp.from(since));

        RollingState stored = loadRollingState(normalizedProvider, properties.getGlobalPrior());
        double[] bandit = jdbc.query("""
            SELECT alpha, beta FROM delivery_schema.delivery_partner_bandit_state WHERE provider_id = ?
            """, rs -> rs.next()
                ? new double[]{rs.getDouble("alpha"), rs.getDouble("beta")}
                : new double[]{1.0, 1.0}, normalizedProvider);

        double liveShare = live.count() == 0 ? 0.0 : live.count() / (live.count() + properties.getLivePullK());
        double combined = live.average() == null
            ? stored.average()
            : liveShare * live.average() + (1.0 - liveShare) * stored.average();
        Double delta = live.average() == null || stored.weight() <= 0 ? null : live.average() - stored.average();
        Momentum momentum = delta == null ? Momentum.INSUFFICIENT_DATA
            : delta > properties.getMomentumThreshold() ? Momentum.IMPROVING
            : delta < -properties.getMomentumThreshold() ? Momentum.DECLINING : Momentum.STABLE;

        return new PartnerMetrics(
            normalizedProvider,
            roundNullable(live.average()),
            live.count(),
            roundMap(live.breakdown()),
            round(stored.average()),
            round(stored.weight()),
            roundMap(stored.breakdown()),
            round(combined),
            round(liveShare, 3),
            momentum,
            roundNullable(delta),
            bandit[0],
            bandit[1]
        );
    }

    public DeliveryScoreResponse insertOutcomeIfAbsent(DeliveryOutcomeRequest request,
                                                        DeliveryOutcomeScorer.ScoredOutcome score,
                                                        Instant occurredAt,
                                                        boolean successful) {
        String providerId = DeliveryProviderRepository.normalize(request.providerId());
        int inserted = jdbc.update("""
            INSERT INTO delivery_schema.delivery_outcome_receipt
                (delivery_id, chef_sub_order_id, order_id, provider_id, composite_score,
                 breakdown, occurred_at, received_at)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?, now())
            ON CONFLICT (delivery_id) DO NOTHING
            """, request.deliveryId(), request.chefSubOrderId(), request.orderId(), providerId,
            score.compositeScore(), json.write(score.breakdown()), Timestamp.from(occurredAt));

        if (inserted == 1) {
            jdbc.update("""
                INSERT INTO delivery_schema.delivery_score_hot
                    (delivery_id, chef_sub_order_id, order_id, provider_id, composite_score, status,
                     distance_km, area, order_hour, day_of_week, occurred_at, breakdown, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now())
                """, request.deliveryId(), request.chefSubOrderId(), request.orderId(), providerId,
                score.compositeScore(), request.status().name(), request.distanceKm(), request.area(),
                request.orderHour(), request.dayOfWeek(), Timestamp.from(occurredAt), json.write(score.breakdown()));
            jdbc.update("""
                INSERT INTO delivery_schema.delivery_partner_bandit_state
                    (provider_id, alpha, beta, updated_at)
                VALUES (?, ?, ?, now())
                ON CONFLICT (provider_id) DO UPDATE SET
                    alpha = delivery_schema.delivery_partner_bandit_state.alpha + EXCLUDED.alpha - 1,
                    beta = delivery_schema.delivery_partner_bandit_state.beta + EXCLUDED.beta - 1,
                    updated_at = now()
                """, providerId, successful ? 2.0 : 1.0, successful ? 1.0 : 2.0);
        }

        return jdbc.query("""
            SELECT delivery_id, chef_sub_order_id, provider_id, composite_score, breakdown, occurred_at
              FROM delivery_schema.delivery_outcome_receipt WHERE delivery_id = ?
            """, (rs, rowNum) -> new DeliveryScoreResponse(
                rs.getObject("delivery_id", UUID.class),
                rs.getObject("chef_sub_order_id", UUID.class),
                rs.getString("provider_id"),
                rs.getDouble("composite_score"),
                json.readDoubleMap(rs.getString("breakdown")),
                inserted == 1,
                rs.getTimestamp("occurred_at").toInstant()
            ), request.deliveryId()).stream().findFirst().orElseThrow();
    }

    public List<ScoreRow> findScoresOlderThan(Instant cutoff) {
        return jdbc.query("""
            SELECT delivery_id, provider_id, composite_score, breakdown, occurred_at
              FROM delivery_schema.delivery_score_hot
             WHERE occurred_at < ?
             ORDER BY provider_id, occurred_at
            """, (rs, rowNum) -> new ScoreRow(
                rs.getObject("delivery_id", UUID.class),
                rs.getString("provider_id"),
                rs.getDouble("composite_score"),
                json.readDoubleMap(rs.getString("breakdown")),
                rs.getTimestamp("occurred_at").toInstant()
            ), Timestamp.from(cutoff));
    }

    public RollingState loadRollingState(String providerId, double globalPrior) {
        return jdbc.query("""
            SELECT stored_avg, stored_weight, stored_breakdown
              FROM delivery_schema.delivery_partner_rolling_state WHERE provider_id = ?
            """, rs -> {
                if (!rs.next()) return new RollingState(globalPrior, 0.0, seedBreakdown(globalPrior));
                return new RollingState(
                    rs.getDouble("stored_avg"),
                    rs.getDouble("stored_weight"),
                    json.readDoubleMap(rs.getString("stored_breakdown"))
                );
            }, DeliveryProviderRepository.normalize(providerId));
    }

    public void upsertRollingState(String providerId, double average, double weight,
                                   Map<String, Double> breakdown, long countDelta, Instant foldedAt) {
        jdbc.update("""
            INSERT INTO delivery_schema.delivery_partner_rolling_state
                (provider_id, stored_avg, stored_weight, stored_breakdown, lifetime_order_count, last_folded_at)
            VALUES (?, ?, ?, ?::jsonb, ?, ?)
            ON CONFLICT (provider_id) DO UPDATE SET
                stored_avg = EXCLUDED.stored_avg,
                stored_weight = EXCLUDED.stored_weight,
                stored_breakdown = EXCLUDED.stored_breakdown,
                lifetime_order_count = delivery_schema.delivery_partner_rolling_state.lifetime_order_count + EXCLUDED.lifetime_order_count,
                last_folded_at = EXCLUDED.last_folded_at
            """, DeliveryProviderRepository.normalize(providerId), average, weight,
            json.write(breakdown), countDelta, Timestamp.from(foldedAt));
    }

    public void upsertDailyArchive(String providerId, LocalDate date, double dayAverage,
                                   Map<String, Double> breakdown, long count, double storedAverageAfterFold) {
        jdbc.update("""
            INSERT INTO delivery_schema.delivery_partner_daily_archive
                (provider_id, archive_date, day_avg_score, day_breakdown, day_order_count, stored_avg_after_fold)
            VALUES (?, ?, ?, ?::jsonb, ?, ?)
            ON CONFLICT (provider_id, archive_date) DO UPDATE SET
                day_avg_score = EXCLUDED.day_avg_score,
                day_breakdown = EXCLUDED.day_breakdown,
                day_order_count = EXCLUDED.day_order_count,
                stored_avg_after_fold = EXCLUDED.stored_avg_after_fold
            """, DeliveryProviderRepository.normalize(providerId), date, dayAverage,
            json.write(breakdown), count, storedAverageAfterFold);
    }

    public void deleteScoresOlderThan(Instant cutoff) {
        jdbc.update("DELETE FROM delivery_schema.delivery_score_hot WHERE occurred_at < ?", Timestamp.from(cutoff));
    }

    public boolean tryMaintenanceLock() {
        Boolean acquired = jdbc.queryForObject("SELECT pg_try_advisory_xact_lock(724110026)", Boolean.class);
        return Boolean.TRUE.equals(acquired);
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }

    private static void putNullable(Map<String, Double> map, String key, Double value) {
        if (value != null) map.put(key, value);
    }

    private static Map<String, Double> seedBreakdown(double value) {
        Map<String, Double> result = new LinkedHashMap<>();
        result.put("completion", value);
        result.put("pickup_timeliness", value);
        result.put("delivery_timeliness", value);
        result.put("cost_efficiency", value);
        result.put("customer_rating_component", value);
        return result;
    }

    private static double round(double value) {
        return DeliveryOutcomeScorer.round(value);
    }

    private static double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, java.math.RoundingMode.HALF_UP).doubleValue();
    }

    private static Double roundNullable(Double value) {
        return value == null ? null : round(value);
    }

    private static Map<String, Double> roundMap(Map<String, Double> source) {
        if (source == null || source.isEmpty()) return Collections.emptyMap();
        Map<String, Double> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(key, round(value)));
        return result;
    }

    private record LiveAggregate(long count, Double average, Map<String, Double> breakdown) {}
    public record RollingState(double average, double weight, Map<String, Double> breakdown) {}
    public record ScoreRow(UUID deliveryId, String providerId, double compositeScore,
                           Map<String, Double> breakdown, Instant occurredAt) {}
}
