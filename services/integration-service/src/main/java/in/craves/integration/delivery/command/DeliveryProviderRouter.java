package in.craves.integration.delivery.command;

import in.craves.integration.delivery.DeliveryAssignmentRepository;
import in.craves.integration.delivery.DeliveryIntelligenceModels.AssignmentCandidateInput;
import in.craves.integration.delivery.DeliveryIntelligenceModels.AssignmentRequest;
import in.craves.integration.delivery.DeliveryIntelligenceModels.AssignmentResponse;
import in.craves.integration.delivery.DeliveryIntelligenceModels.CandidateScore;
import in.craves.integration.delivery.DeliveryIntelligenceModels.ProviderQuoteSnapshot;
import in.craves.integration.delivery.DeliveryIntelligenceService;
import in.craves.integration.delivery.command.DeliveryCommandModels.CreateAudit;
import in.craves.integration.delivery.command.DeliveryCommandModels.DeliveryCommandMessage;
import in.craves.integration.delivery.command.DeliveryCommandModels.QuoteAudit;
import in.craves.integration.delivery.command.DeliveryCommandModels.RoutingResult;
import in.craves.integration.delivery.command.DeliveryCommandRepository.CommandRecord;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.CreateDeliveryRequest;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.CreateReconciliationResult;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.CreateReconciliationStatus;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.ProviderCreateUncertainException;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.ProviderDelivery;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.ProviderQuote;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

@Component
public class DeliveryProviderRouter {
    private final Map<String, DeliveryProviderAdapter> adapters;
    private final DeliveryProviderCatalogRepository providerCatalog;
    private final DeliveryIntelligenceService intelligenceService;
    private final DeliveryAssignmentRepository assignmentRepository;
    private final DeliveryCommandProperties properties;
    private final ExecutorService quoteExecutor;
    private final Clock clock;

    public DeliveryProviderRouter(List<DeliveryProviderAdapter> adapters,
                                  DeliveryProviderCatalogRepository providerCatalog,
                                  DeliveryIntelligenceService intelligenceService,
                                  DeliveryAssignmentRepository assignmentRepository,
                                  DeliveryCommandProperties properties,
                                  @Qualifier("deliveryQuoteExecutor") ExecutorService quoteExecutor,
                                  Clock clock) {
        Map<String, DeliveryProviderAdapter> indexed = new LinkedHashMap<>();
        for (DeliveryProviderAdapter adapter : adapters) {
            String providerId = normalize(adapter.providerId());
            if (indexed.put(providerId, adapter) != null) {
                throw new IllegalStateException("Duplicate delivery provider adapter: " + providerId);
            }
        }
        this.adapters = Map.copyOf(indexed);
        this.providerCatalog = providerCatalog;
        this.intelligenceService = intelligenceService;
        this.assignmentRepository = assignmentRepository;
        this.properties = properties;
        this.quoteExecutor = quoteExecutor;
        this.clock = clock;
    }

