package in.craves.integration.delivery;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.integration.config.DeliveryIntelligenceProperties;
import in.craves.integration.delivery.DeliveryIntelligenceModels.AssignmentRequest;
import in.craves.integration.delivery.DeliveryIntelligenceModels.AssignmentResponse;
import in.craves.integration.delivery.DeliveryIntelligenceModels.AssignmentStatus;
import in.craves.integration.delivery.DeliveryIntelligenceModels.AssignmentStrategy;
import in.craves.integration.delivery.DeliveryIntelligenceModels.CandidateInput;
import in.craves.integration.delivery.DeliveryIntelligenceModels.CandidateScore;
import in.craves.integration.delivery.DeliveryIntelligenceModels.CandidateStatus;
import in.craves.integration.delivery.DeliveryIntelligenceModels.DeliveryOutcomeRequest;
import in.craves.integration.delivery.DeliveryIntelligenceModels.DeliveryScoreResponse;
import in.craves.integration.delivery.DeliveryIntelligenceModels.PartnerMetrics;
import in.craves.integration.delivery.DeliveryIntelligenceModels.ProviderRegistrationRequest;
import in.craves.integration.delivery.DeliveryIntelligenceModels.ProviderResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DeliveryIntelligenceService {
    private final DeliveryProviderRepository providerRepository;
    private final DeliveryAssignmentRepository assignmentRepository;
    private final DeliveryMetricsRepository metricsRepository;
    private final DeliveryOutcomeScorer outcomeScorer;
    private final DeliverySuccessPredictor predictor;
    private final BetaSampler betaSampler;
    private final DeliveryIntelligenceProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public DeliveryIntelligenceService(DeliveryProviderRepository providerRepository,
                                       DeliveryAssignmentRepository assignmentRepository,
                                       DeliveryMetricsRepository metricsRepository,
                                       DeliveryOutcomeScorer outcomeScorer,
                                       DeliverySuccessPredictor predictor,
                                       BetaSampler betaSampler,
                                       DeliveryIntelligenceProperties properties,
                                       ObjectMapper objectMapper) {
        this(providerRepository, assignmentRepository, metricsRepository, outcomeScorer, predictor, betaSampler,
            properties, objectMapper, Clock.systemUTC());
    }

    DeliveryIntelligenceService(DeliveryProviderRepository providerRepository,
                                DeliveryAssignmentRepository assignmentRepository,
                                DeliveryMetricsRepository metricsRepository,
                                DeliveryOutcomeScorer outcomeScorer,
                                DeliverySuccessPredictor predictor,
                                BetaSampler betaSampler,
                                DeliveryIntelligenceProperties properties,
                                ObjectMapper objectMapper,
                                Clock clock) {
        this.providerRepository = providerRepository;
        this.assignmentRepository = assignmentRepository;
        this.metricsRepository = metricsRepository;
        this.outcomeScorer = outcomeScorer;
        this.predictor = predictor;
        this.betaSampler = betaSampler;
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Transactional
    public ProviderResponse registerProvider(ProviderRegistrationRequest request) {
        return providerRepository.upsert(request);
    }

    public AssignmentResponse getAssignment(UUID assignmentId) {
        return assignmentRepository.find(assignmentId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Assignment was not found"));
    }

    @Transactional
    public AssignmentResponse assign(AssignmentRequest request) {
        ensureEnabled();
        return assignmentRepository.findByChefSubOrderId(request.chefSubOrderId())
            .orElseGet(() -> createAssignment(request));
    }

    @Transactional
    public DeliveryScoreResponse recordOutcome(DeliveryOutcomeRequest request) {
        ensureEnabled();
        providerRepository.find(request.providerId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown delivery provider"));
        Instant occurredAt = request.occurredAt() == null ? clock.instant() : request.occurredAt();
        DeliveryOutcomeScorer.ScoredOutcome score = outcomeScorer.score(request);
        return metricsRepository.insertOutcomeIfAbsent(request, score, occurredAt,
            score.compositeScore() >= properties.getSuccessThreshold());
    }

    private AssignmentResponse createAssignment(AssignmentRequest request) {
        AssignmentStrategy strategy = request.strategy() == null
            ? AssignmentStrategy.valueOf(properties.getDefaultStrategy().trim().toUpperCase(java.util.Locale.ROOT))
            : request.strategy();
        Instant now = clock.instant();
        RandomGenerator random = new SplittableRandom(seed(request.chefSubOrderId(), request.orderId()));
        List<UnrankedCandidate> eligible = new ArrayList<>();
        List<CandidateScore> skipped = new ArrayList<>();

        for (CandidateInput input : request.candidates()) {
            ProviderResponse provider = providerRepository.find(input.providerId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown delivery provider: " + input.providerId()));
            boolean servesArea = provider.serviceAreas() == null || provider.serviceAreas().isEmpty()
                || provider.serviceAreas().stream().anyMatch(area -> area.equalsIgnoreCase(request.area()));
            if (!provider.active() || !servesArea || !input.available()) {
                skipped.add(skipped(input));
                continue;
            }
            PartnerMetrics metrics = metricsRepository.load(input.providerId(), now, properties);
            double successProbability = predictor.predict(
                request.distanceKm(), request.orderHour(), request.dayOfWeek(), metrics);
            double exploration = betaSampler.sample(metrics.banditAlpha(), metrics.banditBeta(), random);
            double quality = properties.getMlWeight() * successProbability * 100.0
                + properties.getRollingScoreWeight() * metrics.combinedScore()
                + properties.getExplorationWeight() * exploration * 100.0;
            double proximity = proximityScore(input);
            double finalScore = properties.getProximityWeight() * proximity
                + properties.getQualityWeight() * quality;
            eligible.add(new UnrankedCandidate(
                input, metrics, successProbability, exploration, quality, proximity, finalScore));
        }

        if (eligible.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "No active and available delivery candidates serve this order");
        }
        eligible.sort(Comparator.comparingDouble(UnrankedCandidate::finalScore).reversed()
            .thenComparing(c -> c.input().providerId())
            .thenComparing(c -> c.input().agentId() == null ? "" : c.input().agentId()));

        UnrankedCandidate selected = strategy == AssignmentStrategy.GREEDY
            ? eligible.getFirst() : stochasticSelect(eligible, random);
        List<CandidateScore> scored = new ArrayList<>();
        int rank = 1;
        UUID selectedCandidateId = null;
        for (UnrankedCandidate candidate : eligible) {
            UUID candidateId = UUID.randomUUID();
            boolean chosen = candidate == selected;
            if (chosen) selectedCandidateId = candidateId;
            scored.add(toScore(candidateId, rank++, candidate,
                chosen ? CandidateStatus.SELECTED : CandidateStatus.RANKED));
        }
        int skippedRank = rank;
        for (CandidateScore candidate : skipped) {
            scored.add(new CandidateScore(
                candidate.candidateId(), skippedRank++, candidate.providerId(), candidate.providerQuoteId(),
                candidate.agentId(), candidate.pickupDistanceKm(), candidate.pickupEtaMinutes(),
                candidate.quotedCost(), candidate.currency(), candidate.predictedSuccessProbability(),
                candidate.combinedScore(), candidate.liveAverage(), candidate.storedAverage(),
                candidate.momentum(), candidate.explorationSample(), candidate.providerQualityScore(),
                candidate.proximityScore(), candidate.finalScore(), candidate.status(),
                candidate.providerMetadata()));
        }

        UUID assignmentId = UUID.randomUUID();
        String version = predictor.version() + "|ROLLING_V1|BANDIT_V1|PROXIMITY_QUALITY_V2";
        try {
            assignmentRepository.insert(
                assignmentId, request.chefSubOrderId(), request.orderId(), strategy,
                AssignmentStatus.RANKED, version, selectedCandidateId, selected.input().providerId(),
                selected.input().agentId(), json(request), scored);
        } catch (DuplicateKeyException race) {
            return assignmentRepository.findByChefSubOrderId(request.chefSubOrderId())
                .orElseThrow(() -> race);
        }
        return new AssignmentResponse(
            assignmentId, request.chefSubOrderId(), request.orderId(), strategy,
            AssignmentStatus.RANKED, version, selectedCandidateId,
            normalize(selected.input().providerId()), selected.input().agentId(), scored, now);
    }

    private UnrankedCandidate stochasticSelect(List<UnrankedCandidate> candidates, RandomGenerator random) {
        double max = candidates.stream().mapToDouble(UnrankedCandidate::finalScore).max().orElseThrow();
        double[] weights = new double[candidates.size()];
        double total = 0.0;
        for (int i = 0; i < candidates.size(); i++) {
            weights[i] = Math.exp((candidates.get(i).finalScore() - max)
                / properties.getSoftmaxTemperature());
            total += weights[i];
        }
        double draw = random.nextDouble() * total;
        double cumulative = 0.0;
        for (int i = 0; i < candidates.size(); i++) {
            cumulative += weights[i];
            if (draw <= cumulative) return candidates.get(i);
        }
        return candidates.getLast();
    }

    private double proximityScore(CandidateInput input) {
        if (input.pickupDistanceKm() != null) {
            return clamp(100.0 * (1.0 - input.pickupDistanceKm() / properties.getSearchRadiusKm()));
        }
        if (input.pickupEtaMinutes() != null) {
            return clamp(100.0 * (1.0 - input.pickupEtaMinutes() / properties.getMaxPickupEtaMinutes()));
        }
        return properties.getUnknownProximityScore();
    }

    private CandidateScore toScore(UUID id, int rank, UnrankedCandidate candidate, CandidateStatus status) {
        return new CandidateScore(
            id, rank, normalize(candidate.input().providerId()), candidate.input().providerQuoteId(),
            candidate.input().agentId(), candidate.input().pickupDistanceKm(),
            candidate.input().pickupEtaMinutes(), candidate.input().quotedCost(), candidate.input().currency(),
            round(candidate.successProbability(), 4), round(candidate.metrics().combinedScore()),
            candidate.metrics().liveAverage(), round(candidate.metrics().storedAverage()),
            candidate.metrics().momentum(), round(candidate.exploration(), 4), round(candidate.quality()),
            round(candidate.proximity()), round(candidate.finalScore()), status,
            candidate.input().providerMetadata());
    }

    private CandidateScore skipped(CandidateInput input) {
        return new CandidateScore(
            UUID.randomUUID(), 0, normalize(input.providerId()), input.providerQuoteId(), input.agentId(),
            input.pickupDistanceKm(), input.pickupEtaMinutes(), input.quotedCost(), input.currency(),
            0.0, 0.0, null, properties.getGlobalPrior(),
            DeliveryIntelligenceModels.Momentum.INSUFFICIENT_DATA,
            0.0, 0.0, 0.0, 0.0, CandidateStatus.SKIPPED, input.providerMetadata());
    }

    private void ensureEnabled() {
        if (!properties.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Delivery intelligence is disabled");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not serialize assignment request", ex);
        }
    }

    private static long seed(UUID first, UUID second) {
        return first.getMostSignificantBits() ^ second.getLeastSignificantBits();
    }

    private static String normalize(String value) {
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(100.0, value));
    }

    private static double round(double value) {
        return round(value, 2);
    }

    private static double round(double value, int scale) {
        return BigDecimal.valueOf(value).setScale(scale, RoundingMode.HALF_UP).doubleValue();
    }

    private record UnrankedCandidate(CandidateInput input, PartnerMetrics metrics,
                                     double successProbability, double exploration,
                                     double quality, double proximity, double finalScore) {}
}
