package in.craves.integration.delivery.shiprocket;

import static in.craves.integration.delivery.provider.DeliveryProviderAdapter.DeliveryStatus.IN_TRANSIT;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ShiprocketStatusIdSpaceTest {

    @Test
    void explicitStatusLabelWinsWhenCurrentStatusIdCollidesWithShipmentStatusCode() {
        // Shiprocket's webhook example can expose current_status_id=20 while the shipment itself
        // is IN TRANSIT (shipment_status_id=18). Treating 20 as a shipment code would mean
        // PICKUP EXCEPTION, so the explicit label must win.
        assertThat(ShiprocketStatusMapper.map(20, "IN TRANSIT")).isEqualTo(IN_TRANSIT);
    }
}
