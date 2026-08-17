package in.craves.integration.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ShiprocketPropertiesTest {

    @Test
    void defaultsToReadOnlyAndCreateDisabled() {
        ShiprocketProperties properties = new ShiprocketProperties();

        properties.validate();

        assertThat(properties.isEnabled()).isFalse();
        assertThat(properties.isCreateEnabled()).isFalse();
        assertThat(properties.configuredEnvironment()).isEqualTo("READ_ONLY");
        assertThat(properties.executionMode()).isEqualTo("READ_ONLY");
        assertThat(properties.productionCreateReady()).isFalse();
    }

    @Test
    void legacySandboxIsReadOnlyAndNeverAuthorizesCreate() {
        ShiprocketProperties properties = credentialsReady();
        properties.setEnvironment("SANDBOX");
        properties.setCreateEnabled(true);
        properties.setProductionActivationApproved(true);
        properties.setAttributionApproved(true);
        properties.setWebhookToken("webhook-token");
        properties.setOrderEmail("orders@craves.in");
        setDimensions(properties);

        assertThat(properties.executionMode()).isEqualTo("READ_ONLY");
        assertThatThrownBy(properties::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("explicit PRODUCTION mode");
    }

    @Test
    void partialPackageDimensionsFailClosed() {
        ShiprocketProperties properties = new ShiprocketProperties();
        properties.setPackageLengthCm(new BigDecimal("20"));

        assertThatThrownBy(properties::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("must be configured together");
    }

    @Test
    void productionCreateRequiresAttributionApproval() {
        ShiprocketProperties properties = productionCreateCandidate();
        properties.setAttributionApproved(false);

        assertThatThrownBy(properties::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("SHIPROCKET_ATTRIBUTION_APPROVED");
    }

    @Test
    void productionCreateReadyOnlyWhenEveryGateIsSatisfied() {
        ShiprocketProperties properties = productionCreateCandidate();

        properties.validate();

        assertThat(properties.credentialReady()).isTrue();
        assertThat(properties.packageDimensionsReady()).isTrue();
        assertThat(properties.createPrerequisitesReady()).isTrue();
        assertThat(properties.productionCreateReady()).isTrue();
    }

    private static ShiprocketProperties credentialsReady() {
        ShiprocketProperties properties = new ShiprocketProperties();
        properties.setEnabled(true);
        properties.setEmail("api-user@example.invalid");
        properties.setPassword("test-password");
        return properties;
    }

    private static ShiprocketProperties productionCreateCandidate() {
        ShiprocketProperties properties = credentialsReady();
        properties.setEnvironment("PRODUCTION");
        properties.setCreateEnabled(true);
        properties.setProductionActivationApproved(true);
        properties.setAttributionApproved(true);
        properties.setWebhookToken("webhook-token");
        properties.setOrderEmail("orders@craves.in");
        setDimensions(properties);
        return properties;
    }

    private static void setDimensions(ShiprocketProperties properties) {
        properties.setPackageLengthCm(new BigDecimal("20"));
        properties.setPackageBreadthCm(new BigDecimal("15"));
        properties.setPackageHeightCm(new BigDecimal("10"));
    }
}
