package in.craves.subscription.capacity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CapacityPropertiesTest {
    @Test
    void defaultsAreSafeForFirstDeployment() {
        CapacityProperties properties = new CapacityProperties();

        assertThat(properties.getHoldMinutes()).isEqualTo(15);
        assertThat(properties.getProjectionHorizonDays()).isEqualTo(180);
        assertThat(properties.isProjectionSchedulerEnabled()).isFalse();
        assertThat(properties.getProjectionBatchSize()).isEqualTo(50);
        assertThat(properties.getProjectionFixedDelayMs()).isEqualTo(60_000L);
    }

    @Test
    void rejectsUnboundedCapacityProjectionConfiguration() {
        CapacityProperties properties = new CapacityProperties();

        assertThatThrownBy(() -> properties.setHoldMinutes(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setProjectionHorizonDays(10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setProjectionBatchSize(1000)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> properties.setProjectionFixedDelayMs(1000L)).isInstanceOf(IllegalArgumentException.class);
    }
}
