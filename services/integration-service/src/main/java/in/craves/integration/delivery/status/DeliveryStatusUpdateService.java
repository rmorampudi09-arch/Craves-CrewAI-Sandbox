package in.craves.integration.delivery.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.integration.delivery.command.DeliveryCommandModels;
import in.craves.integration.delivery.command.DeliveryCommandModels.DeliveryStatusChangedData;
import in.craves.integration.delivery.command.DeliveryCommandModels.EventEnvelope;
import in.craves.integration.delivery.command.DeliveryCommandProperties;
import in.craves.integration.delivery.command.DeliveryOutboxRepository;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.DeliveryStatus;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.ProviderStatusUpdate;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.TrackingSnapshot;
import in.craves.integration.delivery.provider.DeliveryWebhookNormalizer;
import in.craves.integration.delivery.status.DeliveryStatusRepository.DeliveryJobState;
import in.craves.integration.delivery.status.DeliveryStatusRepository.TrackingWorkItem;
import in.craves.integration.delivery.status.DeliveryStatusRepository.WebhookWorkItem;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class DeliveryStatusUpdateService {
    private final Map<String, DeliveryWebhookNormalizer> normalizers;
    private final DeliveryStatusRepository repository;
    private final DeliveryOutboxRepository outbox;
    private final DeliveryCommandProperties properties;
    private final ObjectMapper objectMapper;

    public DeliveryStatusUpdateService(List<DeliveryWebhookNormalizer> normalizers,
                                       DeliveryStatusRepository repository,
                                       DeliveryOutboxRepository outbox,
                                       DeliveryCommandProperties properties,
                                       ObjectMapper objectMapper) {
        this.normalizers = indexNormalizers(normalizers);
        this.repository = repository;
        this.outbox = outbox;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ProcessingResult processWebhook(WebhookWorkItem workItem) {
        DeliveryWebhookNormalizer normalizer = normalizer(workItem.providerId());
        ProviderStatusUpdate update = normalizer.normalize(workItem.rawPayload());
        requireProviderMatch(workItem.providerId(), update.providerId());

        DeliveryJobState discovered = repository.findJobByProviderOrder(
            update.providerId(),
            update.providerOrderId()
        ).orElseThrow(() -> new UnmatchedDeliveryJobException(
            "No delivery job matches provider order " + safe(update.providerOrderId())
        ));

        DeliveryJobState job = repository.lockJob(discovered.id())
            .orElseThrow(() -> new UnmatchedDeliveryJobException(
                "Delivery job disappeared during processing"
            ));

        Decision decision = decide(job, update);
        String eventType = webhookEventType(workItem.rawPayload());
        boolean inserted = repository.insertEventIfAbsent(
            job.id(),
            update.providerId(),
            workItem.providerEventId(),
            eventType,
            "WEBHOOK",
            update.status().name(),
            update.providerStatus(),
            workItem.rawPayload(),
            update.observedAt(),
            decision.applied(),
            decision.applied() ? null : decision.reason()
        );

        if (!inserted) {
            repository.markWebhookDuplicate(
                workItem.id(),
                "PROVIDER_EVENT_ALREADY_RECORDED"
            );
            return new ProcessingResult(
                job.id(),
                false,
                true,
                "PROVIDER_EVENT_ALREADY_RECORDED"
            );
        }

        if (decision.applied()) {
            applyAndPublish(job, update, "WEBHOOK");
        }

        repository.markWebhookProcessed(
            workItem.id(),
            job.id(),
            update.providerOrderId(),
            update.providerDeliveryId(),
            update.status().name(),
            decision.reason()
        );
        return new ProcessingResult(
            job.id(),
            decision.applied(),
            false,
            decision.reason()
        );
    }

    @Transactional
    public ProcessingResult processTracking(TrackingWorkItem workItem,
                                            TrackingSnapshot snapshot) {
        ProviderStatusUpdate update = ProviderStatusUpdate.fromTracking(snapshot);
        requireProviderMatch(workItem.providerId(), update.providerId());
        if (!workItem.providerDeliveryId().equals(update.providerOrderId())) {
            throw new IllegalStateException(
                "Tracking response provider order does not match the claimed job"
            );
        }

        DeliveryJobState job = repository.lockJob(workItem.deliveryJobId())
            .orElseThrow(() -> new UnmatchedDeliveryJobException(
                "Tracked delivery job no longer exists"
            ));
        Decision decision = decide(job, update);
        Instant nextTrackingAt = nextTrackingAt(update.status());

        if (!decision.applied()) {
            repository.markTrackingNoChange(job.id(), nextTrackingAt);
            return new ProcessingResult(
                job.id(),
                false,
                false,
                decision.reason()
            );
        }

        String providerEventId = trackingEventId(update);
        JsonNode payload = objectMapper.valueToTree(snapshot);
        boolean inserted = repository.insertEventIfAbsent(
            job.id(),
            update.providerId(),
            providerEventId,
            "TRACK_RECONCILIATION",
            "TRACK",
            update.status().name(),
            update.providerStatus(),
            payload,
            update.observedAt(),
            true,
            null
        );
        if (!inserted) {
            repository.markTrackingNoChange(job.id(), nextTrackingAt);
            return new ProcessingResult(
                job.id(),
                false,
                true,
                "TRACK_EVENT_ALREADY_RECORDED"
            );
        }

        applyAndPublish(job, update, "TRACK");
        return new ProcessingResult(job.id(), true, false, "APPLIED");
    }

    private void applyAndPublish(DeliveryJobState job,
                                 ProviderStatusUpdate update,
                                 String source) {
        repository.applyJobStatus(
            job.id(),
            update.status().name(),
            update.providerStatus(),
            update.trackingUrl(),
            update.observedAt(),
            source,
            nextTrackingAt(update.status())
        );

        DeliveryStatusChangedData data = new DeliveryStatusChangedData(
            job.id(),
            job.orderId(),
            job.chefSubOrderId(),
            update.providerId(),
            update.providerOrderId(),
            update.status().name(),
            update.trackingUrl(),
            update.observedAt()
        );
        EventEnvelope<DeliveryStatusChangedData> event = new EventEnvelope<>(
            UUID.randomUUID(),
            DeliveryCommandModels.DELIVERY_STATUS_CHANGED,
            "1.0",
            Instant.now(),
            job.orderId(),
            null,
            "integration-service",
            "delivery-job/" + job.id(),
            data
        );
        outbox.enqueue(
            DeliveryCommandModels.DELIVERY_STATUS_CHANGED,
            job.id(),
            job.orderId(),
            objectMapper.valueToTree(event)
        );
    }

    private Decision decide(DeliveryJobState job,
                            ProviderStatusUpdate update) {
        if (update.status() == DeliveryStatus.UNKNOWN) {
            return Decision.ignored("UNKNOWN_PROVIDER_STATUS");
        }
        if (job.lastStatusObservedAt() != null
            && !update.observedAt().isAfter(job.lastStatusObservedAt())) {
            return Decision.ignored("STALE_OR_EQUAL_OBSERVED_AT");
        }
        DeliveryStatus current = parseStatus(job.status());
        if (isTerminal(current) && current != update.status()) {
            return Decision.ignored("TERMINAL_STATUS_PROTECTED");
        }
        if (current == update.status()
            && Objects.equals(
                normalize(job.providerStatus()),
                normalize(update.providerStatus())
            )
            && Objects.equals(
                normalize(job.trackingUrl()),
                normalize(update.trackingUrl())
            )) {
            return Decision.ignored("NO_STATE_CHANGE");
        }
        return Decision.accepted();
    }

    private Instant nextTrackingAt(DeliveryStatus status) {
        return isTerminal(status)
            ? null
            : Instant.now().plusSeconds(properties.getTrackingPollSeconds());
    }

    private DeliveryWebhookNormalizer normalizer(String providerId) {
        DeliveryWebhookNormalizer normalizer = normalizers.get(normalize(providerId));
        if (normalizer == null) {
            throw new UnsupportedProviderException(
                "No webhook normalizer is registered for provider " + safe(providerId)
            );
        }
        return normalizer;
    }

    private static Map<String, DeliveryWebhookNormalizer> indexNormalizers(
        List<DeliveryWebhookNormalizer> normalizers
    ) {
        Map<String, DeliveryWebhookNormalizer> indexed = new HashMap<>();
        for (DeliveryWebhookNormalizer normalizer : normalizers) {
            DeliveryWebhookNormalizer previous = indexed.put(
                normalize(normalizer.providerId()),
                normalizer
            );
            if (previous != null) {
                throw new IllegalStateException(
                    "Duplicate delivery webhook normalizer " + normalizer.providerId()
                );
            }
        }
        return Map.copyOf(indexed);
    }

    private static void requireProviderMatch(String expected,
                                             String actual) {
        if (!normalize(expected).equals(normalize(actual))) {
            throw new IllegalStateException(
                "Webhook provider does not match the selected normalizer"
            );
        }
    }

    private static String webhookEventType(JsonNode payload) {
        String value = payload.path("event_type").asText(null);
        return StringUtils.hasText(value) ? value : "provider_webhook";
    }

    private static DeliveryStatus parseStatus(String value) {
        try {
            return DeliveryStatus.valueOf(value);
        } catch (Exception ignored) {
            return DeliveryStatus.UNKNOWN;
        }
    }

    private static boolean isTerminal(DeliveryStatus status) {
        return status == DeliveryStatus.DELIVERED
            || status == DeliveryStatus.CANCELLED
            || status == DeliveryStatus.RETURNED
            || status == DeliveryStatus.FAILED;
    }

    private static String trackingEventId(ProviderStatusUpdate update) {
        String canonical = String.join(
            "|",
            update.providerId(),
            update.providerOrderId(),
            update.status().name(),
            normalize(update.providerStatus()),
            update.observedAt().toString()
        );
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return "track-" + HexFormat.of().formatHex(
                digest.digest(canonical.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception ex) {
            throw new IllegalStateException(
                "Could not derive tracking event identity",
                ex
            );
        }
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String safe(String value) {
        if (value == null) {
            return "<null>";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= 200
            ? normalized
            : normalized.substring(0, 200);
    }

    private record Decision(boolean applied, String reason) {
        static Decision accepted() {
            return new Decision(true, "APPLIED");
        }

        static Decision ignored(String reason) {
            return new Decision(false, reason);
        }
    }

    public record ProcessingResult(
        UUID deliveryJobId,
        boolean applied,
        boolean duplicate,
        String result
    ) {}

    public static class UnmatchedDeliveryJobException extends RuntimeException {
        public UnmatchedDeliveryJobException(String message) {
            super(message);
        }
    }

    public static class UnsupportedProviderException extends RuntimeException {
        public UnsupportedProviderException(String message) {
            super(message);
        }
    }
}
