package in.craves.integration.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BorzoProductionReadinessTest {
    @Test
    void productionCannotUseTestHost() {
        BorzoProperties properties = productionProperties();
        properties.setBaseUrl("https://robotapitest-in.borzodelivery.com/api/business/1.8");

        assertThatThrownBy(properties::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("test or sandbox host");
    }

    @Test
    void enabledProductionRequiresExplicitApproval() {
        BorzoProperties properties = productionProperties();
        properties.setProductionActivationApproved(false);

        assertThatThrownBy(properties::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("BORZO_PRODUCTION_ACTIVATION_APPROVED");
    }

    @Test
    void completeProductionConfigurationIsReady() {
        BorzoProperties properties = productionProperties();
        properties.validate();
        assertThat(properties.productionReady()).isTrue();
    }

    private static BorzoProperties productionProperties() {
        BorzoProperties properties = new BorzoProperties();
        properties.setEnvironment("PRODUCTION");
        properties.setProductionActivationApproved(true);
        properties.setEnabled(true);
        properties.setBaseUrl("https://robotapi-in.borzodelivery.com/api/business/1.8");
        properties.setAuthToken("secret-reference-value");
        properties.setCallbackSecret("callback-secret-reference-value");
        properties.setCallbackUrl("https://api.craves.in/api/v1/delivery/webhooks/borzo");
        return properties;
    }
}
