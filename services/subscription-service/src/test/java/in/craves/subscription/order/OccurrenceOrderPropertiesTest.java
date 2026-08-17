package in.craves.subscription.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class OccurrenceOrderPropertiesTest {
    @Test
    void workersAreDisabledAndLeadIsUnsetByDefault() {
        OccurrenceOrderProperties properties = new OccurrenceOrderProperties();
        properties.validate();

        assertThat(properties.isRequestWorkerEnabled()).isFalse();
        assertThat(properties.isPublisherEnabled()).isFalse();
        assertThat(properties.getLeadHours()).isEqualTo(-1);
    }

    @Test
    void enabledRequestWorkerRequiresApprovedLead() {
        OccurrenceOrderProperties properties = new OccurrenceOrderProperties();
        properties.setRequestWorkerEnabled(true);

        assertThatThrownBy(properties::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("approved order dispatch leadHours");
    }
}
