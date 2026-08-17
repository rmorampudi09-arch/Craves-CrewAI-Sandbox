package in.craves.integration.delivery.command;

import in.craves.integration.delivery.DeliveryIntelligenceModels.AssignmentResponse;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.ProviderDelivery;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.ProviderQuote;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.QuoteRequest;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class DeliveryCommandModels {
    private DeliveryCommandModels() {}

    public static final String CHEF_ACCEPTED_ORDER = "CHEF_ACCEPTED_ORDER";
    public static final String DELIVERY_COMMAND = "DELIVERY_COMMAND";
    public static final String DELIVERY_STATUS_CHANGED = "DELIVERY_STATUS_CHANGED";

    public record EventEnvelope<T>(
        UUID eventId,
        String eventType,
        String eventVersion,
        Instant occurredAt,
        UUID correlationId,
        UUID causationId,
        String source,
        String subject,
        T data
    ) {}

    public record ChefAcceptedOrderData(
        UUID orderId,
        UUID chefSubOrderId,
        Instant readyAt,
        Double distanceKm,
        String area,
        QuoteRequest deliveryRequest
    ) {}

    public record DeliveryCommandMessage(
        UUID commandId,
        UUID sourceEventId,
        UUID correlationId,
        UUID orderId,
        UUID chefSubOrderId,
        Instant readyAt,
        Instant dispatchAt,
        String idempotencyKey,
        double distanceKm,
        String area,
        int orderHour,
        int dayOfWeek,
        QuoteRequest deliveryRequest
    ) {}

    public record QuoteAudit(
        String providerId,
        boolean successful,
        boolean available,
        Double pickupDistanceKm,
        Integer pickupEtaMinutes,
        ProviderQuote quote,
        String error
    ) {}

    public record CreateAudit(
        String providerId,
        boolean successful,
        String error
    ) {}

    public record RoutingResult(
        String providerId,
        ProviderDelivery delivery,
        AssignmentResponse intelligenceAssignment,
        UUID executedCandidateId,
        List<QuoteAudit> quoteAudit,
        List<CreateAudit> createAudit
    ) {}

    public record DeliveryStatusChangedData(
        UUID deliveryJobId,
        UUID orderId,
        UUID chefSubOrderId,
        String providerId,
        String providerDeliveryId,
        String status,
        String trackingUrl,
        Instant observedAt
    ) {}
}
