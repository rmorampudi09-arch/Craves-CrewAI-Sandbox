package in.craves.order.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record ChefAcceptedOrderEventData(
    UUID orderId,
    UUID chefSubOrderId,
    Instant readyAt,
    Double distanceKm,
    String area,
    DeliveryRequestData deliveryRequest
) {
    public record DeliveryRequestData(
        String matter,
        int totalWeightGrams,
        boolean thermoboxRequired,
        DeliveryStopData pickup,
        DeliveryStopData dropoff,
        List<DeliveryItemData> items,
        BigDecimal declaredGoodsValue,
        String paymentCollectionMode,
        UUID pickupLocationReference
    ) {
        public DeliveryRequestData(String matter,
                                   int totalWeightGrams,
                                   boolean thermoboxRequired,
                                   DeliveryStopData pickup,
                                   DeliveryStopData dropoff) {
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

    public record DeliveryItemData(
        UUID menuItemId,
        String itemName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal
    ) {}

    /**
     * Additive structured address fields preserve data already held by Order Service for providers
     * that require locality components. The legacy constructor remains for source compatibility.
     */
    public record DeliveryStopData(
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
        public DeliveryStopData(String address,
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
}
