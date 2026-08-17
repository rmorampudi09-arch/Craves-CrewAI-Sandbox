package in.craves.integration.delivery;

import com.fasterxml.jackson.databind.JsonNode;
import in.craves.integration.delivery.DeliveryIntelligenceModels.CandidateInput;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface DeliveryProviderAdapter {
    enum Capability {
        QUOTE,
        CREATE_DELIVERY,
        CANCEL_DELIVERY,
        TRACK,
        WEBHOOK,
        RIDER_LEVEL_CANDIDATES,
        PRE_CHECKOUT_SERVICEABILITY
    }

    String providerId();
    Set<Capability> capabilities();
    List<CandidateInput> quote(QuoteRequest request);
    CreateDeliveryResult createDelivery(CreateDeliveryCommand command);
    CancelDeliveryResult cancelDelivery(CancelDeliveryCommand command);
    TrackingResult track(String providerDeliveryId);
    NormalizedWebhook normalizeWebhook(JsonNode payload, java.util.Map<String, String> headers);

    record Address(String line1, String line2, String city, String state, String postalCode,
                   double latitude, double longitude, String contactName, String contactPhone) {}
    record QuoteRequest(UUID chefSubOrderId, UUID orderId, Address pickup, Address drop,
                        String parcelDescription, double weightKg, Instant readyAt) {}
    record CreateDeliveryCommand(UUID chefSubOrderId, UUID orderId, String idempotencyKey,
                                 String providerQuoteId, Address pickup, Address drop,
                                 String parcelDescription, double weightKg, Instant readyAt) {}
    record CreateDeliveryResult(String providerDeliveryId, String providerStatus,
                                String trackingUrl, String assignedAgentId, Instant createdAt, JsonNode rawResponse) {}
    record CancelDeliveryCommand(String providerDeliveryId, String idempotencyKey, String reason) {}
    record CancelDeliveryResult(boolean cancelled, String providerStatus, JsonNode rawResponse) {}
    record TrackingResult(String providerDeliveryId, String providerStatus, String assignedAgentId,
                          Double riderLatitude, Double riderLongitude, Instant observedAt, JsonNode rawResponse) {}
    record NormalizedWebhook(String providerEventId, String providerDeliveryId,
                             String canonicalStatus, Instant occurredAt, JsonNode normalizedPayload) {}
}