    public RoutingResult route(DeliveryCommandMessage command) {
        Objects.requireNonNull(command, "delivery command is required");
        List<String> activeProviderIds = providerCatalog.activeProviderIds();
        if (activeProviderIds.isEmpty()) {
            throw new DeliveryProviderTemporarilyUnavailableException(
                "No active delivery providers are configured"
            );
        }

        List<QuoteAudit> quoteAudit = new ArrayList<>();
        List<Callable<QuoteOutcome>> tasks = new ArrayList<>();

        for (String configuredProviderId : activeProviderIds) {
            String providerId = normalize(configuredProviderId);
            DeliveryProviderAdapter adapter = adapters.get(providerId);
            if (adapter == null) {
                quoteAudit.add(new QuoteAudit(
                    providerId, false, false, null, null, null,
                    "Provider is active in the database but no adapter is deployed"
                ));
                continue;
            }
            tasks.add(() -> quote(adapter, command));
        }

        List<QuoteOutcome> outcomes = invokeQuotes(tasks, quoteAudit);
        boolean anyAvailableQuote = outcomes.stream()
            .anyMatch(outcome -> outcome.quote() != null && outcome.quote().available());
        if (!anyAvailableQuote) {
            boolean anyProviderResponse = outcomes.stream().anyMatch(outcome -> outcome.quote() != null);
            if (!anyProviderResponse) {
                throw new DeliveryProviderTemporarilyUnavailableException(
                    "Active delivery providers did not return a quote"
                );
            }
            throw new DeliveryRoutingException(
                "No active delivery provider returned an available quote"
            );
        }

        AssignmentResponse assignment = intelligenceService.assign(
            assignmentRequest(command, outcomes)
        );
        List<RankedQuoteOutcome> candidates = orderByIntelligence(assignment, outcomes);
        if (candidates.isEmpty()) {
            throw new DeliveryRoutingException(
                "The persisted intelligent assignment has no currently available provider quote"
            );
        }

        List<CreateAudit> createAudit = new ArrayList<>();
        int maximumAttempts = Math.min(properties.getMaxProviderAttempts(), candidates.size());
        String clientReference = clientReference(command.chefSubOrderId());

        for (int index = 0; index < maximumAttempts; index++) {
            RankedQuoteOutcome ranked = candidates.get(index);
            QuoteOutcome candidate = ranked.outcome();
            try {
                ProviderDelivery delivery = candidate.adapter().create(
                    new CreateDeliveryRequest(
                        clientReference,
                        command.deliveryRequest(),
                        candidate.quote()
                    )
                );
                if (delivery == null || delivery.providerDeliveryId() == null
                    || delivery.providerDeliveryId().isBlank()) {
                    throw new IllegalStateException("Provider returned no delivery identifier");
                }
                createAudit.add(new CreateAudit(candidate.providerId(), true, null));
                return new RoutingResult(
                    candidate.providerId(),
                    delivery,
                    assignment,
                    ranked.candidate().candidateId(),
                    List.copyOf(quoteAudit),
                    List.copyOf(createAudit)
                );
            } catch (ProviderCreateUncertainException ex) {
                createAudit.add(new CreateAudit(
                    candidate.providerId(), false, "Provider create outcome requires reconciliation"
                ));
                throw new DeliveryCreateReconciliationPendingException(
                    ex.providerId(),
                    ex.clientReference(),
                    ex.attemptedAt(),
                    "Provider create response was not received; fallback is blocked",
                    ex
                );
            } catch (RuntimeException ex) {
                createAudit.add(new CreateAudit(candidate.providerId(), false, safeMessage(ex)));
            }
        }

        throw new DeliveryRoutingException(
            "Delivery creation failed across " + maximumAttempts + " intelligently ranked provider attempt(s)"
        );
    }

    /**
     * Performs a read-only reconciliation for an uncertain provider create. This method never calls
     * the provider create operation and never falls back to another provider.
     */
    public RoutingResult reconcile(CommandRecord command) {
        Objects.requireNonNull(command, "delivery command is required");
        String providerId = normalize(command.reconciliationProviderId());
        String clientReference = requireText(
            command.reconciliationClientReference(), "reconciliation client reference"
        );
        Instant attemptedAt = Objects.requireNonNull(
            command.reconciliationStartedAt(), "reconciliation startedAt is required"
        );

        DeliveryProviderAdapter adapter = adapters.get(providerId);
        if (adapter == null) {
            throw new DeliveryCreateReconciliationPendingException(
                providerId,
                clientReference,
                attemptedAt,
                "The reconciliation provider adapter is not deployed"
            );
        }

        CreateReconciliationResult reconciliation;
        try {
            reconciliation = adapter.reconcileCreate(clientReference, attemptedAt);
        } catch (RuntimeException ex) {
            throw new DeliveryCreateReconciliationPendingException(
                providerId,
                clientReference,
                attemptedAt,
                "Provider create reconciliation call failed",
                ex
            );
        }
        if (reconciliation == null) {
            throw new DeliveryCreateReconciliationPendingException(
                providerId,
                clientReference,
                attemptedAt,
                "Provider create reconciliation returned no result"
            );
        }

        if (reconciliation.status() == CreateReconciliationStatus.FOUND) {
            ProviderDelivery delivery = Objects.requireNonNull(
                reconciliation.delivery(), "reconciled provider delivery is required"
            );
            AssignmentResponse assignment = existingAssignment(command.chefSubOrderId());
            CandidateScore selected = selectedCandidate(assignment, providerId);
            return new RoutingResult(
                providerId,
                delivery,
                assignment,
                selected.candidateId(),
                List.of(),
                List.of(new CreateAudit(providerId, true, "Recovered by provider reconciliation"))
            );
        }

        if (reconciliation.status() == CreateReconciliationStatus.NOT_FOUND) {
            throw new DeliveryCreateDefinitivelyNotFoundException(
                providerId,
                clientReference,
                attemptedAt,
                safeDetail(reconciliation.detail(), "Provider confirmed no matching delivery")
            );
        }

        throw new DeliveryCreateReconciliationPendingException(
            providerId,
            clientReference,
            attemptedAt,
            safeDetail(reconciliation.detail(), "Provider create reconciliation is inconclusive")
        );
    }

