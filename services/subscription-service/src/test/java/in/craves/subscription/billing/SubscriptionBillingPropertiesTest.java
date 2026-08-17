package in.craves.subscription.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SubscriptionBillingPropertiesTest {
    @Test
    void defaultsKeepGenerationAndPublishingDisabled() {
        SubscriptionBillingProperties properties = new SubscriptionBillingProperties();
        properties.validate();

        assertThat(properties.isGeneratorEnabled()).isFalse();
        assertThat(properties.isPublisherEnabled()).isFalse();
    }

    @Test
    void publisherRequiresServiceBusConfiguration() {
        SubscriptionBillingProperties properties = new SubscriptionBillingProperties();
        properties.setPublisherEnabled(true);

        assertThatThrownBy(properties::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Service Bus configuration");
    }
}
