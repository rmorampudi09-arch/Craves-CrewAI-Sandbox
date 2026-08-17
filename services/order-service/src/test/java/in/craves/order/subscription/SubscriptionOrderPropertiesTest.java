package in.craves.order.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SubscriptionOrderPropertiesTest {
    @Test
    void consumerAndCallbackAreDisabledByDefault() {
        SubscriptionOrderProperties properties = new SubscriptionOrderProperties();
        properties.validate();

        assertThat(properties.isConsumerEnabled()).isFalse();
        assertThat(properties.isCallbackWorkerEnabled()).isFalse();
    }

    @Test
    void callbackRequiresHttpsAndInternalCredential() {
        SubscriptionOrderProperties properties = new SubscriptionOrderProperties();
        properties.setCallbackWorkerEnabled(true);

        assertThatThrownBy(properties::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("HTTPS Subscription Service URL");
    }
}
