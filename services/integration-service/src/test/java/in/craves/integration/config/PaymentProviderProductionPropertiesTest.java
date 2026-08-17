package in.craves.integration.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PaymentProviderProductionPropertiesTest {
    @Test
    void productionRequiresExplicitApproval() {
        PaymentProviderProperties properties = properties("production", false, false, "", "");
        assertThatThrownBy(properties::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("CRAVES_CASHFREE_PRODUCTION_ACTIVATION_APPROVED");
    }

    @Test
    void completeProductionConfigurationCanRemainExecutionDisabled() {
        PaymentProviderProperties properties = properties("production", true, false, "client", "secret");
        properties.validate();
        assertThat(properties.productionReady()).isTrue();
        assertThat(properties.paymentExecutionAllowed()).isFalse();
        assertThat(properties.allowedWebhookVersions()).contains("2025-01-01");
    }

    @Test
    void approvedProductionExecutionIsAllowed() {
        PaymentProviderProperties properties = properties("production", true, true, "client", "secret");
        properties.validate();
        assertThat(properties.paymentExecutionAllowed()).isTrue();
    }

    private static PaymentProviderProperties properties(
        String environment,
        boolean approved,
        boolean executionEnabled,
        String clientId,
        String clientKey
    ) {
        return new PaymentProviderProperties(
            environment,
            approved,
            executionEnabled,
            "2025-01-01",
            clientId,
            clientKey,
            "https://sandbox.cashfree.com",
            "https://api.cashfree.com",
            "https://craves.in/payment/return",
            "https://api.craves.in/api/v1/payments/webhooks/cashfree",
            "",
            300,
            "2025-01-01,2023-08-01"
        );
    }
}
