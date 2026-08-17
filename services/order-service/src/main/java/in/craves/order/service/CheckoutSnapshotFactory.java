package in.craves.order.service;

import in.craves.order.exception.OrderApiException;
import in.craves.order.service.CatalogClient.CatalogKitchen;
import in.craves.order.service.CustomerAddressClient.CustomerAddress;
import in.craves.order.web.ApiDtos.CustomerAddressSnapshotResponse;
import in.craves.order.web.ApiDtos.KitchenPickupSnapshotResponse;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class CheckoutSnapshotFactory {
    public CustomerAddressSnapshotResponse customerDropoff(CustomerAddress address) {
        if (address == null || address.id() == null || !address.active()
            || !hasRequiredText(
                address.recipientName(),
                address.contactPhoneNumber(),
                address.addressLine1(),
                address.areaName(),
                address.city(),
                address.state(),
                address.postalCode()
            )
            || !validCoordinates(address.latitude(), address.longitude())) {
            throw OrderApiException.conflict(
                "DELIVERY_ADDRESS_INCOMPLETE",
                "The selected delivery address is incomplete. Update it before placing the order."
            );
        }

        return new CustomerAddressSnapshotResponse(
            address.id(),
            address.recipientName().trim(),
            address.contactPhoneNumber().trim(),
            address.addressLine1().trim(),
            trimToNull(address.addressLine2()),
            trimToNull(address.landmark()),
            address.areaName().trim(),
            address.city().trim(),
            address.state().trim(),
            address.postalCode().trim(),
            address.latitude(),
            address.longitude()
        );
    }

    public KitchenPickupSnapshotResponse kitchenPickup(CatalogKitchen kitchen) {
        String kitchenName = kitchen == null ? null : displayKitchenName(kitchen);
        if (kitchen == null || kitchen.id() == null || !"ACTIVE".equalsIgnoreCase(kitchen.status())
            || !hasRequiredText(
                kitchenName,
                kitchen.phoneNumber(),
                kitchen.addressLine1(),
                kitchen.areaName(),
                kitchen.city(),
                kitchen.state(),
                kitchen.postalCode()
            )
            || !validCoordinates(kitchen.latitude(), kitchen.longitude())) {
            throw OrderApiException.conflict(
                "KITCHEN_PICKUP_ADDRESS_INCOMPLETE",
                "The kitchen pickup profile is incomplete and cannot be used for checkout."
            );
        }

        return new KitchenPickupSnapshotResponse(
            kitchen.id(),
            kitchenName.trim(),
            kitchen.phoneNumber().trim(),
            trimToNull(kitchen.email()),
            kitchen.addressLine1().trim(),
            trimToNull(kitchen.addressLine2()),
            trimToNull(kitchen.landmark()),
            kitchen.areaName().trim(),
            kitchen.city().trim(),
            kitchen.state().trim(),
            kitchen.postalCode().trim(),
            kitchen.latitude(),
            kitchen.longitude()
        );
    }

    private static boolean hasRequiredText(String... values) {
        for (String value : values) {
            if (!StringUtils.hasText(value)) {
                return false;
            }
        }
        return true;
    }

    private static boolean validCoordinates(BigDecimal latitude, BigDecimal longitude) {
        return latitude != null
            && longitude != null
            && latitude.compareTo(BigDecimal.valueOf(-90)) >= 0
            && latitude.compareTo(BigDecimal.valueOf(90)) <= 0
            && longitude.compareTo(BigDecimal.valueOf(-180)) >= 0
            && longitude.compareTo(BigDecimal.valueOf(180)) <= 0;
    }

    private static String displayKitchenName(CatalogKitchen kitchen) {
        return StringUtils.hasText(kitchen.displayName()) ? kitchen.displayName() : kitchen.kitchenName();
    }

    private static String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
