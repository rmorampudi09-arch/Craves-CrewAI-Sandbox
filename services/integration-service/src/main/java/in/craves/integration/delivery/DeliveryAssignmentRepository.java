package in.craves.integration.delivery;

import in.craves.integration.delivery.DeliveryIntelligenceModels.AssignmentResponse;
import in.craves.integration.delivery.DeliveryIntelligenceModels.AssignmentStatus;
import in.craves.integration.delivery.DeliveryIntelligenceModels.AssignmentStrategy;
import in.craves.integration.delivery.DeliveryIntelligenceModels.CandidateScore;
import in.craves.integration.delivery.DeliveryIntelligenceModels.CandidateStatus;
import in.craves.integration.delivery.DeliveryIntelligenceModels.Momentum;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DeliveryAssignmentRepository {
    private final JdbcTemplate jdbc;
    private final DeliveryJsonSupport json;

    public DeliveryAssignmentRepository(JdbcTemplate jdbc, DeliveryJsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public Optional<AssignmentResponse> findByChefSubOrderId(UUID chefSubOrderId) {
        return jdbc.query("SELECT * FROM delivery_schema.delivery_assignment WHERE chef_sub_order_id = ?",
            (rs, rowNum) -> mapAssignment(rs), chefSubOrderId).stream().findFirst();
    }

    /**
     * Compatibility alias for the delivery command router. The canonical repository method remains
     * findByChefSubOrderId so existing intelligence callers are unchanged.
     */
    public Optional<AssignmentResponse> findResponseByChefSubOrderId(UUID chefSubOrderId) {
        return findByChefSubOrderId(chefSubOrderId);
    }

    public Optional<AssignmentResponse> find(UUID assignmentId) {
        return jdbc.query("SELECT * FROM delivery_schema.delivery_assignment WHERE id = ?",
            (rs, rowNum) -> mapAssignment(rs), assignmentId).stream().findFirst();
    }

    public void insert(UUID assignmentId, UUID chefSubOrderId, UUID orderId,
                       AssignmentStrategy strategy, AssignmentStatus status,
                       String scoringVersion, UUID selectedCandidateId,
                       String selectedProviderId, String selectedAgentId,
                       String requestContextJson, List<CandidateScore> candidates) {
        jdbc.update("""
            INSERT INTO delivery_schema.delivery_assignment
                (id, chef_sub_order_id, order_id, strategy, status, scoring_version,
                 selected_candidate_id, selected_provider_id, selected_agent_id, request_context,
                 created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now(), now())
            """, assignmentId, chefSubOrderId, orderId, strategy.name(), status.name(), scoringVersion,
            selectedCandidateId, DeliveryProviderRepository.normalize(selectedProviderId), selectedAgentId,
            requestContextJson);

        for (CandidateScore candidate : candidates) {
            jdbc.update("""
                INSERT INTO delivery_schema.delivery_assignment_candidate
                    (id, assignment_id, provider_id, provider_quote_id, agent_id, candidate_rank,
                     pickup_distance_km, pickup_eta_minutes, quoted_cost, currency,
                     predicted_success_probability, combined_score, live_avg, stored_avg, momentum,
                     exploration_sample, provider_quality_score, proximity_score, final_score,
                     status, provider_metadata, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now(), now())
                """, candidate.candidateId(), assignmentId,
                DeliveryProviderRepository.normalize(candidate.providerId()), candidate.providerQuoteId(),
                candidate.agentId(), candidate.rank(), candidate.pickupDistanceKm(), candidate.pickupEtaMinutes(),
                candidate.quotedCost(), candidate.currency(), candidate.predictedSuccessProbability(),
                candidate.combinedScore(), candidate.liveAverage(), candidate.storedAverage(),
                candidate.momentum().name(), candidate.explorationSample(), candidate.providerQualityScore(),
                candidate.proximityScore(), candidate.finalScore(), candidate.status().name(),
                json.writeNode(candidate.providerMetadata()));
        }
    }

    public void markAssigned(UUID assignmentId,
                             UUID acceptedCandidateId,
                             Collection<String> failedProviderIds) {
        int accepted = jdbc.update("""
            UPDATE delivery_schema.delivery_assignment_candidate
            SET status = 'ACCEPTED', updated_at = now()
            WHERE assignment_id = ? AND id = ?
            """, assignmentId, acceptedCandidateId);
        if (accepted != 1) {
            throw new IllegalStateException("Executed delivery candidate does not belong to the assignment");
        }

        jdbc.update("""
            UPDATE delivery_schema.delivery_assignment_candidate
            SET status = 'RANKED', updated_at = now()
            WHERE assignment_id = ?
              AND id <> ?
              AND status = 'SELECTED'
            """, assignmentId, acceptedCandidateId);

        if (failedProviderIds != null) {
            for (String providerId : failedProviderIds) {
                if (providerId == null || providerId.isBlank()) {
                    continue;
                }
                jdbc.update("""
                    UPDATE delivery_schema.delivery_assignment_candidate
                    SET status = 'FAILED', updated_at = now()
                    WHERE assignment_id = ?
                      AND provider_id = ?
                      AND id <> ?
                      AND status <> 'SKIPPED'
                    """,
                    assignmentId,
                    DeliveryProviderRepository.normalize(providerId),
                    acceptedCandidateId
                );
            }
        }

        int updated = jdbc.update("""
            UPDATE delivery_schema.delivery_assignment AS assignment
            SET status = 'ASSIGNED',
                selected_candidate_id = candidate.id,
                selected_provider_id = candidate.provider_id,
                selected_agent_id = candidate.agent_id,
                updated_at = now()
            FROM delivery_schema.delivery_assignment_candidate AS candidate
            WHERE assignment.id = ?
              AND candidate.assignment_id = assignment.id
              AND candidate.id = ?
            """, assignmentId, acceptedCandidateId);
        if (updated != 1) {
            throw new IllegalStateException("Delivery assignment could not be marked assigned");
        }
    }

    private AssignmentResponse mapAssignment(ResultSet rs) throws SQLException {
        UUID assignmentId = rs.getObject("id", UUID.class);
        List<CandidateScore> candidates = jdbc.query("""
            SELECT * FROM delivery_schema.delivery_assignment_candidate
             WHERE assignment_id = ? ORDER BY candidate_rank
            """, (candidateRs, rowNum) -> mapCandidate(candidateRs), assignmentId);
        return new AssignmentResponse(
            assignmentId,
            rs.getObject("chef_sub_order_id", UUID.class),
            rs.getObject("order_id", UUID.class),
            AssignmentStrategy.valueOf(rs.getString("strategy")),
            AssignmentStatus.valueOf(rs.getString("status")),
            rs.getString("scoring_version"),
            rs.getObject("selected_candidate_id", UUID.class),
            rs.getString("selected_provider_id"),
            rs.getString("selected_agent_id"),
            candidates,
            rs.getTimestamp("created_at").toInstant()
        );
    }

    private CandidateScore mapCandidate(ResultSet rs) throws SQLException {
        return new CandidateScore(
            rs.getObject("id", UUID.class),
            rs.getInt("candidate_rank"),
            rs.getString("provider_id"),
            rs.getString("provider_quote_id"),
            rs.getString("agent_id"),
            nullableDouble(rs, "pickup_distance_km"),
            nullableDouble(rs, "pickup_eta_minutes"),
            rs.getBigDecimal("quoted_cost"),
            rs.getString("currency"),
            rs.getDouble("predicted_success_probability"),
            rs.getDouble("combined_score"),
            nullableDouble(rs, "live_avg"),
            rs.getDouble("stored_avg"),
            Momentum.valueOf(rs.getString("momentum")),
            rs.getDouble("exploration_sample"),
            rs.getDouble("provider_quality_score"),
            rs.getDouble("proximity_score"),
            rs.getDouble("final_score"),
            CandidateStatus.valueOf(rs.getString("status")),
            json.readTree(rs.getString("provider_metadata"))
        );
    }

    private static Double nullableDouble(ResultSet rs, String column) throws SQLException {
        double value = rs.getDouble(column);
        return rs.wasNull() ? null : value;
    }
}
