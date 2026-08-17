package in.craves.subscription.schedule;

import static org.assertj.core.api.Assertions.assertThat;

import in.craves.subscription.schedule.PlanScheduleModels.PutScheduleRequest;
import in.craves.subscription.schedule.PlanScheduleModels.ScheduleItemRequest;
import jakarta.validation.Validation;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlanScheduleRequestValidationTest {
    @Test
    void rejectsEmptyScheduleItems() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            PutScheduleRequest request = new PutScheduleRequest(
                "WEEKLY", "Asia/Kolkata", 24, List.of()
            );
            assertThat(factory.getValidator().validate(request)).isNotEmpty();
        }
    }

    @Test
    void acceptsBoundedWeeklyMealSlotShape() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            PutScheduleRequest request = new PutScheduleRequest(
                "WEEKLY",
                "Asia/Kolkata",
                24,
                List.of(new ScheduleItemRequest(
                    UUID.randomUUID(), 1, 1, null, "LUNCH", LocalTime.of(12, 30), 1
                ))
            );
            assertThat(factory.getValidator().validate(request)).isEmpty();
        }
    }

    @Test
    void rejectsUnsafeMealSlotCode() {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            PutScheduleRequest request = new PutScheduleRequest(
                "MONTHLY",
                "Asia/Kolkata",
                48,
                List.of(new ScheduleItemRequest(
                    UUID.randomUUID(), 1, null, 15, "Lunch slot!", LocalTime.of(12, 30), 1
                ))
            );
            assertThat(factory.getValidator().validate(request)).isNotEmpty();
        }
    }
}
