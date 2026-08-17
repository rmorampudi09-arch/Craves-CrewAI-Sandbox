package in.craves.integration.delivery.shiprocket;

import static in.craves.integration.delivery.provider.DeliveryProviderAdapter.DeliveryStatus.AT_DROPOFF;
import static in.craves.integration.delivery.provider.DeliveryProviderAdapter.DeliveryStatus.CANCELLED;
import static in.craves.integration.delivery.provider.DeliveryProviderAdapter.DeliveryStatus.COURIER_ASSIGNED;
import static in.craves.integration.delivery.provider.DeliveryProviderAdapter.DeliveryStatus.COURIER_TO_PICKUP;
import static in.craves.integration.delivery.provider.DeliveryProviderAdapter.DeliveryStatus.DELAYED;
import static in.craves.integration.delivery.provider.DeliveryProviderAdapter.DeliveryStatus.DELIVERED;
import static in.craves.integration.delivery.provider.DeliveryProviderAdapter.DeliveryStatus.FAILED;
import static in.craves.integration.delivery.provider.DeliveryProviderAdapter.DeliveryStatus.IN_TRANSIT;
import static in.craves.integration.delivery.provider.DeliveryProviderAdapter.DeliveryStatus.PICKED_UP;
import static in.craves.integration.delivery.provider.DeliveryProviderAdapter.DeliveryStatus.RETURNED;
import static in.craves.integration.delivery.provider.DeliveryProviderAdapter.DeliveryStatus.RETURNING;
import static in.craves.integration.delivery.provider.DeliveryProviderAdapter.DeliveryStatus.UNKNOWN;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ShiprocketStatusMapperTest {

    @Test
    void mapsDocumentedTrackingShipmentStatusCodes() {
        assertThat(ShiprocketStatusMapper.map(6, null)).isEqualTo(IN_TRANSIT);
        assertThat(ShiprocketStatusMapper.map(7, null)).isEqualTo(DELIVERED);
        assertThat(ShiprocketStatusMapper.map(8, null)).isEqualTo(CANCELLED);
        assertThat(ShiprocketStatusMapper.map(9, null)).isEqualTo(RETURNING);
        assertThat(ShiprocketStatusMapper.map(10, null)).isEqualTo(RETURNED);
        assertThat(ShiprocketStatusMapper.map(12, null)).isEqualTo(FAILED);
        assertThat(ShiprocketStatusMapper.map(13, null)).isEqualTo(DELAYED);
        assertThat(ShiprocketStatusMapper.map(17, null)).isEqualTo(AT_DROPOFF);
        assertThat(ShiprocketStatusMapper.map(19, null)).isEqualTo(COURIER_TO_PICKUP);
        assertThat(ShiprocketStatusMapper.map(22, null)).isEqualTo(DELAYED);
        assertThat(ShiprocketStatusMapper.map(27, null)).isEqualTo(COURIER_ASSIGNED);
        assertThat(ShiprocketStatusMapper.map(38, null)).isEqualTo(IN_TRANSIT);
        assertThat(ShiprocketStatusMapper.map(42, null)).isEqualTo(PICKED_UP);
        assertThat(ShiprocketStatusMapper.map(45, null)).isEqualTo(CANCELLED);
        assertThat(ShiprocketStatusMapper.map(46, null)).isEqualTo(RETURNING);
        assertThat(ShiprocketStatusMapper.map(76, null)).isEqualTo(FAILED);
        assertThat(ShiprocketStatusMapper.map(78, null)).isEqualTo(RETURNING);
    }

    @Test
    void leavesFulfillmentOnlyStatesUnknownInsteadOfInventingDeliveryProgress() {
        assertThat(ShiprocketStatusMapper.map(26, "FULFILLED")).isEqualTo(UNKNOWN);
        assertThat(ShiprocketStatusMapper.map(43, "SELF FULFILLED")).isEqualTo(UNKNOWN);
        assertThat(ShiprocketStatusMapper.map(59, "BOX PACKING")).isEqualTo(UNKNOWN);
        assertThat(ShiprocketStatusMapper.map(null, "Fulfilled")).isEqualTo(UNKNOWN);
    }

    @Test
    void fallsBackToTextWithoutTurningUndeliveredIntoDelivered() {
        assertThat(ShiprocketStatusMapper.map(null, "Delivered")).isEqualTo(DELIVERED);
        assertThat(ShiprocketStatusMapper.map(null, "RTO Delivered")).isEqualTo(RETURNED);
        assertThat(ShiprocketStatusMapper.map(null, "Undelivered")).isEqualTo(DELAYED);
        assertThat(ShiprocketStatusMapper.map(null, "Out For Delivery")).isEqualTo(AT_DROPOFF);
        assertThat(ShiprocketStatusMapper.map(null, "Pickup Booked")).isEqualTo(COURIER_ASSIGNED);
        assertThat(ShiprocketStatusMapper.map(null, "In Transit")).isEqualTo(IN_TRANSIT);
        assertThat(ShiprocketStatusMapper.map(null, "Untraceable")).isEqualTo(FAILED);
        assertThat(ShiprocketStatusMapper.map(null, null)).isEqualTo(UNKNOWN);
    }
}
