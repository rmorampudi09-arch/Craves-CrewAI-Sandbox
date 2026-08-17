package in.craves.order.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class DeliveryStatusResponsePrivacyTest {
    @Test
    void customerResponseDoesNotExposeProviderTransactionOrRawPayload() {
        var componentNames = Arrays.stream(
            DeliveryStatusDtos.DeliveryStatusResponse.class.getRecordComponents()
        ).map(component -> component.getName()).toList();

        assertThat(componentNames)
            .contains("orderId", "deliveryJobId", "providerId", "status", "trackingUrl")
            .doesNotContain(
                "providerDeliveryId",
                "providerOrderId",
                "rawPayload",
                "payload",
                "signatureHash"
            );
    }
}
