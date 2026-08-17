package in.craves.integration.delivery.provider;

import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Provider-neutral contract used by the Integration Service delivery worker.
 * One adapter implementation exists per external delivery provider.
 */
public interface DeliveryProviderAdapter {
    String providerId();

    ProviderQuote quote(QuoteRequest request);

    ProviderDelivery create(CreateDeliveryRequest request);

    ProviderDelivery cancel(String providerDeliveryId);

    TrackingSnapshot track(String providerDeliveryId);

    /**
     * Attempts to locate a delivery that may already have been created by the provider after Craves
     * lost the create response. Implementations must never create a new provider delivery from this
     * method.
     */
    default CreateReconciliationResult reconcileCreate(String clientReference,
                                                        Instant notBefore) {
        return CreateReconciliationResult.unsupported(
            "Provider does not support deterministic create reconciliation"
        );
    }

    enum DeliveryStatus {
        PENDING,
        SEARCHING,
        COURIER_ASSIGNED,
        COURIER_TO_PICKUP,
        AT_PICKUP,
        PICKED_UP,
        IN_TRANSIT,
        AT_DROPOFF,
        DELIVERED,
        CANCELLED,
        DELAYED,
        RETURNING,
        RETURNED,
        FAILED,
        UNKNOWN
    }

    enum CreateReconciliationStatus {
        FOUND,
        NOT_FOUND,
        INCONCLUSIVE,
        UNSUPPORTED
    }

    /**
     * Canonical delivery stop. The original free-form address remains authoritative for providers
     * that accept it directly (for example Borzo). The additive structured fields preserve address
     * data that Order Service already owns and are required by providers such as Shiprocket.
     */
    record Stop(
        String address,
        String contactName,
        String contactPhone,
        BigDecimal latitude,
        BigDecimal longitude,
        OffsetDateTime requiredStart,
        OffsetDateTime requiredFinish,
        String note,
        String addressLine1,
        String addressLine2,
        String landmark,
        String area,
        String city,
        String state,
        String postalCode,
        String country
    ) {
        public Stop(String address,
                    String contactName,
                    String contactPhone,
                    BigDecimal latitude,
                    BigDecimal longitude,
                    OffsetDateTime requiredStart,
                    OffsetDateTime requiredFinish,
                    String note) {
            this(
                address,
                contactName,
                contactPhone,
                latitude,
                longitude,
                requiredStart,
                requiredFinish,
                note,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
            );
        }
    }

    record ShipmentItem(
        UUID menuItemId,
        String itemName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal
    ) {}

    record QuoteRequest(
        String matter,
        int totalWeightGrams,
        boolean thermoboxRequired,
        Stop pickup,
        Stop dropoff,
        List<ShipmentItem> items,
        BigDecimal declaredGoodsValue,
        String paymentCollectionMode,
        UUID pickupLocationReference
    ) {
        public QuoteRequest(String matter,
                            int totalWeightGrams,
                            boolean thermoboxRequired,
                            Stop pickup,
                            Stop dropoff) {
            this(
                matter,
                totalWeightGrams,
                thermoboxRequired,
                pickup,
                dropoff,
                List.of(),
                null,
                null,
                null
            );
        }
    }

    /**
     * The selected quote is carried into create so an adapter can deterministically book the same
     * courier/service option that delivery intelligence ranked. The legacy constructor remains for
     * existing providers that do not require quote metadata during create.
     */
    record CreateDeliveryRequest(
        String clientReference,
        QuoteRequest quoteRequest,
        ProviderQuote selectedQuote
    ) {
        public CreateDeliveryRequest(String clientReference, QuoteRequest quoteRequest) {
            this(clientReference, quoteRequest, null);
        }
    }

    record ProviderQuote(
        String providerId,
        boolean available,
        BigDecimal paymentAmount,
        BigDecimal deliveryFeeAmount,
        String currency,
        List<String> warnings,
        JsonNode providerMetadata,
        Instant quotedAt
    ) {}

    record ProviderDelivery(
        String providerId,
        String providerDeliveryId,
        String providerOrderName,
        DeliveryStatus status,
        String providerStatus,
        BigDecimal paymentAmount,
        BigDecimal deliveryFeeAmount,
        String trackingUrl,
        JsonNode providerMetadata,
        Instant observedAt
    ) {}

