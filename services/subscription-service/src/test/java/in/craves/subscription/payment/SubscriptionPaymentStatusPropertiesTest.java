package in.craves.subscription.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SubscriptionPaymentStatusPropertiesTest {
    @Test
    void consumerIsDisabledByDefault() {
        SubscriptionPaymentStatusProperties properties = new SubscriptionPaymentStatusProperties();
        properties.validate();
        assertThat(properties.isEnabled()).isFalse();
    }

    @Test
    void enabledConsumerRequiresServiceBus() {
        SubscriptionPaymentStatusProperties properties = new SubscriptionPaymentStatusProperties();
        properties.setEnabled(true);
        assertThatThrownBy(properties::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Service Bus configuration");
    }
}
