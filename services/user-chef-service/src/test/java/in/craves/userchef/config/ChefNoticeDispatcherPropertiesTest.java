package in.craves.userchef.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChefNoticeDispatcherPropertiesTest {
    @Test
    void directDispatchIsDisabledByDefault() {
        assertThat(new ChefNoticeDispatcherProperties().isDirectDispatchEnabled()).isFalse();
    }
}