    private AssignmentResponse existingAssignment(java.util.UUID chefSubOrderId) {
        return assignmentRepository.findResponseByChefSubOrderId(chefSubOrderId)
            .orElseThrow(() -> new DeliveryRoutingException(
                "The delivery assignment is missing during provider create reconciliation"
            ));
    }

    private static CandidateScore selectedCandidate(AssignmentResponse assignment, String providerId) {
        if (assignment.selectedCandidateId() != null) {
            for (CandidateScore candidate : assignment.rankedCandidates()) {
                if (assignment.selectedCandidateId().equals(candidate.candidateId())) {
                    return candidate;
                }
            }
        }
        return assignment.rankedCandidates().stream()
            .filter(candidate -> providerId.equals(normalize(candidate.providerId())))
            .findFirst()
            .orElseThrow(() -> new DeliveryRoutingException(
                "The persisted assignment has no candidate for reconciliation provider " + providerId
            ));
    }

    private QuoteOutcome quote(DeliveryProviderAdapter adapter, DeliveryCommandMessage command) {
        String providerId = normalize(adapter.providerId());
        try {
            ProviderQuote quote = adapter.quote(command.deliveryRequest());
            if (quote == null) {
                return new QuoteOutcome(providerId, adapter, null, "Provider returned no quote");
            }
            return new QuoteOutcome(providerId, adapter, quote, null);
        } catch (RuntimeException ex) {
            return new QuoteOutcome(providerId, adapter, null, safeMessage(ex));
        }
    }

