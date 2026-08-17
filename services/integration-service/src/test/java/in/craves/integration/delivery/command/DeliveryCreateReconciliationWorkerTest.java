package in.craves.integration.delivery.command;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.craves.integration.delivery.command.DeliveryCommandCompletionService.CompletionReceipt;
import in.craves.integration.delivery.command.DeliveryCommandModels.DeliveryCommandMessage;
import in.craves.integration.delivery.command.DeliveryCommandModels.RoutingResult;
import in.craves.integration.delivery.command.DeliveryCommandRepository.CommandRecord;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.QuoteRequest;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.Stop;
import in.craves.integration.delivery.status.DeliveryLeaseRecoveryRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeliveryCreateReconciliationWorkerTest {

    @Test
    void recoversFinalLeasesThenCompletesProviderDeliveryWithoutAnotherCreate() {
        DeliveryCommandRepository commands = mock(DeliveryCommandRepository.class);
        DeliveryJobRepository deliveryJobs = mock(DeliveryJobRepository.class);
        DeliveryProviderRouter router = mock(DeliveryProviderRouter.class);
        DeliveryCommandCompletionService completion = mock(
            DeliveryCommandCompletionService.class
        );
        DeliveryLeaseRecoveryRepository leaseRecovery = mock(
            DeliveryLeaseRecoveryRepository.class
        );
        DeliveryCommandProperties properties = new DeliveryCommandProperties();
        properties.setReconciliationBatchSize(20);
        properties.setMaxReconciliationAttempts(20);
        properties.setReconciliationStaleMinutes(10);

        DeliveryCreateReconciliationWorker worker =
            new DeliveryCreateReconciliationWorker(
                commands,
                deliveryJobs,
                router,
                completion,
                leaseRecovery,
                properties
            );
        CommandRecord command = pendingCommand();
        RoutingResult routingResult = new RoutingResult(
            "borzo",
            null,
            null,
            null,
            List.of(),
            List.of()
        );
        UUID deliveryJobId = UUID.randomUUID();

        when(commands.claimReconciliationBatch(20, 20, 10))
            .thenReturn(List.of(command));
        when(deliveryJobs.findIdByChefSubOrderId(command.chefSubOrderId()))
            .thenReturn(Optional.empty());
        when(router.reconcile(command)).thenReturn(routingResult);
        when(completion.complete(command.message(), routingResult))
            .thenReturn(new CompletionReceipt(deliveryJobId, false));

        worker.reconcilePending();

        verify(leaseRecovery)
            .deadLetterExhaustedCreateReconciliationLeases(20, 10);
        verify(router).reconcile(command);
        verify(completion).complete(command.message(), routingResult);
        verify(commands, never()).scheduleReconciliationRetry(
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any()
        );
    }

    private static CommandRecord pendingCommand() {
        UUID orderId = UUID.randomUUID();
        UUID subOrderId = UUID.randomUUID();
        DeliveryCommandMessage message = new DeliveryCommandMessage(
            UUID.randomUUID(),
            UUID.randomUUID(),
            orderId,
            orderId,
            subOrderId,
            Instant.parse("2026-07-24T03:30:00Z"),
            Instant.parse("2026-07-24T03:20:00Z"),
            subOrderId.toString(),
            4.6,
            "Madhapur",
            8,
            4,
            quoteRequest()
        );
        return new CommandRecord(
            message.commandId(),
            subOrderId,
            orderId,
            "RECONCILIATION_PENDING",
            1,
            message,
            7001L,
            "delivery-command:test",
            "borzo",
            "CRV-1234567890123456789012345678",
            Instant.parse("2026-07-24T03:00:00Z"),
            1,
            0,
            null,
            null
        );
    }

    private static QuoteRequest quoteRequest() {
        return new QuoteRequest(
            "Packaged food",
            2000,
            true,
            new Stop(
                "Madhapur, Hyderabad",
                "Chef",
                "919999999991",
                new BigDecimal("17.4483"),
                new BigDecimal("78.3915"),
                null,
                null,
                "Pickup"
            ),
            new Stop(
                "Gachibowli, Hyderabad",
                "Customer",
                "919999999992",
                new BigDecimal("17.4401"),
                new BigDecimal("78.3489"),
                null,
                null,
                "Dropoff"
            )
        );
    }
}
