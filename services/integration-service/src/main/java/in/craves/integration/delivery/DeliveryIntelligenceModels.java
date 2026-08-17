package in.craves.integration.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class DeliveryIntelligenceModels {
    private DeliveryIntelligenceModels() {}

    public enum AssignmentStrategy { GREEDY, STOCHASTIC }
    public enum Momentum { IMPROVING, DECLINING, STABLE, INSUFFICIENT_DATA }
    public enum AssignmentStatus { RANKED, ASSIGNED, EXHAUSTED, CANCELLED }
    public enum CandidateStatus { RANKED, SELECTED, ATTEMPTED, ACCEPTED, DECLINED, FAILED, SKIPPED }
    public enum OutcomeStatus { DELIVERED, CANCELLED, FAILED }

    public record ProviderRegistrationRequest(
        @NotBlank String providerId,
        @NotBlank String displayName,
        @NotBlank String adapterType,
        boolean active,
        List<String> serviceAreas,
        Map<String, Boolean> capabilities
    ) {}

    public record ProviderResponse(
        String providerId,
        String displayName,
        String adapterType,
        boolean active,
        List<String> serviceAreas,
        Map<String, Boolean> capabilities,
        Instant createdAt,
        Instant updatedAt
    ) {}

    public record CandidateInput(
        @NotBlank String providerId,
        String providerQuoteId,
        String agentId,
        @DecimalMin("0.0") Double pickupDistanceKm,
        @DecimalMin("0.0") Double pickupEtaMinutes,
        @DecimalMin("0.0") BigDecimal quotedCost,
        String currency,
        boolean available,
        JsonNode providerMetadata
    ) {}

    /**
     * Provider quote snapshot used by the delivery command router. The scoring engine remains
     * provider-neutral and consumes CandidateInput; this snapshot preserves the richer quote data
     * at the router boundary without changing the established intelligence calculations.
     */
    public record ProviderQuoteSnapshot(
        BigDecimal paymentAmount,
        BigDecimal deliveryFeeAmount,
        String currency,
        List<String> warnings,
        JsonNode providerMetadata,
        Instant quotedAt
    ) {}

    /**
     * Compatibility input for the command router. Provider success probability is retained for
     * audit/transport compatibility, while DeliveryIntelligenceService continues to calculate its
     * own prediction from stored/live provider metrics.
     */
    public record AssignmentCandidateInput(
        @NotBlank String providerId,
        String providerQuoteId,
        String agentId,
        @DecimalMin("0.0") Double pickupDistanceKm,
        @DecimalMin("0.0") Double pickupEtaMinutes,
        @DecimalMin("0.0") BigDecimal quotedCost,
        String currency,
        Double predictedSuccessProbability,
        ProviderQuoteSnapshot quoteSnapshot
    ) {
        CandidateInput toCandidateInput() {
            return new CandidateInput(
                providerId,
                providerQuoteId,
                agentId,
                pickupDistanceKm,
                pickupEtaMinutes,
                quotedCost,
                currency,
                true,
                quoteSnapshot == null ? null : quoteSnapshot.providerMetadata()
            );
        }
    }

    public record AssignmentRequest(
        @NotNull UUID chefSubOrderId,
        @NotNull UUID orderId,
        @DecimalMin("0.0") double distanceKm,
        @Min(0) @Max(23) int orderHour,
        @Min(0) @Max(6) int dayOfWeek,
        @NotBlank String area,
        AssignmentStrategy strategy,
        @NotEmpty List<@Valid CandidateInput> candidates
    ) {
        /**
         * Router-oriented constructor kept separate from the canonical REST/service constructor.
         */
        public AssignmentRequest(UUID chefSubOrderId,
                                 UUID orderId,
                                 String area,
                                 double distanceKm,
                                 int orderHour,
                                 int dayOfWeek,
                                 List<AssignmentCandidateInput> candidates) {
            this(
                chefSubOrderId,
                orderId,
                distanceKm,
                orderHour,
                dayOfWeek,
                area,
                null,
                candidates == null
                    ? List.of()
                    : candidates.stream().map(AssignmentCandidateInput::toCandidateInput).toList()
            );
        }
    }

    public record PartnerMetrics(
        String providerId,
        Double liveAverage,
        long liveCount,
        Map<String, Double> liveBreakdown,
        double storedAverage,
        double storedWeight,
        Map<String, Double> storedBreakdown,
        double combinedScore,
        double liveShare,
        Momentum momentum,
        Double momentumDelta,
        double banditAlpha,
        double banditBeta
    ) {}

    public record CandidateScore(
        UUID candidateId,
        int rank,
        String providerId,
        String providerQuoteId,
        String agentId,
        Double pickupDistanceKm,
        Double pickupEtaMinutes,
        BigDecimal quotedCost,
        String currency,
        double predictedSuccessProbability,
        double combinedScore,
        Double liveAverage,
        double storedAverage,
        Momentum momentum,
        double explorationSample,
        double providerQualityScore,
        double proximityScore,
        double finalScore,
        CandidateStatus status,
        JsonNode providerMetadata
    ) {
        public int candidateRank() {
            return rank;
        }
    }

    public record AssignmentResponse(
        UUID assignmentId,
        UUID chefSubOrderId,
        UUID orderId,
        AssignmentStrategy strategy,
        AssignmentStatus status,
        String scoringVersion,
        UUID selectedCandidateId,
        String selectedProviderId,
        String selectedAgentId,
        List<CandidateScore> candidates,
        Instant createdAt
    ) {
        public List<CandidateScore> rankedCandidates() {
            return candidates;
        }
    }

    public record DeliveryOutcomeRequest(
        @NotNull UUID deliveryId,
        @NotNull UUID chefSubOrderId,
        @NotNull UUID orderId,
        @NotBlank String providerId,
        @NotNull OutcomeStatus status,
        @DecimalMin("0.0") double promisedPickupMinutes,
        @DecimalMin("0.0") Double actualPickupMinutes,
        @DecimalMin("0.0") double promisedDeliveryMinutes,
        @DecimalMin("0.0") Double actualDeliveryMinutes,
        @NotNull @DecimalMin("0.0") BigDecimal quotedCost,
        @NotNull @DecimalMin("0.0") BigDecimal actualCost,
        @DecimalMin("1.0") @jakarta.validation.constraints.DecimalMax("5.0") Double customerRating,
        boolean hadComplaint,
        @DecimalMin("0.0") double distanceKm,
        @NotBlank String area,
        @Min(0) @Max(23) int orderHour,
        @Min(0) @Max(6) int dayOfWeek,
        Instant occurredAt
    ) {}

    public record DeliveryScoreResponse(
        UUID deliveryId,
        UUID chefSubOrderId,
        String providerId,
        double compositeScore,
        Map<String, Double> breakdown,
        boolean newlyRecorded,
        Instant occurredAt
    ) {}
}
