package in.craves.order.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class DeliveryStatusConsumerPropertiesTest {
    @Test
    void defaultsAreFailClosedAndBounded() {
        DeliveryStatusConsumerProperties properties = new DeliveryStatusConsumerProperties();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.getTopicName()).isEqualTo("craves-domain-events");
        assertThat(properties.getSubscriptionName())
            .isEqualTo("order-service-delivery-status-changed");
        assertThat(properties.validatedMaxConcurrentMessages()).isEqualTo(2);
        assertThat(properties.validatedPrefetchCount()).isEqualTo(4);
        assertThat(properties.validatedMaxDeliveryAttempts()).isEqualTo(5);
    }

    @Test
    void invalidRuntimeNumbersFallBackSafely() {
        DeliveryStatusConsumerProperties properties = new DeliveryStatusConsumerProperties();
        properties.setMaxConcurrentMessages(0);
        properties.setPrefetchCount(-4);
        properties.setMaxDeliveryAttempts(0);

        assertThat(properties.validatedMaxConcurrentMessages()).isEqualTo(2);
        assertThat(properties.validatedPrefetchCount()).isZero();
        assertThat(properties.validatedMaxDeliveryAttempts()).isEqualTo(5);
    }
}
