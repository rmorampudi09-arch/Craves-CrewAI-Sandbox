package in.craves.integration.delivery.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.integration.delivery.command.DeliveryCommandProperties;
import in.craves.integration.delivery.command.DeliveryOutboxRepository;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.DeliveryStatus;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.ProviderStatusUpdate;
import in.craves.integration.delivery.provider.DeliveryWebhookNormalizer;
import in.craves.integration.delivery.status.DeliveryStatusRepository.DeliveryJobState;
import in.craves.integration.delivery.status.DeliveryStatusRepository.WebhookWorkItem;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DeliveryStatusUpdateServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void appliesNewerWebhookAndEnqueuesStatusOutboxTransactionally() {
        DeliveryStatusRepository repository = mock(DeliveryStatusRepository.class);
        DeliveryOutboxRepository outbox = mock(DeliveryOutboxRepository.class);
        DeliveryWebhookNormalizer normalizer = mock(DeliveryWebhookNormalizer.class);
        DeliveryCommandProperties properties = new DeliveryCommandProperties();
        properties.setTrackingPollSeconds(60);
        when(normalizer.providerId()).thenReturn("borzo");

        UUID jobId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID subOrderId = UUID.randomUUID();
        Instant observedAt = Instant.parse("2026-07-24T03:00:00Z");
        ProviderStatusUpdate update = update(DeliveryStatus.AT_PICKUP, observedAt);
        WebhookWorkItem workItem = workItem();
        DeliveryJobState job = job(
            jobId,
            orderId,
            subOrderId,
            "COURIER_TO_PICKUP",
            Instant.parse("2026-07-24T02:59:00Z")
        );

        when(normalizer.normalize(workItem.rawPayload())).thenReturn(update);
        when(repository.findJobByProviderOrder("borzo", "1250032"))
            .thenReturn(Optional.of(job));
        when(repository.lockJob(jobId)).thenReturn(Optional.of(job));
        when(repository.insertEventIfAbsent(
            eq(jobId),
            eq("borzo"),
            eq(workItem.providerEventId()),
            eq("delivery_changed"),
            eq("WEBHOOK"),
            eq("AT_PICKUP"),
            eq("courier_at_pickup"),
            eq(workItem.rawPayload()),
            eq(observedAt),
            eq(true),
            isNull()
        )).thenReturn(true);

        DeliveryStatusUpdateService service = new DeliveryStatusUpdateService(
            List.of(normalizer),
            repository,
            outbox,
            properties,
            objectMapper
        );

        var result = service.processWebhook(workItem);

        assertThat(result.applied()).isTrue();
        assertThat(result.duplicate()).isFalse();
        verify(repository).applyJobStatus(
            eq(jobId),
            eq("AT_PICKUP"),
            eq("courier_at_pickup"),
            eq("https://tracking.example/1"),
            eq(observedAt),
            eq("WEBHOOK"),
            any()
        );
        verify(outbox).enqueue(
            eq("DELIVERY_STATUS_CHANGED"),
            eq(jobId),
            eq(orderId),
            any()
        );
        verify(repository).markWebhookProcessed(
            workItem.id(),
            jobId,
            "1250032",
            "11712",
            "AT_PICKUP",
            "APPLIED"
        );
    }

    @Test
    void recordsOlderWebhookButDoesNotRegressDeliveryJobOrPublish() {
        DeliveryStatusRepository repository = mock(DeliveryStatusRepository.class);
        DeliveryOutboxRepository outbox = mock(DeliveryOutboxRepository.class);
        DeliveryWebhookNormalizer normalizer = mock(DeliveryWebhookNormalizer.class);
        DeliveryCommandProperties properties = new DeliveryCommandProperties();
        when(normalizer.providerId()).thenReturn("borzo");

        UUID jobId = UUID.randomUUID();
        DeliveryJobState job = job(
            jobId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            "IN_TRANSIT",
            Instant.parse("2026-07-24T03:05:00Z")
        );
        WebhookWorkItem workItem = workItem();
        ProviderStatusUpdate update = update(
            DeliveryStatus.AT_PICKUP,
            Instant.parse("2026-07-24T03:00:00Z")
        );

        when(normalizer.normalize(workItem.rawPayload())).thenReturn(update);
        when(repository.findJobByProviderOrder("borzo", "1250032"))
            .thenReturn(Optional.of(job));
        when(repository.lockJob(jobId)).thenReturn(Optional.of(job));
        when(repository.insertEventIfAbsent(
            eq(jobId),
            eq("borzo"),
            eq(workItem.providerEventId()),
            eq("delivery_changed"),
            eq("WEBHOOK"),
            eq("AT_PICKUP"),
            eq("courier_at_pickup"),
            eq(workItem.rawPayload()),
            eq(update.observedAt()),
            eq(false),
            eq("STALE_OR_EQUAL_OBSERVED_AT")
        )).thenReturn(true);

        DeliveryStatusUpdateService service = new DeliveryStatusUpdateService(
            List.of(normalizer),
            repository,
            outbox,
            properties,
            objectMapper
        );

        var result = service.processWebhook(workItem);

        assertThat(result.applied()).isFalse();
        assertThat(result.result()).isEqualTo("STALE_OR_EQUAL_OBSERVED_AT");
        verify(repository, never()).applyJobStatus(
            any(),
            anyString(),
            anyString(),
            any(),
            any(),
            anyString(),
            any()
        );
        verify(outbox, never()).enqueue(anyString(), any(), any(), any());
        verify(repository).markWebhookProcessed(
            workItem.id(),
            jobId,
            "1250032",
            "11712",
            "AT_PICKUP",
            "STALE_OR_EQUAL_OBSERVED_AT"
        );
    }

    @Test
    void protectsTerminalDeliveryFromLaterNonTerminalCallback() {
        DeliveryStatusRepository repository = mock(DeliveryStatusRepository.class);
        DeliveryOutboxRepository outbox = mock(DeliveryOutboxRepository.class);
        DeliveryWebhookNormalizer normalizer = mock(DeliveryWebhookNormalizer.class);
        DeliveryCommandProperties properties = new DeliveryCommandProperties();
        when(normalizer.providerId()).thenReturn("borzo");

        UUID jobId = UUID.randomUUID();
        DeliveryJobState job = job(
            jobId,
            UUID.randomUUID(),
            UUID.randomUUID(),
            "DELIVERED",
            Instant.parse("2026-07-24T03:00:00Z")
        );
        WebhookWorkItem workItem = workItem();
        ProviderStatusUpdate update = update(
            DeliveryStatus.IN_TRANSIT,
            Instant.parse("2026-07-24T03:10:00Z")
        );

        when(normalizer.normalize(workItem.rawPayload())).thenReturn(update);
        when(repository.findJobByProviderOrder("borzo", "1250032"))
            .thenReturn(Optional.of(job));
        when(repository.lockJob(jobId)).thenReturn(Optional.of(job));
        when(repository.insertEventIfAbsent(
            eq(jobId),
            eq("borzo"),
            eq(workItem.providerEventId()),
            eq("delivery_changed"),
            eq("WEBHOOK"),
            eq("IN_TRANSIT"),
            eq("courier_at_pickup"),
            eq(workItem.rawPayload()),
            eq(update.observedAt()),
            eq(false),
            eq("TERMINAL_STATUS_PROTECTED")
        )).thenReturn(true);

        DeliveryStatusUpdateService service = new DeliveryStatusUpdateService(
            List.of(normalizer),
            repository,
            outbox,
            properties,
            objectMapper
        );

        var result = service.processWebhook(workItem);

        assertThat(result.applied()).isFalse();
        assertThat(result.result()).isEqualTo("TERMINAL_STATUS_PROTECTED");
        verify(repository, never()).applyJobStatus(
            any(),
            anyString(),
            anyString(),
            any(),
            any(),
            anyString(),
            any()
        );
        verify(outbox, never()).enqueue(anyString(), any(), any(), any());
    }

    private WebhookWorkItem workItem() {
        return new WebhookWorkItem(
            UUID.randomUUID(),
            "borzo",
            "provider-event-1",
            objectMapper.createObjectNode().put("event_type", "delivery_changed"),
            1
        );
    }

    private static ProviderStatusUpdate update(DeliveryStatus status,
                                               Instant observedAt) {
        return new ProviderStatusUpdate(
            "borzo",
            "1250032",
            "11712",
            status,
            "courier_at_pickup",
            "https://tracking.example/1",
            observedAt,
            new ObjectMapper().createObjectNode()
        );
    }

    private static DeliveryJobState job(UUID jobId,
                                        UUID orderId,
                                        UUID subOrderId,
                                        String status,
                                        Instant lastObservedAt) {
        return new DeliveryJobState(
            jobId,
            orderId,
            subOrderId,
            "borzo",
            "1250032",
            status,
            status.toLowerCase(),
            "https://tracking.example/1",
            lastObservedAt
        );
    }
}
