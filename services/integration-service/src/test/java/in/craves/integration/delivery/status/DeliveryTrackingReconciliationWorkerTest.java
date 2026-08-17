package in.craves.integration.delivery.status;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.integration.delivery.command.DeliveryCommandProperties;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.DeliveryStatus;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.ProviderDelivery;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.TrackingSnapshot;
import in.craves.integration.delivery.status.DeliveryStatusRepository.TrackingWorkItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeliveryTrackingReconciliationWorkerTest {

    @Test
    void recoversFinalLeasesThenPerformsReadOnlyProviderTracking() {
        DeliveryProviderAdapter adapter = mock(DeliveryProviderAdapter.class);
        DeliveryStatusRepository repository = mock(DeliveryStatusRepository.class);
        DeliveryLeaseRecoveryRepository leaseRecovery = mock(
            DeliveryLeaseRecoveryRepository.class
        );
        DeliveryStatusUpdateService statusService = mock(
            DeliveryStatusUpdateService.class
        );
        DeliveryCommandProperties properties = new DeliveryCommandProperties();
        properties.setTrackingBatchSize(20);
        properties.setTrackingStaleMinutes(5);
        properties.setMaxTrackingAttempts(20);

        TrackingWorkItem workItem = new TrackingWorkItem(
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            "borzo",
            "1250032",
            1
        );
        TrackingSnapshot snapshot = new TrackingSnapshot(
            new ProviderDelivery(
                "borzo",
                "1250032",
                "50032",
                DeliveryStatus.IN_TRANSIT,
                "active",
                new BigDecimal("125.50"),
                new BigDecimal("125.50"),
                "https://tracking.example/1",
                new ObjectMapper().createObjectNode(),
                Instant.parse("2026-07-24T03:00:00Z")
            ),
            null,
            Instant.parse("2026-07-24T03:00:00Z")
        );

        when(adapter.providerId()).thenReturn("borzo");
        when(repository.claimTrackingBatch(20, 5, 20))
            .thenReturn(List.of(workItem));
        when(adapter.track("1250032")).thenReturn(snapshot);
        when(statusService.processTracking(workItem, snapshot)).thenReturn(
            new DeliveryStatusUpdateService.ProcessingResult(
                workItem.deliveryJobId(),
                true,
                false,
                "APPLIED"
            )
        );

        DeliveryTrackingReconciliationWorker worker =
            new DeliveryTrackingReconciliationWorker(
                List.of(adapter),
                repository,
                leaseRecovery,
                statusService,
                properties
            );

        worker.reconcile();

        verify(leaseRecovery).deadLetterExhaustedTrackingLeases(20, 5);
        verify(adapter).track("1250032");
        verify(statusService).processTracking(workItem, snapshot);
    }
}
