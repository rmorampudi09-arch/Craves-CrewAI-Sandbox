package in.craves.subscription.capacity;

import in.craves.subscription.capacity.CapacityModels.CapacityIncidentPage;
import in.craves.subscription.capacity.CapacityModels.CapacityIncidentResponse;
import in.craves.subscription.capacity.CapacityModels.DateOverrideResponse;
import in.craves.subscription.capacity.CapacityModels.MenuItemDateOverrideResponse;
import in.craves.subscription.capacity.CapacityModels.MenuItemRuleResponse;
import in.craves.subscription.capacity.CapacityModels.SlotRuleResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CapacityRepository {
    private static final String ACTIVE_ENTITLEMENT =
        "(status = 'COMMITTED' OR (status = 'HOLD' AND hold_expires_at > now()))";
    private static final String ACTIVE_ALLOCATION =
        "(status IN ('COMMITTED','MATERIALIZED') OR (status = 'HOLD' AND hold_expires_at > now()))";

    private final JdbcTemplate jdbcTemplate;

    public CapacityRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Serializes all capacity admission/configuration work for one chef. */
    public CapacityControl lockChef(UUID chefIdentityId) {
        jdbcTemplate.update(
            "INSERT INTO subscription_schema.chef_capacity_control (chef_identity_id) VALUES (?) " +
                "ON CONFLICT (chef_identity_id) DO NOTHING",
            chefIdentityId
        );
        return jdbcTemplate.query(
            "SELECT chef_identity_id, admin_sales_frozen, freeze_reason FROM subscription_schema.chef_capacity_control " +
                "WHERE chef_identity_id = ? FOR UPDATE",
            (rs, rowNum) -> new CapacityControl(
                rs.getObject("chef_identity_id", UUID.class),
                rs.getBoolean("admin_sales_frozen"),
                rs.getString("freeze_reason")
            ),
            chefIdentityId
        ).stream().findFirst().orElseThrow();
    }

    public CapacityControl getControl(UUID chefIdentityId) {
        jdbcTemplate.update(
            "INSERT INTO subscription_schema.chef_capacity_control (chef_identity_id) VALUES (?) " +
                "ON CONFLICT (chef_identity_id) DO NOTHING",
            chefIdentityId
        );
        return jdbcTemplate.query(
            "SELECT chef_identity_id, admin_sales_frozen, freeze_reason FROM subscription_schema.chef_capacity_control WHERE chef_identity_id = ?",
            (rs, rowNum) -> new CapacityControl(
                rs.getObject("chef_identity_id", UUID.class),
                rs.getBoolean("admin_sales_frozen"),
                rs.getString("freeze_reason")
            ), chefIdentityId
        ).stream().findFirst().orElseThrow();
    }

    public void setFrozen(UUID chefIdentityId, boolean frozen, String reason, UUID actorIdentityId) {
        if (frozen) {
            jdbcTemplate.update(
                "INSERT INTO subscription_schema.chef_capacity_control " +
                    "(chef_identity_id, admin_sales_frozen, freeze_reason, frozen_by_identity_id, frozen_at, updated_at) " +
                    "VALUES (?, true, ?, ?, now(), now()) " +
                    "ON CONFLICT (chef_identity_id) DO UPDATE SET admin_sales_frozen = true, freeze_reason = EXCLUDED.freeze_reason, " +
                    "frozen_by_identity_id = EXCLUDED.frozen_by_identity_id, frozen_at = now(), updated_at = now()",
                chefIdentityId, reason, actorIdentityId
            );
        } else {
            jdbcTemplate.update(
                "INSERT INTO subscription_schema.chef_capacity_control (chef_identity_id, admin_sales_frozen, updated_at) " +
                    "VALUES (?, false, now()) " +
                    "ON CONFLICT (chef_identity_id) DO UPDATE SET admin_sales_frozen = false, freeze_reason = NULL, " +
                    "frozen_by_identity_id = NULL, frozen_at = NULL, updated_at = now()",
                chefIdentityId
            );
        }
    }

    public SlotRuleRow upsertSlotRule(
        UUID chefIdentityId, int isoDayOfWeek, String mealSlotCode, int totalCapacityUnits,
        int subscriptionCapacityUnits, boolean salesEnabled, UUID actorIdentityId
    ) {
        UUID id = UUID.randomUUID();
        return jdbcTemplate.query(
            "INSERT INTO subscription_schema.chef_capacity_rule " +
                "(id, chef_identity_id, iso_day_of_week, meal_slot_code, total_capacity_units, subscription_capacity_units, " +
                "sales_enabled, version, updated_by_identity_id, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, now(), now()) " +
                "ON CONFLICT (chef_identity_id, iso_day_of_week, meal_slot_code) DO UPDATE SET " +
                "total_capacity_units = EXCLUDED.total_capacity_units, subscription_capacity_units = EXCLUDED.subscription_capacity_units, " +
                "sales_enabled = EXCLUDED.sales_enabled, version = subscription_schema.chef_capacity_rule.version + 1, " +
                "updated_by_identity_id = EXCLUDED.updated_by_identity_id, updated_at = now() " +
                "RETURNING *",
            (rs, rowNum) -> mapSlotRuleRow(rs),
            id, chefIdentityId, isoDayOfWeek, mealSlotCode, totalCapacityUnits,
            subscriptionCapacityUnits, salesEnabled, actorIdentityId
        ).stream().findFirst().orElseThrow();
    }

    public Optional<SlotRuleRow> findSlotRule(UUID chefIdentityId, int isoDayOfWeek, String mealSlotCode) {
        return jdbcTemplate.query(
            "SELECT * FROM subscription_schema.chef_capacity_rule WHERE chef_identity_id = ? AND iso_day_of_week = ? AND meal_slot_code = ?",
            (rs, rowNum) -> mapSlotRuleRow(rs), chefIdentityId, isoDayOfWeek, mealSlotCode
        ).stream().findFirst();
    }

    public List<SlotRuleRow> listSlotRuleRows(UUID chefIdentityId) {
        return jdbcTemplate.query(
            "SELECT * FROM subscription_schema.chef_capacity_rule WHERE chef_identity_id = ? ORDER BY iso_day_of_week, meal_slot_code",
            (rs, rowNum) -> mapSlotRuleRow(rs), chefIdentityId
        );
    }

    public MenuRuleRow upsertMenuRule(
        UUID chefIdentityId, UUID menuItemId, int isoDayOfWeek, String mealSlotCode,
        int maxSubscriptionUnits, boolean salesEnabled, UUID actorIdentityId
    ) {
        UUID id = UUID.randomUUID();
        return jdbcTemplate.query(
            "INSERT INTO subscription_schema.chef_menu_item_capacity_rule " +
                "(id, chef_identity_id, menu_item_id, iso_day_of_week, meal_slot_code, max_subscription_units, sales_enabled, " +
                "version, updated_by_identity_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, 1, ?, now(), now()) " +
                "ON CONFLICT (chef_identity_id, menu_item_id, iso_day_of_week, meal_slot_code) DO UPDATE SET " +
                "max_subscription_units = EXCLUDED.max_subscription_units, sales_enabled = EXCLUDED.sales_enabled, " +
                "version = subscription_schema.chef_menu_item_capacity_rule.version + 1, updated_by_identity_id = EXCLUDED.updated_by_identity_id, " +
                "updated_at = now() RETURNING *",
            (rs, rowNum) -> mapMenuRuleRow(rs),
            id, chefIdentityId, menuItemId, isoDayOfWeek, mealSlotCode, maxSubscriptionUnits, salesEnabled, actorIdentityId
        ).stream().findFirst().orElseThrow();
    }

    public Optional<MenuRuleRow> findMenuRule(UUID chefIdentityId, UUID menuItemId, int isoDayOfWeek, String mealSlotCode) {
        return jdbcTemplate.query(
            "SELECT * FROM subscription_schema.chef_menu_item_capacity_rule WHERE chef_identity_id = ? AND menu_item_id = ? " +
                "AND iso_day_of_week = ? AND meal_slot_code = ?",
            (rs, rowNum) -> mapMenuRuleRow(rs), chefIdentityId, menuItemId, isoDayOfWeek, mealSlotCode
        ).stream().findFirst();
    }

    public List<MenuRuleRow> listMenuRuleRows(UUID chefIdentityId) {
        return jdbcTemplate.query(
            "SELECT * FROM subscription_schema.chef_menu_item_capacity_rule WHERE chef_identity_id = ? " +
                "ORDER BY iso_day_of_week, meal_slot_code, menu_item_id",
            (rs, rowNum) -> mapMenuRuleRow(rs), chefIdentityId
        );
    }

    public DateOverrideRow upsertDateOverride(
        UUID chefIdentityId, LocalDate serviceDate, String mealSlotCode, int totalCapacityUnits,
        int subscriptionCapacityUnits, boolean closed, String reason, UUID actorIdentityId
    ) {
        UUID id = UUID.randomUUID();
        return jdbcTemplate.query(
            "INSERT INTO subscription_schema.chef_capacity_override " +
                "(id, chef_identity_id, service_date, meal_slot_code, total_capacity_units, subscription_capacity_units, closed, reason, " +
                "updated_by_identity_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now()) " +
                "ON CONFLICT (chef_identity_id, service_date, meal_slot_code) DO UPDATE SET total_capacity_units = EXCLUDED.total_capacity_units, " +
                "subscription_capacity_units = EXCLUDED.subscription_capacity_units, closed = EXCLUDED.closed, reason = EXCLUDED.reason, " +
                "updated_by_identity_id = EXCLUDED.updated_by_identity_id, updated_at = now() RETURNING *",
            (rs, rowNum) -> mapDateOverrideRow(rs),
            id, chefIdentityId, serviceDate, mealSlotCode, totalCapacityUnits, subscriptionCapacityUnits, closed, reason, actorIdentityId
        ).stream().findFirst().orElseThrow();
    }

    public Optional<DateOverrideRow> findDateOverride(UUID chefIdentityId, LocalDate serviceDate, String mealSlotCode) {
        return jdbcTemplate.query(
            "SELECT * FROM subscription_schema.chef_capacity_override WHERE chef_identity_id = ? AND service_date = ? AND meal_slot_code = ?",
            (rs, rowNum) -> mapDateOverrideRow(rs), chefIdentityId, serviceDate, mealSlotCode
        ).stream().findFirst();
    }

    public List<DateOverrideRow> listDateOverrideRows(UUID chefIdentityId, LocalDate fromDate, LocalDate throughDate) {
        return jdbcTemplate.query(
            "SELECT * FROM subscription_schema.chef_capacity_override WHERE chef_identity_id = ? AND service_date BETWEEN ? AND ? " +
                "ORDER BY service_date, meal_slot_code",
            (rs, rowNum) -> mapDateOverrideRow(rs), chefIdentityId, fromDate, throughDate
        );
    }

    public MenuDateOverrideRow upsertMenuDateOverride(
        UUID chefIdentityId, UUID menuItemId, LocalDate serviceDate, String mealSlotCode,
        int maxSubscriptionUnits, boolean closed, String reason, UUID actorIdentityId
    ) {
        UUID id = UUID.randomUUID();
        return jdbcTemplate.query(
            "INSERT INTO subscription_schema.chef_menu_item_capacity_override " +
                "(id, chef_identity_id, menu_item_id, service_date, meal_slot_code, max_subscription_units, closed, reason, " +
                "updated_by_identity_id, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now()) " +
                "ON CONFLICT (chef_identity_id, menu_item_id, service_date, meal_slot_code) DO UPDATE SET " +
                "max_subscription_units = EXCLUDED.max_subscription_units, closed = EXCLUDED.closed, reason = EXCLUDED.reason, " +
                "updated_by_identity_id = EXCLUDED.updated_by_identity_id, updated_at = now() RETURNING *",
            (rs, rowNum) -> mapMenuDateOverrideRow(rs),
            id, chefIdentityId, menuItemId, serviceDate, mealSlotCode, maxSubscriptionUnits, closed, reason, actorIdentityId
        ).stream().findFirst().orElseThrow();
    }

    public Optional<MenuDateOverrideRow> findMenuDateOverride(
        UUID chefIdentityId, UUID menuItemId, LocalDate serviceDate, String mealSlotCode
    ) {
        return jdbcTemplate.query(
            "SELECT * FROM subscription_schema.chef_menu_item_capacity_override WHERE chef_identity_id = ? AND menu_item_id = ? " +
                "AND service_date = ? AND meal_slot_code = ?",
            (rs, rowNum) -> mapMenuDateOverrideRow(rs), chefIdentityId, menuItemId, serviceDate, mealSlotCode
        ).stream().findFirst();
    }

    public List<MenuDateOverrideRow> listMenuDateOverrideRows(UUID chefIdentityId, LocalDate fromDate, LocalDate throughDate) {
        return jdbcTemplate.query(
            "SELECT * FROM subscription_schema.chef_menu_item_capacity_override WHERE chef_identity_id = ? AND service_date BETWEEN ? AND ? " +
                "ORDER BY service_date, meal_slot_code, menu_item_id",
            (rs, rowNum) -> mapMenuDateOverrideRow(rs), chefIdentityId, fromDate, throughDate
        );
    }

    public int activeWeeklyUnits(UUID chefIdentityId, int isoDayOfWeek, String mealSlotCode) {
        Integer value = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(units), 0) FROM subscription_schema.subscription_capacity_entitlement " +
                "WHERE chef_identity_id = ? AND recurrence_type = 'WEEKLY' AND iso_day_of_week = ? AND meal_slot_code = ? AND " + ACTIVE_ENTITLEMENT,
            Integer.class, chefIdentityId, isoDayOfWeek, mealSlotCode
        );
        return value == null ? 0 : value;
    }

    public int activeMonthlyUnits(UUID chefIdentityId, int dayOfMonth, String mealSlotCode) {
        Integer value = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(units), 0) FROM subscription_schema.subscription_capacity_entitlement " +
                "WHERE chef_identity_id = ? AND recurrence_type = 'MONTHLY' AND day_of_month = ? AND meal_slot_code = ? AND " + ACTIVE_ENTITLEMENT,
            Integer.class, chefIdentityId, dayOfMonth, mealSlotCode
        );
        return value == null ? 0 : value;
    }

    public int maxActiveMonthlyUnits(UUID chefIdentityId, String mealSlotCode) {
        Integer value = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(total_units), 0) FROM (SELECT day_of_month, SUM(units) AS total_units " +
                "FROM subscription_schema.subscription_capacity_entitlement WHERE chef_identity_id = ? AND recurrence_type = 'MONTHLY' " +
                "AND meal_slot_code = ? AND " + ACTIVE_ENTITLEMENT + " GROUP BY day_of_month) grouped",
            Integer.class, chefIdentityId, mealSlotCode
        );
        return value == null ? 0 : value;
    }

    public int activeWeeklyItemUnits(UUID chefIdentityId, UUID menuItemId, int isoDayOfWeek, String mealSlotCode) {
        Integer value = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(units), 0) FROM subscription_schema.subscription_capacity_entitlement WHERE chef_identity_id = ? " +
                "AND menu_item_id = ? AND recurrence_type = 'WEEKLY' AND iso_day_of_week = ? AND meal_slot_code = ? AND " + ACTIVE_ENTITLEMENT,
            Integer.class, chefIdentityId, menuItemId, isoDayOfWeek, mealSlotCode
        );
        return value == null ? 0 : value;
    }

    public int activeMonthlyItemUnits(UUID chefIdentityId, UUID menuItemId, int dayOfMonth, String mealSlotCode) {
        Integer value = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(units), 0) FROM subscription_schema.subscription_capacity_entitlement WHERE chef_identity_id = ? " +
                "AND menu_item_id = ? AND recurrence_type = 'MONTHLY' AND day_of_month = ? AND meal_slot_code = ? AND " + ACTIVE_ENTITLEMENT,
            Integer.class, chefIdentityId, menuItemId, dayOfMonth, mealSlotCode
        );
        return value == null ? 0 : value;
    }

    public int maxActiveMonthlyItemUnits(UUID chefIdentityId, UUID menuItemId, String mealSlotCode) {
        Integer value = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(total_units), 0) FROM (SELECT day_of_month, SUM(units) AS total_units " +
                "FROM subscription_schema.subscription_capacity_entitlement WHERE chef_identity_id = ? AND menu_item_id = ? " +
                "AND recurrence_type = 'MONTHLY' AND meal_slot_code = ? AND " + ACTIVE_ENTITLEMENT + " GROUP BY day_of_month) grouped",
            Integer.class, chefIdentityId, menuItemId, mealSlotCode
        );
        return value == null ? 0 : value;
    }

    public void insertEntitlement(
        UUID subscriptionId, UUID chefIdentityId, String recurrenceType, Integer isoDayOfWeek, Integer dayOfMonth,
        String mealSlotCode, UUID menuItemId, int units, String status, Instant holdExpiresAt
    ) {
        jdbcTemplate.update(
            "INSERT INTO subscription_schema.subscription_capacity_entitlement " +
                "(id, subscription_id, chef_identity_id, recurrence_type, iso_day_of_week, day_of_month, meal_slot_code, menu_item_id, units, status, hold_expires_at, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())",
            UUID.randomUUID(), subscriptionId, chefIdentityId, recurrenceType, isoDayOfWeek, dayOfMonth,
            mealSlotCode, menuItemId, units, status, holdExpiresAt
        );
    }

    public List<EntitlementRow> listActiveEntitlements(UUID subscriptionId) {
        return jdbcTemplate.query(
            "SELECT * FROM subscription_schema.subscription_capacity_entitlement WHERE subscription_id = ? AND status IN ('HOLD','COMMITTED') " +
                "ORDER BY meal_slot_code, COALESCE(iso_day_of_week, day_of_month), menu_item_id",
            (rs, rowNum) -> mapEntitlementRow(rs), subscriptionId
        );
    }

    public List<EntitlementRow> listCommittedEntitlementsForChef(UUID chefIdentityId) {
        return jdbcTemplate.query(
            "SELECT * FROM subscription_schema.subscription_capacity_entitlement WHERE chef_identity_id = ? AND status = 'COMMITTED' " +
                "ORDER BY subscription_id, meal_slot_code, COALESCE(iso_day_of_week, day_of_month), menu_item_id",
            (rs, rowNum) -> mapEntitlementRow(rs), chefIdentityId
        );
    }

    public void updateEntitlementStatus(UUID subscriptionId, String oldStatus, String newStatus, Instant holdExpiresAt) {
        jdbcTemplate.update(
            "UPDATE subscription_schema.subscription_capacity_entitlement SET status = ?, hold_expires_at = ?, updated_at = now() " +
                "WHERE subscription_id = ? AND status = ?",
            newStatus, holdExpiresAt, subscriptionId, oldStatus
        );
    }

    public void releaseEntitlements(UUID subscriptionId) {
        jdbcTemplate.update(
            "UPDATE subscription_schema.subscription_capacity_entitlement SET status = 'RELEASED', hold_expires_at = NULL, updated_at = now() " +
                "WHERE subscription_id = ? AND status IN ('HOLD','COMMITTED')",
            subscriptionId
        );
    }

    public void expireHolds(Instant now) {
        jdbcTemplate.update(
            "UPDATE subscription_schema.subscription_capacity_entitlement SET status = 'EXPIRED', updated_at = now() " +
                "WHERE status = 'HOLD' AND hold_expires_at <= ?",
            now
        );
        jdbcTemplate.update(
            "UPDATE subscription_schema.subscription_capacity_allocation SET status = 'EXPIRED', updated_at = now() " +
                "WHERE status = 'HOLD' AND hold_expires_at <= ?",
            now
        );
    }

    public int currentDateSlotUnits(UUID chefIdentityId, LocalDate serviceDate, String mealSlotCode) {
        Integer value = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(units),0) FROM subscription_schema.subscription_capacity_allocation WHERE chef_identity_id = ? " +
                "AND service_date = ? AND meal_slot_code = ? AND " + ACTIVE_ALLOCATION,
            Integer.class, chefIdentityId, serviceDate, mealSlotCode
        );
        return value == null ? 0 : value;
    }

    public int currentDateItemUnits(UUID chefIdentityId, UUID menuItemId, LocalDate serviceDate, String mealSlotCode) {
        Integer value = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(units),0) FROM subscription_schema.subscription_capacity_allocation WHERE chef_identity_id = ? AND menu_item_id = ? " +
                "AND service_date = ? AND meal_slot_code = ? AND " + ACTIVE_ALLOCATION,
            Integer.class, chefIdentityId, menuItemId, serviceDate, mealSlotCode
        );
        return value == null ? 0 : value;
    }

    public void upsertAllocation(
        UUID subscriptionId, UUID chefIdentityId, LocalDate serviceDate, String mealSlotCode,
        UUID menuItemId, int units, String status, Instant holdExpiresAt
    ) {
        jdbcTemplate.update(
            "INSERT INTO subscription_schema.subscription_capacity_allocation " +
                "(id, subscription_id, chef_identity_id, service_date, meal_slot_code, menu_item_id, units, status, hold_expires_at, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now()) " +
                "ON CONFLICT (subscription_id, service_date, meal_slot_code, menu_item_id) DO UPDATE SET " +
                "units = EXCLUDED.units, status = EXCLUDED.status, hold_expires_at = EXCLUDED.hold_expires_at, updated_at = now()",
            UUID.randomUUID(), subscriptionId, chefIdentityId, serviceDate, mealSlotCode, menuItemId, units, status, holdExpiresAt
        );
    }

    public void updateAllocationStatus(UUID subscriptionId, String oldStatus, String newStatus, Instant holdExpiresAt) {
        jdbcTemplate.update(
            "UPDATE subscription_schema.subscription_capacity_allocation SET status = ?, hold_expires_at = ?, updated_at = now() " +
                "WHERE subscription_id = ? AND status = ?",
            newStatus, holdExpiresAt, subscriptionId, oldStatus
        );
    }

    public void releaseAllocations(UUID subscriptionId, LocalDate fromDate) {
        jdbcTemplate.update(
            "UPDATE subscription_schema.subscription_capacity_allocation SET status = 'RELEASED', hold_expires_at = NULL, updated_at = now() " +
                "WHERE subscription_id = ? AND service_date >= ? AND status IN ('HOLD','COMMITTED','MATERIALIZED')",
            subscriptionId, fromDate
        );
    }

    public void releaseAllocationsForDate(UUID subscriptionId, LocalDate serviceDate) {
        jdbcTemplate.update(
            "UPDATE subscription_schema.subscription_capacity_allocation SET status = 'RELEASED', hold_expires_at = NULL, updated_at = now() " +
                "WHERE subscription_id = ? AND service_date = ? AND status IN ('HOLD','COMMITTED')",
            subscriptionId, serviceDate
        );
    }

    public void markAllocationMaterialized(UUID subscriptionId, LocalDate serviceDate, String mealSlotCode, UUID occurrenceId) {
        jdbcTemplate.update(
            "UPDATE subscription_schema.subscription_capacity_allocation SET status = 'MATERIALIZED', occurrence_id = ?, updated_at = now() " +
                "WHERE subscription_id = ? AND service_date = ? AND meal_slot_code = ? AND status = 'COMMITTED'",
            occurrenceId, subscriptionId, serviceDate, mealSlotCode
        );
    }

    public void upsertBucket(
        UUID chefIdentityId, LocalDate serviceDate, String mealSlotCode, int totalCapacityUnits,
        int subscriptionCapacityUnits, boolean closed, String source, int configVersion
    ) {
        int held = heldDateSlotUnits(chefIdentityId, serviceDate, mealSlotCode);
        int committed = committedDateSlotUnits(chefIdentityId, serviceDate, mealSlotCode);
        int deficit = Math.max(0, held + committed - subscriptionCapacityUnits);
        jdbcTemplate.update(
            "INSERT INTO subscription_schema.chef_capacity_bucket " +
                "(chef_identity_id, service_date, meal_slot_code, total_capacity_units, subscription_capacity_units, held_units, committed_units, closed, deficit_units, source, config_version, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now()) " +
                "ON CONFLICT (chef_identity_id, service_date, meal_slot_code) DO UPDATE SET total_capacity_units = EXCLUDED.total_capacity_units, " +
                "subscription_capacity_units = EXCLUDED.subscription_capacity_units, held_units = EXCLUDED.held_units, committed_units = EXCLUDED.committed_units, " +
                "closed = EXCLUDED.closed, deficit_units = EXCLUDED.deficit_units, source = EXCLUDED.source, config_version = EXCLUDED.config_version, updated_at = now()",
            chefIdentityId, serviceDate, mealSlotCode, totalCapacityUnits, subscriptionCapacityUnits, held, committed,
            closed, deficit, source, configVersion
        );
    }

    public void upsertMenuBucket(
        UUID chefIdentityId, UUID menuItemId, LocalDate serviceDate, String mealSlotCode,
        int maxSubscriptionUnits, boolean closed, String source, int configVersion
    ) {
        int held = heldDateItemUnits(chefIdentityId, menuItemId, serviceDate, mealSlotCode);
        int committed = committedDateItemUnits(chefIdentityId, menuItemId, serviceDate, mealSlotCode);
        int deficit = Math.max(0, held + committed - maxSubscriptionUnits);
        jdbcTemplate.update(
            "INSERT INTO subscription_schema.chef_menu_item_capacity_bucket " +
                "(chef_identity_id, menu_item_id, service_date, meal_slot_code, max_subscription_units, held_units, committed_units, closed, deficit_units, source, config_version, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now()) " +
                "ON CONFLICT (chef_identity_id, menu_item_id, service_date, meal_slot_code) DO UPDATE SET max_subscription_units = EXCLUDED.max_subscription_units, " +
                "held_units = EXCLUDED.held_units, committed_units = EXCLUDED.committed_units, closed = EXCLUDED.closed, deficit_units = EXCLUDED.deficit_units, " +
                "source = EXCLUDED.source, config_version = EXCLUDED.config_version, updated_at = now()",
            chefIdentityId, menuItemId, serviceDate, mealSlotCode, maxSubscriptionUnits, held, committed,
            closed, deficit, source, configVersion
        );
    }

    public void openOrUpdateIncident(
        UUID chefIdentityId, LocalDate serviceDate, Integer isoDayOfWeek, String mealSlotCode, UUID menuItemId,
        String incidentType, String severity, int reservedUnits, int capacityUnits, String reason
    ) {
        Optional<UUID> existing = jdbcTemplate.query(
            "SELECT id FROM subscription_schema.capacity_incident WHERE chef_identity_id = ? " +
                "AND service_date IS NOT DISTINCT FROM ? AND iso_day_of_week IS NOT DISTINCT FROM ? AND meal_slot_code = ? " +
                "AND menu_item_id IS NOT DISTINCT FROM ? AND incident_type = ? AND status = 'OPEN' FOR UPDATE",
            (rs, rowNum) -> rs.getObject("id", UUID.class),
            chefIdentityId, serviceDate, isoDayOfWeek, mealSlotCode, menuItemId, incidentType
        ).stream().findFirst();
        if (existing.isPresent()) {
            jdbcTemplate.update(
                "UPDATE subscription_schema.capacity_incident SET severity = ?, reserved_units = ?, capacity_units = ?, reason = ?, updated_at = now() WHERE id = ?",
                severity, reservedUnits, capacityUnits, reason, existing.get()
            );
        } else {
            jdbcTemplate.update(
                "INSERT INTO subscription_schema.capacity_incident " +
                    "(id, chef_identity_id, service_date, iso_day_of_week, meal_slot_code, menu_item_id, incident_type, severity, status, reserved_units, capacity_units, reason, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'OPEN', ?, ?, ?, now(), now())",
                UUID.randomUUID(), chefIdentityId, serviceDate, isoDayOfWeek, mealSlotCode, menuItemId,
                incidentType, severity, reservedUnits, capacityUnits, reason
            );
        }
    }

    public void resolveIncident(
        UUID chefIdentityId, LocalDate serviceDate, Integer isoDayOfWeek, String mealSlotCode,
        UUID menuItemId, String incidentType
    ) {
        jdbcTemplate.update(
            "UPDATE subscription_schema.capacity_incident SET status = 'RESOLVED', resolved_at = now(), updated_at = now() " +
                "WHERE chef_identity_id = ? AND service_date IS NOT DISTINCT FROM ? AND iso_day_of_week IS NOT DISTINCT FROM ? " +
                "AND meal_slot_code = ? AND menu_item_id IS NOT DISTINCT FROM ? AND incident_type = ? AND status = 'OPEN'",
            chefIdentityId, serviceDate, isoDayOfWeek, mealSlotCode, menuItemId, incidentType
        );
    }

    public long countOpenIncidents(UUID chefIdentityId) {
        Long value = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM subscription_schema.capacity_incident WHERE chef_identity_id = ? AND status = 'OPEN'",
            Long.class, chefIdentityId
        );
        return value == null ? 0L : value;
    }

    public CapacityIncidentPage listIncidents(
        UUID chefIdentityId, String status, Instant afterCreatedAt, UUID afterId, int limit
    ) {
        List<CapacityIncidentResponse> rows = jdbcTemplate.query(
            "SELECT * FROM subscription_schema.capacity_incident WHERE (CAST(? AS UUID) IS NULL OR chef_identity_id = ?) " +
                "AND (CAST(? AS VARCHAR) IS NULL OR status = ?) " +
                "AND (CAST(? AS TIMESTAMPTZ) IS NULL OR created_at < ? OR (created_at = ? AND (CAST(? AS UUID) IS NULL OR id < ?))) " +
                "ORDER BY created_at DESC, id DESC LIMIT ?",
            this::mapIncident,
            chefIdentityId, chefIdentityId, status, status, afterCreatedAt, afterCreatedAt, afterCreatedAt,
            afterId, afterId, limit + 1
        );
        boolean hasMore = rows.size() > limit;
        List<CapacityIncidentResponse> items = hasMore ? new ArrayList<>(rows.subList(0, limit)) : rows;
        CapacityIncidentResponse last = items.isEmpty() ? null : items.getLast();
        return new CapacityIncidentPage(
            List.copyOf(items), hasMore && last != null ? last.createdAt() : null,
            hasMore && last != null ? last.id() : null, hasMore
        );
    }

    public void audit(
        UUID chefIdentityId, UUID actorIdentityId, String action, String entityType,
        String entityKey, String reason, String oldStateJson, String newStateJson
    ) {
        jdbcTemplate.update(
            "INSERT INTO subscription_schema.capacity_audit " +
                "(id, chef_identity_id, actor_identity_id, action, entity_type, entity_key, reason, old_state, new_state, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), now())",
            UUID.randomUUID(), chefIdentityId, actorIdentityId, action, entityType, entityKey, reason,
            oldStateJson, newStateJson
        );
    }

    public List<SubscriptionProjectionCandidate> listProjectionCandidates(int limit) {
        return jdbcTemplate.query(
            "SELECT cs.id, cs.plan_id, cs.chef_identity_id, cs.start_date, cs.next_service_date " +
                "FROM subscription_schema.customer_subscription cs WHERE cs.status = 'ACTIVE' AND cs.chef_identity_id IS NOT NULL " +
                "AND EXISTS (SELECT 1 FROM subscription_schema.subscription_capacity_entitlement e WHERE e.subscription_id = cs.id AND e.status = 'COMMITTED') " +
                "ORDER BY cs.updated_at ASC LIMIT ?",
            (rs, rowNum) -> new SubscriptionProjectionCandidate(
                rs.getObject("id", UUID.class), rs.getObject("plan_id", UUID.class),
                rs.getObject("chef_identity_id", UUID.class), rs.getObject("start_date", LocalDate.class),
                rs.getObject("next_service_date", LocalDate.class)
            ), limit
        );
    }

    public Optional<LocalDate> maxAllocatedDate(UUID subscriptionId) {
        return jdbcTemplate.query(
            "SELECT MAX(service_date) AS max_date FROM subscription_schema.subscription_capacity_allocation " +
                "WHERE subscription_id = ? AND status IN ('COMMITTED','MATERIALIZED')",
            (rs, rowNum) -> rs.getObject("max_date", LocalDate.class), subscriptionId
        ).stream().filter(value -> value != null).findFirst();
    }

    public List<SlotRuleResponse> listSlotRules(UUID chefIdentityId) {
        return listSlotRuleRows(chefIdentityId).stream().map(row -> {
            int reserved = activeWeeklyUnits(chefIdentityId, row.isoDayOfWeek(), row.mealSlotCode())
                + maxActiveMonthlyUnits(chefIdentityId, row.mealSlotCode());
            return new SlotRuleResponse(
                row.id(), row.chefIdentityId(), row.isoDayOfWeek(), row.mealSlotCode(), row.totalCapacityUnits(),
                row.subscriptionCapacityUnits(), row.salesEnabled(), reserved,
                Math.max(0, row.subscriptionCapacityUnits() - reserved), Math.max(0, reserved - row.subscriptionCapacityUnits()),
                row.version(), row.updatedAt()
            );
        }).toList();
    }

    public List<MenuItemRuleResponse> listMenuRules(UUID chefIdentityId) {
        return listMenuRuleRows(chefIdentityId).stream().map(row -> {
            int reserved = activeWeeklyItemUnits(chefIdentityId, row.menuItemId(), row.isoDayOfWeek(), row.mealSlotCode())
                + maxActiveMonthlyItemUnits(chefIdentityId, row.menuItemId(), row.mealSlotCode());
            return new MenuItemRuleResponse(
                row.id(), row.chefIdentityId(), row.menuItemId(), row.isoDayOfWeek(), row.mealSlotCode(),
                row.maxSubscriptionUnits(), row.salesEnabled(), reserved,
                Math.max(0, row.maxSubscriptionUnits() - reserved), Math.max(0, reserved - row.maxSubscriptionUnits()),
                row.version(), row.updatedAt()
            );
        }).toList();
    }

    public List<DateOverrideResponse> listDateOverrides(UUID chefIdentityId, LocalDate fromDate, LocalDate throughDate) {
        return listDateOverrideRows(chefIdentityId, fromDate, throughDate).stream().map(row -> {
            int held = heldDateSlotUnits(chefIdentityId, row.serviceDate(), row.mealSlotCode());
            int committed = committedDateSlotUnits(chefIdentityId, row.serviceDate(), row.mealSlotCode());
            return new DateOverrideResponse(
                row.id(), row.chefIdentityId(), row.serviceDate(), row.mealSlotCode(), row.totalCapacityUnits(),
                row.subscriptionCapacityUnits(), row.closed(), row.reason(), held, committed,
                Math.max(0, held + committed - row.subscriptionCapacityUnits()), row.updatedAt()
            );
        }).toList();
    }

    public List<MenuItemDateOverrideResponse> listMenuDateOverrides(UUID chefIdentityId, LocalDate fromDate, LocalDate throughDate) {
        return listMenuDateOverrideRows(chefIdentityId, fromDate, throughDate).stream().map(row -> {
            int held = heldDateItemUnits(chefIdentityId, row.menuItemId(), row.serviceDate(), row.mealSlotCode());
            int committed = committedDateItemUnits(chefIdentityId, row.menuItemId(), row.serviceDate(), row.mealSlotCode());
            return new MenuItemDateOverrideResponse(
                row.id(), row.chefIdentityId(), row.menuItemId(), row.serviceDate(), row.mealSlotCode(),
                row.maxSubscriptionUnits(), row.closed(), row.reason(), held, committed,
                Math.max(0, held + committed - row.maxSubscriptionUnits()), row.updatedAt()
            );
        }).toList();
    }

    private int heldDateSlotUnits(UUID chefIdentityId, LocalDate serviceDate, String mealSlotCode) {
        Integer value = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(units),0) FROM subscription_schema.subscription_capacity_allocation WHERE chef_identity_id = ? " +
                "AND service_date = ? AND meal_slot_code = ? AND status = 'HOLD' AND hold_expires_at > now()",
            Integer.class, chefIdentityId, serviceDate, mealSlotCode
        );
        return value == null ? 0 : value;
    }

    private int committedDateSlotUnits(UUID chefIdentityId, LocalDate serviceDate, String mealSlotCode) {
        Integer value = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(units),0) FROM subscription_schema.subscription_capacity_allocation WHERE chef_identity_id = ? " +
                "AND service_date = ? AND meal_slot_code = ? AND status IN ('COMMITTED','MATERIALIZED')",
            Integer.class, chefIdentityId, serviceDate, mealSlotCode
        );
        return value == null ? 0 : value;
    }

    private int heldDateItemUnits(UUID chefIdentityId, UUID menuItemId, LocalDate serviceDate, String mealSlotCode) {
        Integer value = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(units),0) FROM subscription_schema.subscription_capacity_allocation WHERE chef_identity_id = ? AND menu_item_id = ? " +
                "AND service_date = ? AND meal_slot_code = ? AND status = 'HOLD' AND hold_expires_at > now()",
            Integer.class, chefIdentityId, menuItemId, serviceDate, mealSlotCode
        );
        return value == null ? 0 : value;
    }

    private int committedDateItemUnits(UUID chefIdentityId, UUID menuItemId, LocalDate serviceDate, String mealSlotCode) {
        Integer value = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(units),0) FROM subscription_schema.subscription_capacity_allocation WHERE chef_identity_id = ? AND menu_item_id = ? " +
                "AND service_date = ? AND meal_slot_code = ? AND status IN ('COMMITTED','MATERIALIZED')",
            Integer.class, chefIdentityId, menuItemId, serviceDate, mealSlotCode
        );
        return value == null ? 0 : value;
    }

    private SlotRuleRow mapSlotRuleRow(ResultSet rs) throws SQLException {
        return new SlotRuleRow(
            rs.getObject("id", UUID.class), rs.getObject("chef_identity_id", UUID.class), rs.getInt("iso_day_of_week"),
            rs.getString("meal_slot_code"), rs.getInt("total_capacity_units"), rs.getInt("subscription_capacity_units"),
            rs.getBoolean("sales_enabled"), rs.getInt("version"), rs.getObject("updated_at", Instant.class)
        );
    }

    private MenuRuleRow mapMenuRuleRow(ResultSet rs) throws SQLException {
        return new MenuRuleRow(
            rs.getObject("id", UUID.class), rs.getObject("chef_identity_id", UUID.class), rs.getObject("menu_item_id", UUID.class),
            rs.getInt("iso_day_of_week"), rs.getString("meal_slot_code"), rs.getInt("max_subscription_units"),
            rs.getBoolean("sales_enabled"), rs.getInt("version"), rs.getObject("updated_at", Instant.class)
        );
    }

    private DateOverrideRow mapDateOverrideRow(ResultSet rs) throws SQLException {
        return new DateOverrideRow(
            rs.getObject("id", UUID.class), rs.getObject("chef_identity_id", UUID.class), rs.getObject("service_date", LocalDate.class),
            rs.getString("meal_slot_code"), rs.getInt("total_capacity_units"), rs.getInt("subscription_capacity_units"),
            rs.getBoolean("closed"), rs.getString("reason"), rs.getObject("updated_at", Instant.class)
        );
    }

    private MenuDateOverrideRow mapMenuDateOverrideRow(ResultSet rs) throws SQLException {
        return new MenuDateOverrideRow(
            rs.getObject("id", UUID.class), rs.getObject("chef_identity_id", UUID.class), rs.getObject("menu_item_id", UUID.class),
            rs.getObject("service_date", LocalDate.class), rs.getString("meal_slot_code"), rs.getInt("max_subscription_units"),
            rs.getBoolean("closed"), rs.getString("reason"), rs.getObject("updated_at", Instant.class)
        );
    }

    private EntitlementRow mapEntitlementRow(ResultSet rs) throws SQLException {
        return new EntitlementRow(
            rs.getObject("id", UUID.class), rs.getObject("subscription_id", UUID.class), rs.getObject("chef_identity_id", UUID.class),
            rs.getString("recurrence_type"), nullableInt(rs, "iso_day_of_week"), nullableInt(rs, "day_of_month"),
            rs.getString("meal_slot_code"), rs.getObject("menu_item_id", UUID.class), rs.getInt("units"), rs.getString("status"),
            rs.getObject("hold_expires_at", Instant.class)
        );
    }

    private CapacityIncidentResponse mapIncident(ResultSet rs, int rowNum) throws SQLException {
        return new CapacityIncidentResponse(
            rs.getObject("id", UUID.class), rs.getObject("chef_identity_id", UUID.class), rs.getObject("service_date", LocalDate.class),
            nullableInt(rs, "iso_day_of_week"), rs.getString("meal_slot_code"), rs.getObject("menu_item_id", UUID.class),
            rs.getString("incident_type"), rs.getString("severity"), rs.getString("status"), rs.getInt("reserved_units"),
            rs.getInt("capacity_units"), rs.getString("reason"), rs.getObject("created_at", Instant.class),
            rs.getObject("updated_at", Instant.class), rs.getObject("resolved_at", Instant.class)
        );
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    public record CapacityControl(UUID chefIdentityId, boolean frozen, String freezeReason) {}
    public record SlotRuleRow(UUID id, UUID chefIdentityId, int isoDayOfWeek, String mealSlotCode, int totalCapacityUnits, int subscriptionCapacityUnits, boolean salesEnabled, int version, Instant updatedAt) {}
    public record MenuRuleRow(UUID id, UUID chefIdentityId, UUID menuItemId, int isoDayOfWeek, String mealSlotCode, int maxSubscriptionUnits, boolean salesEnabled, int version, Instant updatedAt) {}
    public record DateOverrideRow(UUID id, UUID chefIdentityId, LocalDate serviceDate, String mealSlotCode, int totalCapacityUnits, int subscriptionCapacityUnits, boolean closed, String reason, Instant updatedAt) {}
    public record MenuDateOverrideRow(UUID id, UUID chefIdentityId, UUID menuItemId, LocalDate serviceDate, String mealSlotCode, int maxSubscriptionUnits, boolean closed, String reason, Instant updatedAt) {}
    public record EntitlementRow(UUID id, UUID subscriptionId, UUID chefIdentityId, String recurrenceType, Integer isoDayOfWeek, Integer dayOfMonth, String mealSlotCode, UUID menuItemId, int units, String status, Instant holdExpiresAt) {}
    public record SubscriptionProjectionCandidate(UUID subscriptionId, UUID planId, UUID chefIdentityId, LocalDate startDate, LocalDate nextServiceDate) {}
}
