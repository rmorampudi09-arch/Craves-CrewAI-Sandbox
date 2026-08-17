package in.craves.integration.delivery.status;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.integration.delivery.command.DeliveryCommandProperties;
import in.craves.integration.delivery.status.DeliveryStatusRepository.WebhookWorkItem;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeliveryWebhookProcessorTest {

    @Test
    void recoversFinalLeasesAndSchedulesBoundedRetryWhenProcessingFails() {
        DeliveryStatusRepository repository = mock(DeliveryStatusRepository.class);
        DeliveryLeaseRecoveryRepository leaseRecovery = mock(
            DeliveryLeaseRecoveryRepository.class
        );
        DeliveryStatusUpdateService statusService = mock(
            DeliveryStatusUpdateService.class
        );
        DeliveryCommandProperties properties = new DeliveryCommandProperties();
        properties.setWebhookBatchSize(20);
        properties.setWebhookStaleMinutes(5);
        properties.setMaxWebhookAttempts(10);
        properties.setWebhookRetryBaseSeconds(5);

        WebhookWorkItem workItem = new WebhookWorkItem(
            UUID.randomUUID(),
            "borzo",
            "provider-event-1",
            new ObjectMapper().createObjectNode().put(
                "event_type",
                "delivery_changed"
            ),
            2
        );

        when(repository.claimWebhookBatch(20, 5, 10))
            .thenReturn(List.of(workItem));
        when(statusService.processWebhook(workItem)).thenThrow(
            new DeliveryStatusUpdateService.UnmatchedDeliveryJobException(
                "No delivery job matches provider order 1250032"
            )
        );

        DeliveryWebhookProcessor processor = new DeliveryWebhookProcessor(
            repository,
            leaseRecovery,
            statusService,
            properties
        );

        processor.process();

        verify(leaseRecovery).deadLetterExhaustedWebhookLeases(10, 5);
        verify(repository).markWebhookFailed(
            eq(workItem.id()),
            eq(2),
            eq(10),
            any(),
            eq("No delivery job matches provider order 1250032")
        );
    }
}
