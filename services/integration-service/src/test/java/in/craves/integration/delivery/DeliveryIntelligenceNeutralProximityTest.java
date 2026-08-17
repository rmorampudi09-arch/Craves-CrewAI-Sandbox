package in.craves.integration.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.integration.config.DeliveryIntelligenceProperties;
import in.craves.integration.delivery.DeliveryIntelligenceModels.AssignmentRequest;
import in.craves.integration.delivery.DeliveryIntelligenceModels.AssignmentStrategy;
import in.craves.integration.delivery.DeliveryIntelligenceModels.CandidateInput;
import in.craves.integration.delivery.DeliveryIntelligenceModels.Momentum;
import in.craves.integration.delivery.DeliveryIntelligenceModels.PartnerMetrics;
import in.craves.integration.delivery.DeliveryIntelligenceModels.ProviderResponse;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeliveryIntelligenceNeutralProximityTest {

    @Test
    void usesNeutralProximityWhenProviderDoesNotExposePreBookingRiderEta() {
        DeliveryProviderRepository providers = mock(DeliveryProviderRepository.class);
        DeliveryAssignmentRepository assignments = mock(DeliveryAssignmentRepository.class);
        DeliveryMetricsRepository metrics = mock(DeliveryMetricsRepository.class);
        DeliveryOutcomeScorer outcomeScorer = mock(DeliveryOutcomeScorer.class);
        DeliverySuccessPredictor predictor = mock(DeliverySuccessPredictor.class);
        BetaSampler betaSampler = mock(BetaSampler.class);
        DeliveryIntelligenceProperties properties = new DeliveryIntelligenceProperties();
        properties.setDefaultStrategy("GREEDY");

        Instant now = Instant.parse("2026-07-14T12:00:00Z");
        Clock clock = Clock.fixed(now, ZoneOffset.UTC);
        DeliveryIntelligenceService service = new DeliveryIntelligenceService(
            providers,
            assignments,
            metrics,
            outcomeScorer,
            predictor,
            betaSampler,
            properties,
            new ObjectMapper(),
            clock
        );

        when(assignments.findByChefSubOrderId(any())).thenReturn(Optional.empty());
        when(providers.find("borzo")).thenReturn(Optional.of(new ProviderResponse(
            "borzo",
            "Borzo",
            "BORZO_BUSINESS_API",
            true,
            List.of("Madhapur"),
            Map.of("QUOTE", true, "CREATE_DELIVERY", true),
            now,
            now
        )));
        when(metrics.load(eq("borzo"), eq(now), eq(properties))).thenReturn(new PartnerMetrics(
            "borzo",
            null,
            0,
            Map.of(),
            100.0,
            0.0,
            Map.of(),
            100.0,
            0.0,
            Momentum.INSUFFICIENT_DATA,
            null,
            1.0,
            1.0
        ));
        when(predictor.predict(anyDouble(), anyInt(), anyInt(), any())).thenReturn(0.90);
        when(predictor.version()).thenReturn("HEURISTIC_TEST");
        when(betaSampler.sample(anyDouble(), anyDouble(), any())).thenReturn(0.50);

        UUID orderId = UUID.randomUUID();
        UUID chefSubOrderId = UUID.randomUUID();
        AssignmentRequest request = new AssignmentRequest(
            chefSubOrderId,
            orderId,
            4.6,
            19,
            1,
            "Madhapur",
            AssignmentStrategy.GREEDY,
            List.of(new CandidateInput(
                "borzo",
                null,
                null,
                null,
                null,
                new BigDecimal("125.50"),
                "INR",
                true,
                new ObjectMapper().createObjectNode().put("delivery_fee_amount", "125.50")
            ))
        );

        var response = service.assign(request);

        assertThat(response.selectedProviderId()).isEqualTo("borzo");
        assertThat(response.scoringVersion()).endsWith("PROXIMITY_QUALITY_V2");
        assertThat(response.candidates()).hasSize(1);
        assertThat(response.candidates().getFirst().pickupEtaMinutes()).isNull();
        assertThat(response.candidates().getFirst().pickupDistanceKm()).isNull();
        assertThat(response.candidates().getFirst().proximityScore()).isEqualTo(50.0);
    }
}