    private List<QuoteOutcome> invokeQuotes(List<Callable<QuoteOutcome>> tasks,
                                            List<QuoteAudit> quoteAudit) {
        if (tasks.isEmpty()) {
            return List.of();
        }
        List<Future<QuoteOutcome>> futures = new ArrayList<>(tasks.size());
        for (Callable<QuoteOutcome> task : tasks) {
            futures.add(quoteExecutor.submit(task));
        }

        List<QuoteOutcome> outcomes = new ArrayList<>(tasks.size());
        Duration timeout = properties.quoteTimeout();
        long deadlineNanos = System.nanoTime() + timeout.toNanos();
        for (Future<QuoteOutcome> future : futures) {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                future.cancel(true);
                quoteAudit.add(new QuoteAudit(
                    "unknown", false, false, null, null, null,
                    "Quote fan-out timed out"
                ));
                continue;
            }
            try {
                QuoteOutcome outcome = future.get(remainingNanos, TimeUnit.NANOSECONDS);
                outcomes.add(outcome);
                ProviderQuote quote = outcome.quote();
                Double pickupEta = quote == null ? null : pickupEtaMinutes(quote);
                quoteAudit.add(new QuoteAudit(
                    outcome.providerId(),
                    quote != null,
                    quote != null && quote.available(),
                    quote == null ? null : pickupDistanceKm(quote),
                    pickupEta == null ? null : (int) Math.round(pickupEta),
                    quote,
                    outcome.error()
                ));
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new DeliveryRoutingException("Delivery quote fan-out was interrupted", ex);
            } catch (ExecutionException | TimeoutException ex) {
                future.cancel(true);
                quoteAudit.add(new QuoteAudit(
                    "unknown", false, false, null, null, null,
                    safeMessage(ex)
                ));
            }
        }
        return List.copyOf(outcomes);
    }

    private AssignmentRequest assignmentRequest(DeliveryCommandMessage command,
                                                List<QuoteOutcome> outcomes) {
        List<AssignmentCandidateInput> candidates = new ArrayList<>();
        for (QuoteOutcome outcome : outcomes) {
            ProviderQuote quote = outcome.quote();
            if (quote == null || !quote.available()) {
                continue;
            }
            candidates.add(new AssignmentCandidateInput(
                outcome.providerId(),
                providerQuoteId(quote),
                agentId(quote),
                pickupDistanceKm(quote),
                pickupEtaMinutes(quote),
                quote.deliveryFeeAmount(),
                quote.currency(),
                providerSuccessProbability(quote),
                new ProviderQuoteSnapshot(
                    quote.paymentAmount(),
                    quote.deliveryFeeAmount(),
                    quote.currency(),
                    quote.warnings(),
                    quote.providerMetadata(),
                    quote.quotedAt()
                )
            ));
        }
        return new AssignmentRequest(
            command.chefSubOrderId(),
            command.orderId(),
            command.area(),
            command.distanceKm(),
            command.orderHour(),
            command.dayOfWeek(),
            candidates
        );
    }

    private List<RankedQuoteOutcome> orderByIntelligence(AssignmentResponse assignment,
                                                         List<QuoteOutcome> outcomes) {
        Map<String, QuoteOutcome> byProvider = new HashMap<>();
        for (QuoteOutcome outcome : outcomes) {
            if (outcome.quote() != null && outcome.quote().available()) {
                byProvider.put(outcome.providerId(), outcome);
            }
        }

        List<RankedQuoteOutcome> result = new ArrayList<>();
        for (CandidateScore candidate : assignment.rankedCandidates()) {
            QuoteOutcome outcome = byProvider.get(normalize(candidate.providerId()));
            if (outcome != null) {
                result.add(new RankedQuoteOutcome(candidate, outcome));
            }
        }
        result.sort(Comparator.comparingInt(ranked -> ranked.candidate().candidateRank()));
        return List.copyOf(result);
    }

    private static String clientReference(java.util.UUID chefSubOrderId) {
        return chefSubOrderId.toString();
    }

    private static String providerQuoteId(ProviderQuote quote) {
        return textMetadata(quote, "quote_id");
    }

    private static String agentId(ProviderQuote quote) {
        return textMetadata(quote, "agent_id");
    }

    private static Double pickupDistanceKm(ProviderQuote quote) {
        return doubleMetadata(quote, "pickup_distance_km");
    }

    private static Double pickupEtaMinutes(ProviderQuote quote) {
        return doubleMetadata(quote, "pickup_eta_minutes");
    }

    private static Double providerSuccessProbability(ProviderQuote quote) {
        Double value = doubleMetadata(quote, "predicted_success_probability");
        if (value == null) {
            return 1.0d;
        }
        return Math.max(0.0d, Math.min(1.0d, value));
    }

    private static String textMetadata(ProviderQuote quote, String field) {
        if (quote.providerMetadata() == null || quote.providerMetadata().get(field) == null
            || quote.providerMetadata().get(field).isNull()) {
            return null;
        }
        String value = quote.providerMetadata().get(field).asText();
        return value == null || value.isBlank() ? null : value;
    }

    private static Double doubleMetadata(ProviderQuote quote, String field) {
        if (quote.providerMetadata() == null || quote.providerMetadata().get(field) == null
            || quote.providerMetadata().get(field).isNull()) {
            return null;
        }
        if (quote.providerMetadata().get(field).isNumber()) {
            return quote.providerMetadata().get(field).asDouble();
        }
        try {
            return Double.valueOf(quote.providerMetadata().get(field).asText());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private static String safeDetail(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }

    private static String requireText(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new DeliveryRoutingException(label + " is required");
        }
        return value.trim();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private record QuoteOutcome(
        String providerId,
        DeliveryProviderAdapter adapter,
        ProviderQuote quote,
        String error
    ) {}

    private record RankedQuoteOutcome(CandidateScore candidate, QuoteOutcome outcome) {}

    public static class DeliveryRoutingException extends RuntimeException {
        public DeliveryRoutingException(String message) {
            super(message);
        }

        public DeliveryRoutingException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public static class DeliveryProviderTemporarilyUnavailableException extends DeliveryRoutingException {
        public DeliveryProviderTemporarilyUnavailableException(String message) {
            super(message);
        }
    }

    public static class DeliveryCreateReconciliationPendingException extends RuntimeException {
        private final String providerId;
        private final String clientReference;
        private final Instant attemptedAt;

        public DeliveryCreateReconciliationPendingException(String providerId,
                                                             String clientReference,
                                                             Instant attemptedAt,
                                                             String message) {
            super(message);
            this.providerId = providerId;
            this.clientReference = clientReference;
            this.attemptedAt = attemptedAt;
        }

        public DeliveryCreateReconciliationPendingException(String providerId,
                                                             String clientReference,
                                                             Instant attemptedAt,
                                                             String message,
                                                             Throwable cause) {
            super(message, cause);
            this.providerId = providerId;
            this.clientReference = clientReference;
            this.attemptedAt = attemptedAt;
        }

        public String providerId() {
            return providerId;
        }

        public String clientReference() {
            return clientReference;
        }

        public Instant attemptedAt() {
            return attemptedAt;
        }
    }

    public static class DeliveryCreateDefinitivelyNotFoundException extends RuntimeException {
        private final String providerId;
        private final String clientReference;
        private final Instant attemptedAt;

        public DeliveryCreateDefinitivelyNotFoundException(String providerId,
                                                            String clientReference,
                                                            Instant attemptedAt,
                                                            String message) {
            super(message);
            this.providerId = providerId;
            this.clientReference = clientReference;
            this.attemptedAt = attemptedAt;
        }

        public String providerId() {
            return providerId;
        }

        public String clientReference() {
            return clientReference;
        }

        public Instant attemptedAt() {
            return attemptedAt;
        }
    }
}
