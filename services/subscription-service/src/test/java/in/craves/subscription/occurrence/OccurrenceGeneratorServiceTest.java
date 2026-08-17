package in.craves.subscription.occurrence;

import static org.assertj.core.api.Assertions.assertThat;

import in.craves.subscription.occurrence.OccurrenceGeneratorService.SlotKey;
import in.craves.subscription.occurrence.OccurrenceRepository.ActiveSchedule;
import in.craves.subscription.occurrence.OccurrenceRepository.ScheduleItem;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OccurrenceGeneratorServiceTest {
    @Test
    void selectsWeeklyItemsAndNextDate() {
        ScheduleItem monday = new ScheduleItem(UUID.randomUUID(), 1, 1, null, "LUNCH", LocalTime.NOON, 1);
        ScheduleItem wednesday = new ScheduleItem(UUID.randomUUID(), 2, 3, null, "LUNCH", LocalTime.NOON, 1);
        ActiveSchedule schedule = new ActiveSchedule(
            UUID.randomUUID(), "WEEKLY", "Asia/Kolkata", LocalTime.NOON, 24, 1, List.of(monday, wednesday)
        );
        LocalDate mondayDate = LocalDate.of(2026, 8, 3);

        assertThat(OccurrenceGeneratorService.matchingItems(schedule, mondayDate)).containsExactly(monday);
        assertThat(OccurrenceGeneratorService.nextMatchingDate(schedule, mondayDate))
            .isEqualTo(LocalDate.of(2026, 8, 5));
    }

    @Test
    void selectsMonthlyDayAndRollsAcrossMonth() {
        ScheduleItem item = new ScheduleItem(UUID.randomUUID(), 1, null, 5, "DINNER", LocalTime.of(19, 30), 1);
        ActiveSchedule schedule = new ActiveSchedule(
            UUID.randomUUID(), "MONTHLY", "Asia/Kolkata", LocalTime.of(19, 30), 48, 1, List.of(item)
        );
        LocalDate serviceDate = LocalDate.of(2026, 8, 5);

        assertThat(OccurrenceGeneratorService.matchingItems(schedule, serviceDate)).containsExactly(item);
        assertThat(OccurrenceGeneratorService.nextMatchingDate(schedule, serviceDate))
            .isEqualTo(LocalDate.of(2026, 9, 5));
    }

    @Test
    void groupsSameServiceDayIntoIndependentMealSlots() {
        ScheduleItem lunchMain = new ScheduleItem(UUID.randomUUID(), 1, 1, null, "LUNCH", LocalTime.of(12, 30), 1);
        ScheduleItem lunchSide = new ScheduleItem(UUID.randomUUID(), 1, 1, null, "LUNCH", LocalTime.of(12, 30), 2);
        ScheduleItem dinner = new ScheduleItem(UUID.randomUUID(), 1, 1, null, "DINNER", LocalTime.of(19, 30), 1);

        Map<SlotKey, List<ScheduleItem>> grouped = OccurrenceGeneratorService.groupBySlot(
            List.of(lunchMain, lunchSide, dinner)
        );

        assertThat(grouped).hasSize(2);
        assertThat(grouped.get(new SlotKey("LUNCH", LocalTime.of(12, 30))))
            .containsExactly(lunchMain, lunchSide);
        assertThat(grouped.get(new SlotKey("DINNER", LocalTime.of(19, 30))))
            .containsExactly(dinner);
    }
}
