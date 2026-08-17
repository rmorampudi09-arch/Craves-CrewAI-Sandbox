package in.craves.integration.delivery.borzo;

import static in.craves.integration.delivery.provider.DeliveryProviderAdapter.DeliveryStatus.AT_PICKUP;
import static in.craves.integration.delivery.provider.DeliveryProviderAdapter.DeliveryStatus.COURIER_ASSIGNED;
import static in.craves.integration.delivery.provider.DeliveryProviderAdapter.DeliveryStatus.DELIVERED;
import static in.craves.integration.delivery.provider.DeliveryProviderAdapter.DeliveryStatus.RETURNING;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

class BorzoStatusMapperTest {
    private final BorzoStatusMapper mapper = new BorzoStatusMapper();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void mapsDetailedDeliveryStatuses() {
        assertThat(mapper.fromDeliveryStatus("courier_assigned")).isEqualTo(COURIER_ASSIGNED);
        assertThat(mapper.fromDeliveryStatus("courier_at_pickup")).isEqualTo(AT_PICKUP);
        assertThat(mapper.fromDeliveryStatus("finished")).isEqualTo(DELIVERED);
        assertThat(mapper.fromDeliveryStatus("return_courier_departed")).isEqualTo(RETURNING);
    }

    @Test
    void prefersDestinationDeliveryStatusOverOrderStatus() throws Exception {
        var order = objectMapper.readTree("""
            {
              "status": "active",
              "points": [
                {"delivery": null},
                {"delivery": {"status": "finished"}}
              ]
            }
            """);

        assertThat(mapper.fromOrder(order)).isEqualTo(DELIVERED);
    }
}
