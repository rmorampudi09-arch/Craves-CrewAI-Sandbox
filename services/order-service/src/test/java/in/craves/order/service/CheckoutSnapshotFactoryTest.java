package in.craves.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import in.craves.order.exception.OrderApiException;
import in.craves.order.service.CatalogClient.CatalogKitchen;
import in.craves.order.service.CustomerAddressClient.CustomerAddress;
import in.craves.order.web.ApiDtos.CustomerAddressSnapshotResponse;
import in.craves.order.web.ApiDtos.KitchenPickupSnapshotResponse;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CheckoutSnapshotFactoryTest {
    private final CheckoutSnapshotFactory factory = new CheckoutSnapshotFactory();

    @Test
    void createsImmutableCustomerDropoffSnapshot() {
        UUID addressId = UUID.randomUUID();
        CustomerAddress address = new CustomerAddress(
            addressId,
            UUID.randomUUID(),
            "HOME",
            "Raviteja",
            "+918019166645",
            "Plot 10, Road 2",
            "Fourth floor",
            "Near Metro",
            "Madhapur",
            "Hyderabad",
            "Telangana",
            "500081",
            new BigDecimal("17.4483000"),
            new BigDecimal("78.3915000"),
            true,
            true,
            Instant.parse("2026-07-16T08:00:00Z"),
            Instant.parse("2026-07-16T08:00:00Z")
        );

        CustomerAddressSnapshotResponse snapshot = factory.customerDropoff(address);

        assertThat(snapshot.sourceAddressId()).isEqualTo(addressId);
        assertThat(snapshot.recipientName()).isEqualTo("Raviteja");
        assertThat(snapshot.contactPhoneNumber()).isEqualTo("+918019166645");
        assertThat(snapshot.areaName()).isEqualTo("Madhapur");
        assertThat(snapshot.latitude()).isEqualByComparingTo("17.4483000");
        assertThat(snapshot.longitude()).isEqualByComparingTo("78.3915000");
    }

    @Test
    void rejectsInactiveCustomerAddress() {
        CustomerAddress address = new CustomerAddress(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "HOME",
            "Raviteja",
            "+918019166645",
            "Plot 10, Road 2",
            null,
            null,
            "Madhapur",
            "Hyderabad",
            "Telangana",
            "500081",
            new BigDecimal("17.4483000"),
            new BigDecimal("78.3915000"),
            false,
            false,
            Instant.now(),
            Instant.now()
        );

        OrderApiException exception = catchThrowableOfType(
            () -> factory.customerDropoff(address),
            OrderApiException.class
        );

        assertThat(exception.code()).isEqualTo("DELIVERY_ADDRESS_INCOMPLETE");
    }

    @Test
    void createsImmutableKitchenPickupSnapshot() {
        UUID kitchenId = UUID.randomUUID();
        CatalogKitchen kitchen = new CatalogKitchen(
            kitchenId,
            UUID.randomUUID(),
            "Lakshmi Home Kitchen",
            "Lakshmi's Kitchen",
            "Homemade Telugu meals",
            "+919876543210",
            "kitchen@example.com",
            "House 21, Street 5",
            "Ground floor",
            "Opposite park",
            "Kondapur",
            "Hyderabad",
            "Telangana",
            "500084",
            new BigDecimal("17.4698000"),
            new BigDecimal("78.3651000"),
            "ACTIVE"
        );

        KitchenPickupSnapshotResponse snapshot = factory.kitchenPickup(kitchen);

        assertThat(snapshot.kitchenId()).isEqualTo(kitchenId);
        assertThat(snapshot.kitchenName()).isEqualTo("Lakshmi's Kitchen");
        assertThat(snapshot.contactPhoneNumber()).isEqualTo("+919876543210");
        assertThat(snapshot.addressLine1()).isEqualTo("House 21, Street 5");
        assertThat(snapshot.latitude()).isEqualByComparingTo("17.4698000");
    }

    @Test
    void rejectsKitchenWithoutRequiredPickupCoordinates() {
        CatalogKitchen kitchen = new CatalogKitchen(
            UUID.randomUUID(),
            UUID.randomUUID(),
            "Lakshmi Home Kitchen",
            null,
            null,
            "+919876543210",
            null,
            "House 21, Street 5",
            null,
            null,
            "Kondapur",
            "Hyderabad",
            "Telangana",
            "500084",
            null,
            null,
            "ACTIVE"
        );

        OrderApiException exception = catchThrowableOfType(
            () -> factory.kitchenPickup(kitchen),
            OrderApiException.class
        );

        assertThat(exception.code()).isEqualTo("KITCHEN_PICKUP_ADDRESS_INCOMPLETE");
    }
}
