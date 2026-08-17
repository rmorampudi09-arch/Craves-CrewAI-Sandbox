package in.craves.subscription.occurrence;

import in.craves.subscription.occurrence.OccurrenceRepository.ActiveSchedule;
import in.craves.subscription.occurrence.OccurrenceRepository.ClaimedSubscription;
import in.craves.subscription.occurrence.OccurrenceRepository.ScheduleItem;
import in.craves.subscription.occurrence.OccurrenceRepository.SkipRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OccurrenceGeneratorService {
    private static final Logger LOGGER = LoggerFactory.getLogger(OccurrenceGeneratorService.class);

    private final OccurrenceGeneratorProperties properties;
    private final OccurrenceRepository repository;
    private final Clock clock;

    @Autowired
    public OccurrenceGeneratorService(
        OccurrenceGeneratorProperties properties,
        OccurrenceRepository repository
    ) {
        this(properties, repository, Clock.systemUTC());
    }

    OccurrenceGeneratorService(
        OccurrenceGeneratorProperties properties,
        OccurrenceRepository repository,
        Clock clock
    ) {
        this.properties = properties;
        this.repository = repository;
        this.clock = clock;
    }

    public GenerationSummary generateDue() {
        int claimed = 0;
        int generated = 0;
        int skipped = 0;
        int advancedWithoutOccurrence = 0;
        int deferred = 0;
        int failed = 0;
        List<ClaimedSubscription> subscriptions = repository.claimDue(
            properties.getHorizonDays(),
            properties.getStaleLockMinutes(),
            properties.getBatchSize()
        );
        for (ClaimedSubscription subscription : subscriptions) {
            claimed++;
            try {
                ActiveSchedule schedule = repository.findActiveSchedule(subscription.planId())
                    .orElseThrow(() -> new IllegalStateException("Active plan schedule disappeared during generation"));
                ZoneId zone = ZoneId.of(schedule.timezone());
                List<ScheduleItem> matching = matchingItems(schedule, subscription.serviceDate());
                LocalDate next = nextMatchingDate(schedule, subscription.serviceDate());
                if (matching.isEmpty()) {
                    repository.releaseAndAdvance(subscription, next);
                    advancedWithoutOccurrence++;
                    continue;
                }
                if (subscription.customerIdentityId() == null || subscription.chefIdentityId() == null
                    || subscription.deliveryAddressId() == null) {
                    throw new IllegalStateException("Active subscription is missing required identity or delivery address");
                }

                SkipRequest skipRequest = repository.findRequestedSkip(
                    subscription.subscriptionId(), subscription.serviceDate()
                ).orElse(null);
                boolean deferredSlot = false;
                for (Map.Entry<SlotKey, List<ScheduleItem>> slot : groupBySlot(matching).entrySet()) {
                    Instant serviceAt = ZonedDateTime.of(
                        subscription.serviceDate(), slot.getKey().serviceTime(), zone
                    ).toInstant();
                    Instant generationOpensAt = serviceAt.minusSeconds(schedule.generationLeadHours() * 3600L);
                    if (clock.instant().isBefore(generationOpensAt)) {
                        deferredSlot = true;
                        continue;
                    }
                    UUID occurrenceId = repository.createOccurrence(
                        subscription,
                        schedule,
                        subscription.serviceDate(),
                        slot.getKey().mealSlotCode(),
                        serviceAt,
                        slot.getValue(),
                        skipRequest
                    );
                    if (occurrenceId != null) {
                        if (skipRequest == null) {
                            generated++;
                        } else {
                            skipped++;
                        }
                    }
                }
                if (deferredSlot) {
                    repository.releaseAndAdvance(subscription, subscription.serviceDate());
                    deferred++;
                } else {
                    repository.releaseAndAdvance(subscription, next);
                }
            } catch (RuntimeException exception) {
                failed++;
                repository.releaseAfterFailure(subscription);
                LOGGER.error(
                    "Subscription occurrence generation failed subscriptionId={} serviceDate={}",
                    subscription.subscriptionId(), subscription.serviceDate(), exception
                );
            }
        }
        return new GenerationSummary(claimed, generated, skipped, advancedWithoutOccurrence, deferred, failed);
    }

    static List<ScheduleItem> matchingItems(ActiveSchedule schedule, LocalDate date) {
        return schedule.items().stream()
            .filter(item -> matches(schedule.recurrenceType(), item, date))
            .sorted(Comparator
                .comparing(ScheduleItem::serviceTime)
                .thenComparing(ScheduleItem::mealSlotCode)
                .thenComparingInt(ScheduleItem::sequenceNumber))
            .toList();
    }

    static Map<SlotKey, List<ScheduleItem>> groupBySlot(List<ScheduleItem> items) {
        Map<SlotKey, List<ScheduleItem>> grouped = new LinkedHashMap<>();
        for (ScheduleItem item : items) {
            SlotKey key = new SlotKey(item.mealSlotCode(), item.serviceTime());
            grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }
        return grouped;
    }

    static LocalDate nextMatchingDate(ActiveSchedule schedule, LocalDate after) {
        int maxDays = "WEEKLY".equals(schedule.recurrenceType()) ? 14 : 62;
        for (int offset = 1; offset <= maxDays; offset++) {
            LocalDate candidate = after.plusDays(offset);
            if (schedule.items().stream().anyMatch(item -> matches(schedule.recurrenceType(), item, candidate))) {
                return candidate;
            }
        }
        throw new IllegalStateException("Active schedule contains no future service day within its recurrence window");
    }

    private static boolean matches(String recurrence, ScheduleItem item, LocalDate date) {
        if ("WEEKLY".equals(recurrence)) {
            return item.isoDayOfWeek() != null
                && item.isoDayOfWeek() == date.getDayOfWeek().getValue();
        }
        return item.dayOfMonth() != null && item.dayOfMonth() == date.getDayOfMonth();
    }

    record SlotKey(String mealSlotCode, LocalTime serviceTime) {
    }

    public record GenerationSummary(
        int claimed,
        int generated,
        int skipped,
        int advancedWithoutOccurrence,
        int deferred,
        int failed
    ) {
    }
}
