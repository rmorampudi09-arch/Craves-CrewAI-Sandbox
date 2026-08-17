package in.craves.integration.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PaymentProviderPropertiesTest {

    @Test
    void sandboxEnvironmentUsesSandboxEndpointAndSimulationStatus() {
        PaymentProviderProperties properties = new PaymentProviderProperties(
            "sandbox",
            false,
            false,
            "2025-01-01",
            "client-id",
            "client-secret",
            "https://sandbox.cashfree.com",
            "https://api.cashfree.com",
            "https://craves.in/payment/return",
            "https://api.craves.in/webhooks/cashfree",
            "PENDING",
            300,
            "2025-01-01"
        );

        properties.validate();
        assertThat(properties.sandbox()).isTrue();
        assertThat(properties.baseUrl()).isEqualTo("https://sandbox.cashfree.com");
        assertThat(properties.sandboxRefundSimulationStatus()).isEqualTo("PENDING");
        assertThat(properties.paymentExecutionAllowed()).isTrue();
    }

    @Test
    void productionEnvironmentUsesProductionEndpointButExecutionCanStayDisabled() {
        PaymentProviderProperties properties = new PaymentProviderProperties(
            "production",
            true,
            false,
            "2025-01-01",
            "client-id",
            "client-secret",
            "https://sandbox.cashfree.com",
            "https://api.cashfree.com",
            "https://craves.in/payment/return",
            "https://api.craves.in/webhooks/cashfree",
            "",
            300,
            "2025-01-01"
        );

        properties.validate();
        assertThat(properties.sandbox()).isFalse();
        assertThat(properties.baseUrl()).isEqualTo("https://api.cashfree.com");
        assertThat(properties.paymentExecutionAllowed()).isFalse();
    }
}
