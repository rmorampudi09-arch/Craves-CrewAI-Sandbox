package in.craves.integration.refund;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RefundProductionApprovalDefaultsTest {
    @Test
    void productionApprovalsAndWorkersDefaultToFalse() {
        RefundWorkflowProperties properties = new RefundWorkflowProperties();
        assertThat(properties.isProviderExecutionEnabled()).isFalse();
        assertThat(properties.isReconciliationEnabled()).isFalse();
        assertThat(properties.isProductionProviderExecutionApproved()).isFalse();
        assertThat(properties.isProductionReconciliationApproved()).isFalse();
    }
}