    record Courier(
        String providerCourierId,
        String name,
        String phone,
        String photoUrl,
        BigDecimal latitude,
        BigDecimal longitude
    ) {}

    record TrackingSnapshot(
        ProviderDelivery delivery,
        Courier courier,
        Instant observedAt
    ) {}

    record ProviderStatusUpdate(
        String providerId,
        String providerOrderId,
        String providerDeliveryId,
        DeliveryStatus status,
        String providerStatus,
        String trackingUrl,
        Instant observedAt,
        JsonNode providerMetadata
    ) {
        public ProviderStatusUpdate {
            Objects.requireNonNull(providerId, "providerId is required");
            Objects.requireNonNull(providerOrderId, "providerOrderId is required");
            Objects.requireNonNull(status, "status is required");
            Objects.requireNonNull(observedAt, "observedAt is required");
            providerMetadata = providerMetadata == null
                ? null
                : providerMetadata.deepCopy();
        }

        public static ProviderStatusUpdate fromTracking(TrackingSnapshot snapshot) {
            Objects.requireNonNull(snapshot, "snapshot is required");
            ProviderDelivery delivery = Objects.requireNonNull(
                snapshot.delivery(),
                "tracking delivery is required"
            );
            Instant observedAt = snapshot.observedAt() == null
                ? delivery.observedAt()
                : snapshot.observedAt();
            return new ProviderStatusUpdate(
                delivery.providerId(),
                delivery.providerDeliveryId(),
                null,
                delivery.status(),
                delivery.providerStatus(),
                delivery.trackingUrl(),
                Objects.requireNonNull(
                    observedAt,
                    "tracking observedAt is required"
                ),
                delivery.providerMetadata()
            );
        }
    }

    record CreateReconciliationResult(
        CreateReconciliationStatus status,
        ProviderDelivery delivery,
        String detail
    ) {
        public CreateReconciliationResult {
            Objects.requireNonNull(status, "status is required");
            if (status == CreateReconciliationStatus.FOUND && delivery == null) {
                throw new IllegalArgumentException(
                    "FOUND reconciliation requires a provider delivery"
                );
            }
            if (status != CreateReconciliationStatus.FOUND && delivery != null) {
                throw new IllegalArgumentException(
                    "Only FOUND reconciliation may contain a provider delivery"
                );
            }
        }

        public static CreateReconciliationResult found(ProviderDelivery delivery) {
            return new CreateReconciliationResult(
                CreateReconciliationStatus.FOUND,
                Objects.requireNonNull(delivery, "delivery is required"),
                "Provider delivery recovered by deterministic client reference"
            );
        }

        public static CreateReconciliationResult notFound(String detail) {
            return new CreateReconciliationResult(
                CreateReconciliationStatus.NOT_FOUND,
                null,
                detail
            );
        }

        public static CreateReconciliationResult inconclusive(String detail) {
            return new CreateReconciliationResult(
                CreateReconciliationStatus.INCONCLUSIVE,
                null,
                detail
            );
        }

        public static CreateReconciliationResult unsupported(String detail) {
            return new CreateReconciliationResult(
                CreateReconciliationStatus.UNSUPPORTED,
                null,
                detail
            );
        }
    }

    /**
     * Raised only when the provider create request may have succeeded but the response was not
     * received. Callers must reconcile the deterministic client reference before any retry or
     * provider fallback.
     */
    class ProviderCreateUncertainException extends RuntimeException {
        private final String providerId;
        private final String clientReference;
        private final Instant attemptedAt;

        public ProviderCreateUncertainException(String providerId,
                                                 String clientReference,
                                                 Instant attemptedAt,
                                                 Throwable cause) {
            super(
                "Provider create outcome is uncertain and requires reconciliation",
                cause
            );
            this.providerId = Objects.requireNonNull(providerId, "providerId is required");
            this.clientReference = Objects.requireNonNull(clientReference, "clientReference is required");
            this.attemptedAt = Objects.requireNonNull(attemptedAt, "attemptedAt is required");
        }

        public String providerId() { return providerId; }
        public String clientReference() { return clientReference; }
        public Instant attemptedAt() { return attemptedAt; }
    }
}
