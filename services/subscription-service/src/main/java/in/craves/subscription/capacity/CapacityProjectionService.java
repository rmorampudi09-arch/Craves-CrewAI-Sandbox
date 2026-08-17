package in.craves.subscription.capacity;

import in.craves.subscription.capacity.CapacityRepository.DateOverrideRow;
import in.craves.subscription.capacity.CapacityRepository.EntitlementRow;
import in.craves.subscription.capacity.CapacityRepository.MenuDateOverrideRow;
import in.craves.subscription.capacity.CapacityRepository.SlotRuleRow;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CapacityProjectionService {
    private final JdbcTemplate jdbcTemplate;
    private final CapacityRepository repository;
    private final CapacityProperties properties;
    private final Clock clock;

    @Autowired
    public CapacityProjectionService(
        JdbcTemplate jdbcTemplate,
        CapacityRepository repository,
        CapacityProperties properties
    ) {
        this(jdbcTemplate, repository, properties, Clock.systemUTC());
    }

    CapacityProjectionService(
        JdbcTemplate jdbcTemplate,
        CapacityRepository repository,
        CapacityProperties properties,
        Clock clock
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.repository = repository;
        this.properties = properties;
        this.clock = clock;
    }

    /**
     * Claims only subscriptions whose concrete projection is behind the configured horizon.
     * FOR UPDATE SKIP LOCKED lets multiple service replicas divide work without duplicate claims.
     */
    @Transactional
    public ProjectionSummary projectLaggingBatch() {
        repository.expireHolds(clock.instant());
        LocalDate today = LocalDate.now(clock);
        LocalDate targetThrough = today.plusDays(properties.getProjectionHorizonDays());
        List<Candidate> candidates = jdbcTemplate.query(
            "SELECT cs.id, cs.chef_identity_id, cs.start_date, projection.max_date " +
                "FROM subscription_schema.customer_subscription cs " +
                "LEFT JOIN LATERAL (" +
                "  SELECT MAX(a.service_date) AS max_date " +
                "  FROM subscription_schema.subscription_capacity_allocation a " +
                "  WHERE a.subscription_id = cs.id AND a.status IN ('COMMITTED','MATERIALIZED')" +
                ") projection ON true " +
                "WHERE cs.status = 'ACTIVE' AND cs.chef_identity_id IS NOT NULL " +
                "AND EXISTS (SELECT 1 FROM subscription_schema.subscription_capacity_entitlement e " +
                "            WHERE e.subscription_id = cs.id AND e.status = 'COMMITTED') " +
                "AND COALESCE(projection.max_date, cs.start_date - 1) < ? " +
                "ORDER BY projection.max_date NULLS FIRST, cs.updated_at, cs.id " +
                "FOR UPDATE OF cs SKIP LOCKED LIMIT ?",
            (rs, rowNum) -> new Candidate(
                rs.getObject("id", UUID.class),
                rs.getObject("chef_identity_id", UUID.class),
                rs.getObject("start_date", LocalDate.class),
                rs.getObject("max_date", LocalDate.class)
            ),
            targetThrough,
            properties.getProjectionBatchSize()
        );

        int projectedSubscriptions = 0;
        int projectedDates = 0;
        int incidents = 0;
        for (Candidate candidate : candidates) {
            repository.lockChef(candidate.chefIdentityId());
            List<EntitlementRow> entitlements = repository.listActiveEntitlements(candidate.subscriptionId()).stream()
                .filter(value -> "COMMITTED".equals(value.status()))
                .toList();
            if (entitlements.isEmpty()) {
                continue;
            }
            LocalDate from = candidate.maxDate() == null
                ? candidate.startDate()
                : candidate.maxDate().plusDays(1);
            if (from.isBefore(today)) {
                from = today;
            }
            for (LocalDate date = from; !date.isAfter(targetThrough); date = date.plusDays(1)) {
                LocalDate projectionDate = date;
                List<EntitlementRow> matching = entitlements.stream()
                    .filter(value -> matches(value, projectionDate))
                    .toList();
                if (matching.isEmpty()) {
                    continue;
                }
                for (EntitlementRow entitlement : matching) {
                    repository.upsertAllocation(
                        candidate.subscriptionId(),
                        candidate.chefIdentityId(),
                        date,
                        entitlement.mealSlotCode(),
                        entitlement.menuItemId(),
                        entitlement.units(),
                        "COMMITTED",
                        null
                    );
                }
                incidents += reconcileProtectedDate(candidate.chefIdentityId(), date, matching);
                projectedDates++;
            }
            projectedSubscriptions++;
        }
        return new ProjectionSummary(candidates.size(), projectedSubscriptions, projectedDates, incidents);
    }

    private int reconcileProtectedDate(UUID chefIdentityId, LocalDate date, List<EntitlementRow> matching) {
        int incidentCount = 0;
        Set<String> slots = new HashSet<>();
        Set<ItemSlot> items = new HashSet<>();
        for (EntitlementRow entitlement : matching) {
            slots.add(entitlement.mealSlotCode());
            items.add(new ItemSlot(entitlement.menuItemId(), entitlement.mealSlotCode()));
        }

        for (String slot : slots) {
            Optional<DateOverrideRow> override = repository.findDateOverride(chefIdentityId, date, slot);
            SlotCapacity effective;
            if (override.isPresent()) {
                DateOverrideRow value = override.get();
                effective = new SlotCapacity(
                    value.totalCapacityUnits(),
                    value.closed() ? 0 : value.subscriptionCapacityUnits(),
                    value.closed(),
                    "OVERRIDE",
                    1
                );
            } else {
                Optional<SlotRuleRow> rule = repository.findSlotRule(
                    chefIdentityId, date.getDayOfWeek().getValue(), slot
                );
                if (rule.isEmpty()) {
                    int reserved = repository.currentDateSlotUnits(chefIdentityId, date, slot);
                    repository.openOrUpdateIncident(
                        chefIdentityId, date, null, slot, null, "PROJECTION_FAILURE", "P2",
                        reserved, 0,
                        "Existing subscription commitment has no chef capacity rule for this service date; commitment is preserved and new sales remain fail-closed"
                    );
                    incidentCount++;
                    continue;
                }
                SlotRuleRow value = rule.get();
                effective = new SlotCapacity(
                    value.totalCapacityUnits(),
                    value.salesEnabled() ? value.subscriptionCapacityUnits() : 0,
                    !value.salesEnabled(),
                    "RULE",
                    value.version()
                );
            }

            repository.resolveIncident(chefIdentityId, date, null, slot, null, "PROJECTION_FAILURE");
            int reserved = repository.currentDateSlotUnits(chefIdentityId, date, slot);
            repository.upsertBucket(
                chefIdentityId, date, slot, effective.totalCapacityUnits(), effective.subscriptionCapacityUnits(),
                effective.closed(), effective.source(), effective.version()
            );
            if (reserved > effective.subscriptionCapacityUnits()) {
                repository.openOrUpdateIncident(
                    chefIdentityId, date, null, slot, null, "DATE_DEFICIT", "P2",
                    reserved, effective.subscriptionCapacityUnits(),
                    "Existing subscription commitments exceed current chef date/slot capacity; existing customers remain protected and new sales are blocked"
                );
                incidentCount++;
            } else {
                repository.resolveIncident(chefIdentityId, date, null, slot, null, "DATE_DEFICIT");
            }
        }

        for (ItemSlot item : items) {
            Optional<MenuDateOverrideRow> override = repository.findMenuDateOverride(
                chefIdentityId, item.menuItemId(), date, item.mealSlotCode()
            );
            Optional<ItemCapacity> effective;
            if (override.isPresent()) {
                MenuDateOverrideRow value = override.get();
                effective = Optional.of(new ItemCapacity(
                    value.closed() ? 0 : value.maxSubscriptionUnits(), value.closed(), "OVERRIDE", 1
                ));
            } else {
                effective = repository.findMenuRule(
                    chefIdentityId, item.menuItemId(), date.getDayOfWeek().getValue(), item.mealSlotCode()
                ).map(value -> new ItemCapacity(
                    value.salesEnabled() ? value.maxSubscriptionUnits() : 0,
                    !value.salesEnabled(),
                    "RULE",
                    value.version()
                ));
            }
            if (effective.isEmpty()) {
                continue;
            }
            int reserved = repository.currentDateItemUnits(
                chefIdentityId, item.menuItemId(), date, item.mealSlotCode()
            );
            ItemCapacity value = effective.get();
            repository.upsertMenuBucket(
                chefIdentityId, item.menuItemId(), date, item.mealSlotCode(),
                value.maxSubscriptionUnits(), value.closed(), value.source(), value.version()
            );
            if (reserved > value.maxSubscriptionUnits()) {
                repository.openOrUpdateIncident(
                    chefIdentityId, date, null, item.mealSlotCode(), item.menuItemId(), "ITEM_DEFICIT", "P2",
                    reserved, value.maxSubscriptionUnits(),
                    "Existing subscription commitments exceed current menu-item capacity; existing customers remain protected"
                );
                incidentCount++;
            } else {
                repository.resolveIncident(
                    chefIdentityId, date, null, item.mealSlotCode(), item.menuItemId(), "ITEM_DEFICIT"
                );
            }
        }
        return incidentCount;
    }

    private static boolean matches(EntitlementRow value, LocalDate date) {
        if ("WEEKLY".equals(value.recurrenceType())) {
            return value.isoDayOfWeek() != null && value.isoDayOfWeek() == date.getDayOfWeek().getValue();
        }
        return value.dayOfMonth() != null && value.dayOfMonth() == date.getDayOfMonth();
    }

    private record Candidate(
        UUID subscriptionId,
        UUID chefIdentityId,
        LocalDate startDate,
        LocalDate maxDate
    ) {}

    private record ItemSlot(UUID menuItemId, String mealSlotCode) {}
    private record SlotCapacity(
        int totalCapacityUnits,
        int subscriptionCapacityUnits,
        boolean closed,
        String source,
        int version
    ) {}
    private record ItemCapacity(
        int maxSubscriptionUnits,
        boolean closed,
        String source,
        int version
    ) {}

    public record ProjectionSummary(
        int claimedSubscriptions,
        int projectedSubscriptions,
        int projectedDates,
        int incidentsRaised
    ) {}
}
