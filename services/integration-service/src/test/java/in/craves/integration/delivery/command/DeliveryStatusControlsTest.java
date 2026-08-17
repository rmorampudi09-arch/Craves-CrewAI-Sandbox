package in.craves.integration.delivery.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class DeliveryStatusControlsTest {

    @Test
    void allDeliveryExecutionAndStatusWorkersAreDisabledByDefault() {
        DeliveryCommandProperties properties = new DeliveryCommandProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.isReconciliationEnabled()).isFalse();
        assertThat(properties.isWebhookProcessingEnabled()).isFalse();
        assertThat(properties.isTrackingReconciliationEnabled()).isFalse();
        assertThat(properties.isStatusPublisherEnabled()).isFalse();
        properties.validate();
    }

    @Test
    void statusPublisherRequiresServiceBusConfiguration() {
        DeliveryCommandProperties properties = new DeliveryCommandProperties();
        properties.setStatusPublisherEnabled(true);

        assertThatThrownBy(properties::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SERVICE_BUS_FULLY_QUALIFIED_NAMESPACE")
            .hasMessageContaining("delivery messaging is enabled");
    }
}
