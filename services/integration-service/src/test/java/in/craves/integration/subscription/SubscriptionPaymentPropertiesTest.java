package in.craves.integration.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SubscriptionPaymentPropertiesTest {
    @Test
    void defaultsDisableConsumerAndPublisher() {
        SubscriptionPaymentProperties properties = new SubscriptionPaymentProperties();
        properties.validate();

        assertThat(properties.isConsumerEnabled()).isFalse();
        assertThat(properties.isStatusPublisherEnabled()).isFalse();
    }

    @Test
    void enabledConsumerRequiresMessagingConfiguration() {
        SubscriptionPaymentProperties properties = new SubscriptionPaymentProperties();
        properties.setConsumerEnabled(true);

        assertThatThrownBy(properties::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Service Bus configuration");
    }
}
