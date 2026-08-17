package in.craves.notification.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NotificationDeliveryPropertiesTest {
    @Test
    void allExternalDeliveryIsDisabledByDefault() {
        NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
        properties.validate();

        assertThat(properties.isWorkerEnabled()).isFalse();
        assertThat(properties.isPushEnabled()).isFalse();
        assertThat(properties.isEmailEnabled()).isFalse();
    }

    @Test
    void pushRequiresFirebaseCredentialSecret() {
        NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
        properties.setPushEnabled(true);

        assertThatThrownBy(properties::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Firebase service account secret");
    }

    @Test
    void workerRequiresAtLeastOneProvider() {
        NotificationDeliveryProperties properties = new NotificationDeliveryProperties();
        properties.setWorkerEnabled(true);

        assertThatThrownBy(properties::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("At least one provider channel");
    }
}
