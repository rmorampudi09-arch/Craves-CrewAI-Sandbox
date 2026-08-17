package in.craves.integration.delivery.shiprocket;

import in.craves.integration.delivery.provider.DeliveryProviderAdapter.DeliveryStatus;
import java.util.Locale;

/**
 * Maps Shiprocket tracking/webhook shipment status codes to Craves delivery states.
 *
 * <p>The numeric values intentionally follow Shiprocket's Tracking "Shipment Status Codes"
 * table rather than the separate Orders/current-status id space. Shiprocket responses can expose
 * both status id families, so an explicit recognized status label wins over a numeric id. Unknown
 * or fulfillment-only states stay UNKNOWN so Craves never fabricates delivery progress.</p>
 */
final class ShiprocketStatusMapper {
    private ShiprocketStatusMapper() {}

    static DeliveryStatus map(Integer statusCode, String statusText) {
        DeliveryStatus textMapped = byText(statusText);
        if (textMapped != DeliveryStatus.UNKNOWN) {
            return textMapped;
        }
        if (statusCode != null) {
            return switch (statusCode) {
                case 6, 18, 38, 48, 49, 50, 51, 54, 55, 56, 57 -> DeliveryStatus.IN_TRANSIT;
                case 7 -> DeliveryStatus.DELIVERED;
                case 8, 16, 45 -> DeliveryStatus.CANCELLED;
                case 9, 14, 40, 41, 46, 75, 78 -> DeliveryStatus.RETURNING;
                case 10 -> DeliveryStatus.RETURNED;
                case 11 -> DeliveryStatus.PENDING;
                case 12, 24, 25, 44, 47, 76 -> DeliveryStatus.FAILED;
                case 13, 15, 20, 21, 22, 23, 39, 71, 72, 77 -> DeliveryStatus.DELAYED;
                case 17 -> DeliveryStatus.AT_DROPOFF;
                case 19 -> DeliveryStatus.COURIER_TO_PICKUP;
                case 27, 52 -> DeliveryStatus.COURIER_ASSIGNED;
                case 42 -> DeliveryStatus.PICKED_UP;
                // 26 FULFILLED, 43 SELF FULFILLED and fulfillment-centre-only states such as
                // 59-63/67/68 do not prove customer delivery progress.
                default -> DeliveryStatus.UNKNOWN;
            };
        }
        return DeliveryStatus.UNKNOWN;
    }

    private static DeliveryStatus byText(String statusText) {
        if (statusText == null || statusText.isBlank()) {
            return DeliveryStatus.UNKNOWN;
        }
        String value = statusText.trim().toUpperCase(Locale.ROOT)
            .replace('-', '_')
            .replace(' ', '_');
        if (value.contains("RTO_DELIVERED") || value.contains("RETURN_DELIVERED")) {
            return DeliveryStatus.RETURNED;
        }
        if (value.contains("FULFILLED")) {
            return DeliveryStatus.UNKNOWN;
        }
        if (value.contains("DELIVERED") && !value.contains("UNDELIVERED")) {
            return DeliveryStatus.DELIVERED;
        }
        if (value.contains("CANCEL")) {
            return DeliveryStatus.CANCELLED;
        }
        if (value.contains("RTO") || value.contains("RETURN")) {
            return DeliveryStatus.RETURNING;
        }
        if (value.contains("PICKED_UP")) {
            return DeliveryStatus.PICKED_UP;
        }
        if (value.contains("OUT_FOR_PICKUP")) {
            return DeliveryStatus.COURIER_TO_PICKUP;
        }
        if (value.contains("PICKUP_BOOKED") || value.contains("SHIPMENT_BOOKED") || value.contains("AWB_ASSIGNED")) {
            return DeliveryStatus.COURIER_ASSIGNED;
        }
        if (value.contains("OUT_FOR_DELIVERY")) {
            return DeliveryStatus.AT_DROPOFF;
        }
        if (value.contains("IN_TRANSIT") || value.contains("SHIPPED") || value.contains("REACHED_AT_DESTINATION")) {
            return DeliveryStatus.IN_TRANSIT;
        }
        if (value.contains("LOST") || value.contains("DAMAGED") || value.contains("DESTROYED")
            || value.contains("FAILED") || value.contains("UNTRACEABLE") || value.contains("DISPOSED")) {
            return DeliveryStatus.FAILED;
        }
        if (value.contains("DELAY") || value.contains("EXCEPTION") || value.contains("UNDELIVERED")
            || value.contains("MISROUTED") || value.contains("ISSUE_RELATED_TO_THE_RECIPIENT")) {
            return DeliveryStatus.DELAYED;
        }
        return DeliveryStatus.UNKNOWN;
    }
}
