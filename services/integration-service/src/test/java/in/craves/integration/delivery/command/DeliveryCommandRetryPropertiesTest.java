package in.craves.integration.delivery.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class DeliveryCommandRetryPropertiesTest {

    @Test
    void backsOffExponentiallyAndCapsAtConfiguredMaximum() {
        DeliveryCommandRetryProperties properties = new DeliveryCommandRetryProperties();
        properties.setBaseSeconds(30);
        properties.setMaxSeconds(600);

        assertThat(properties.delay(1)).isEqualTo(Duration.ofSeconds(30));
        assertThat(properties.delay(2)).isEqualTo(Duration.ofSeconds(60));
        assertThat(properties.delay(3)).isEqualTo(Duration.ofSeconds(120));
        assertThat(properties.delay(4)).isEqualTo(Duration.ofSeconds(240));
        assertThat(properties.delay(5)).isEqualTo(Duration.ofSeconds(480));
        assertThat(properties.delay(6)).isEqualTo(Duration.ofSeconds(600));
        assertThat(properties.delay(20)).isEqualTo(Duration.ofSeconds(600));
    }
}
