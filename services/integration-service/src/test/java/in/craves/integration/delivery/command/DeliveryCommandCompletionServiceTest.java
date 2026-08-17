package in.craves.integration.delivery.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.integration.delivery.DeliveryAssignmentRepository;
import in.craves.integration.delivery.DeliveryIntelligenceModels.AssignmentResponse;
import in.craves.integration.delivery.DeliveryIntelligenceModels.AssignmentStatus;
import in.craves.integration.delivery.DeliveryIntelligenceModels.AssignmentStrategy;
import in.craves.integration.delivery.DeliveryIntelligenceModels.CandidateScore;
import in.craves.integration.delivery.DeliveryIntelligenceModels.CandidateStatus;
import in.craves.integration.delivery.DeliveryIntelligenceModels.Momentum;
import in.craves.integration.delivery.command.DeliveryCommandModels.CreateAudit;
import in.craves.integration.delivery.command.DeliveryCommandModels.DeliveryCommandMessage;
import in.craves.integration.delivery.command.DeliveryCommandModels.RoutingResult;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.DeliveryStatus;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.ProviderDelivery;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.QuoteRequest;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.Stop;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeliveryCommandCompletionServiceTest {

    @Test
    void commitsActualFallbackCandidateWithJobAndOutbox() {
        DeliveryAssignmentRepository assignments = mock(DeliveryAssignmentRepository.class);
        DeliveryJobRepository deliveryJobs = mock(DeliveryJobRepository.class);
        DeliveryOutboxRepository outbox = mock(DeliveryOutboxRepository.class);
        DeliveryCommandRepository commands = mock(DeliveryCommandRepository.class);
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        DeliveryCommandCompletionService service = new DeliveryCommandCompletionService(
            assignments, deliveryJobs, outbox, commands, objectMapper
        );

        DeliveryCommandMessage command = command();
        UUID assignmentId = UUID.randomUUID();
        UUID fastCandidateId = UUID.randomUUID();
        UUID backupCandidateId = UUID.randomUUID();
        AssignmentResponse assignment = assignment(
            command, assignmentId, fastCandidateId, backupCandidateId
        );
        ProviderDelivery delivery = new ProviderDelivery(
            "backup",
            "backup-delivery-1",
            "backup-order",
            DeliveryStatus.SEARCHING,
            "planned",
            new BigDecimal("90.00"),
            new BigDecimal("90.00"),
            "https://tracking.example/backup",
            objectMapper.createObjectNode(),
            Instant.now()
        );
        RoutingResult routing = new RoutingResult(
            "backup",
            delivery,
            assignment,
            backupCandidateId,
            List.of(),
            List.of(
                new CreateAudit("fast", false, "create failed"),
                new CreateAudit("backup", true, null)
            )
        );

        UUID deliveryJobId = UUID.randomUUID();
        when(deliveryJobs.findIdByChefSubOrderId(command.chefSubOrderId()))
            .thenReturn(Optional.empty());
        when(deliveryJobs.insert(command.orderId(), command.chefSubOrderId(), routing))
            .thenReturn(deliveryJobId);
        when(outbox.enqueue(any(), any(), any(), any())).thenReturn(UUID.randomUUID());

        var receipt = service.complete(command, routing);

        assertThat(receipt.deliveryJobId()).isEqualTo(deliveryJobId);
        assertThat(receipt.duplicate()).isFalse();
        verify(assignments).markAssigned(
            eq(assignmentId), eq(backupCandidateId), eq(List.of("fast"))
        );
        verify(commands).markCompleted(command.commandId());
        verify(outbox).enqueue(
            eq(DeliveryCommandModels.DELIVERY_STATUS_CHANGED),
            eq(deliveryJobId),
            eq(command.orderId()),
            any()
        );
    }

    private static AssignmentResponse assignment(DeliveryCommandMessage command,
                                                   UUID assignmentId,
                                                   UUID fastCandidateId,
                                                   UUID backupCandidateId) {
        ObjectMapper objectMapper = new ObjectMapper();
        CandidateScore fast = candidate(
            fastCandidateId, 1, "fast", CandidateStatus.SELECTED, objectMapper
        );
        CandidateScore backup = candidate(
            backupCandidateId, 2, "backup", CandidateStatus.RANKED, objectMapper
        );
        return new AssignmentResponse(
            assignmentId,
            command.chefSubOrderId(),
            command.orderId(),
            AssignmentStrategy.STOCHASTIC,
            AssignmentStatus.RANKED,
            "HEURISTIC_TEST|ROLLING_V1|BANDIT_V1|PROXIMITY_QUALITY_V2",
            fastCandidateId,
            "fast",
            null,
            List.of(fast, backup),
            Instant.now()
        );
    }

    private static CandidateScore candidate(UUID id,
                                              int rank,
                                              String providerId,
                                              CandidateStatus status,
                                              ObjectMapper objectMapper) {
        return new CandidateScore(
            id, rank, providerId, providerId + "-quote", null,
            null, 5.0 + rank, new BigDecimal("100.00"), "INR",
            0.90, 90.0, 90.0, 90.0, Momentum.STABLE,
            0.5, 90.0, 80.0, 86.0, status,
            objectMapper.createObjectNode()
        );
    }

    private static DeliveryCommandMessage command() {
        UUID orderId = UUID.randomUUID();
        UUID chefSubOrderId = UUID.randomUUID();
        QuoteRequest request = new QuoteRequest(
            "Packaged food",
            2000,
            true,
            new Stop(
                "Madhapur, Hyderabad", "Chef", "919999999991",
                new BigDecimal("17.4483"), new BigDecimal("78.3915"),
                null, null, "Pickup"
            ),
            new Stop(
                "Gachibowli, Hyderabad", "Customer", "919999999992",
                new BigDecimal("17.4401"), new BigDecimal("78.3489"),
                null, null, "Dropoff"
            )
        );
        return new DeliveryCommandMessage(
            UUID.randomUUID(),
            UUID.randomUUID(),
            orderId,
            orderId,
            chefSubOrderId,
            Instant.now().plusSeconds(1800),
            Instant.now(),
            chefSubOrderId.toString(),
            4.6,
            "Madhapur",
            19,
            1,
            request
        );
    }
}
