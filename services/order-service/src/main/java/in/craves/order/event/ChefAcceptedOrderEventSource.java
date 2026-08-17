package in.craves.order.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ChefAcceptedOrderEventSource(
    UUID orderId,
    UUID checkoutId,
    Instant acceptedAt,
    Instant readyAt,
    int totalPackageWeightGrams,
    boolean thermoboxRequired,
    UUID pickupLocationReference,
    String kitchenName,
    String pickupPhoneNumber,
    String pickupAddressLine1,
    String pickupAddressLine2,
    String pickupLandmark,
    String pickupAreaName,
    String pickupCity,
    String pickupState,
    String pickupPostalCode,
    BigDecimal pickupLatitude,
    BigDecimal pickupLongitude,
    String dropoffRecipientName,
    String dropoffPhoneNumber,
    String dropoffAddressLine1,
    String dropoffAddressLine2,
    String dropoffLandmark,
    String dropoffAreaName,
    String dropoffCity,
    String dropoffState,
    String dropoffPostalCode,
    BigDecimal dropoffLatitude,
    BigDecimal dropoffLongitude,
    List<DeliveryItemSource> deliveryItems,
    BigDecimal declaredGoodsValue,
    String paymentCollectionMode
) {
    /**
     * Legacy constructor retained so existing tests and callers that do not need provider commerce
     * metadata remain source compatible.
     */
    public ChefAcceptedOrderEventSource(
        UUID orderId,
        UUID checkoutId,
        Instant acceptedAt,
        Instant readyAt,
        int totalPackageWeightGrams,
        boolean thermoboxRequired,
        String kitchenName,
        String pickupPhoneNumber,
        String pickupAddressLine1,
        String pickupAddressLine2,
        String pickupLandmark,
        String pickupAreaName,
        String pickupCity,
        String pickupState,
        String pickupPostalCode,
        BigDecimal pickupLatitude,
        BigDecimal pickupLongitude,
        String dropoffRecipientName,
        String dropoffPhoneNumber,
        String dropoffAddressLine1,
        String dropoffAddressLine2,
        String dropoffLandmark,
        String dropoffAreaName,
        String dropoffCity,
        String dropoffState,
        String dropoffPostalCode,
        BigDecimal dropoffLatitude,
        BigDecimal dropoffLongitude
    ) {
        this(
            orderId,
            checkoutId,
            acceptedAt,
            readyAt,
            totalPackageWeightGrams,
            thermoboxRequired,
            null,
            kitchenName,
            pickupPhoneNumber,
            pickupAddressLine1,
            pickupAddressLine2,
            pickupLandmark,
            pickupAreaName,
            pickupCity,
            pickupState,
            pickupPostalCode,
            pickupLatitude,
            pickupLongitude,
            dropoffRecipientName,
            dropoffPhoneNumber,
            dropoffAddressLine1,
            dropoffAddressLine2,
            dropoffLandmark,
            dropoffAreaName,
            dropoffCity,
            dropoffState,
            dropoffPostalCode,
            dropoffLatitude,
            dropoffLongitude,
            List.of(),
            null,
            null
        );
    }

    public record DeliveryItemSource(
        UUID menuItemId,
        String itemName,
        BigDecimal unitPrice,
        int quantity,
        BigDecimal lineTotal
    ) {}
}
