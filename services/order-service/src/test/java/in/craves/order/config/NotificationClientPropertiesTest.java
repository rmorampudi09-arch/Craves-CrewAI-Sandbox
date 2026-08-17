package in.craves.order.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NotificationClientPropertiesTest {
    @Test
    void directDispatchIsDisabledByDefault() {
        assertThat(new NotificationClientProperties().isDirectDispatchEnabled()).isFalse();
    }
}
