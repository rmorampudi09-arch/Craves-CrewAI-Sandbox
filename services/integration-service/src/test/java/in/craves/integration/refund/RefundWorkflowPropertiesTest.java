package in.craves.integration.refund;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RefundWorkflowPropertiesTest {
    @Test
    void allExecutionSwitchesDefaultToDisabled() {
        RefundWorkflowProperties properties = new RefundWorkflowProperties();

        assertThat(properties.isConsumerEnabled()).isFalse();
        assertThat(properties.isProviderExecutionEnabled()).isFalse();
        assertThat(properties.isReconciliationEnabled()).isFalse();
        assertThat(properties.isStatusPublisherEnabled()).isFalse();
        assertThat(properties.getSubscriptionName()).isEqualTo("integration-service-refund-requested");
    }
}
