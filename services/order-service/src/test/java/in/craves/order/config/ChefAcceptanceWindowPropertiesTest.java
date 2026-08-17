package in.craves.order.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ChefAcceptanceWindowPropertiesTest {
    @Test
    void usesApprovedThirtyMinuteWindowAndReminderSchedule() {
        ChefAcceptanceWindowProperties properties = new ChefAcceptanceWindowProperties();

        assertThat(properties.validatedTimeoutMinutes()).isEqualTo(30);
        assertThat(properties.validatedFirstReminderMinutes()).isEqualTo(10);
        assertThat(properties.validatedSecondReminderMinutes()).isEqualTo(20);
        assertThat(properties.isWorkerEnabled()).isFalse();
        assertThat(properties.validatedWorkerBatchSize()).isEqualTo(20);
    }

    @Test
    void boundsInvalidWorkerBatchAndRepairsInvalidReminderValues() {
        ChefAcceptanceWindowProperties properties = new ChefAcceptanceWindowProperties();
        properties.setTimeoutMinutes(30);
        properties.setFirstReminderMinutes(0);
        properties.setSecondReminderMinutes(60);
        properties.setWorkerBatchSize(1000);

        assertThat(properties.validatedFirstReminderMinutes()).isBetween(1, 29);
        assertThat(properties.validatedSecondReminderMinutes())
            .isGreaterThan(properties.validatedFirstReminderMinutes())
            .isLessThan(30);
        assertThat(properties.validatedWorkerBatchSize()).isEqualTo(100);
    }
}
