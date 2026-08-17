package in.craves.subscription.capacity;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@Primary
public class PostgresSlotCapacityRepository extends CapacityRepository {
    private final JdbcTemplate jdbcTemplate;

    public PostgresSlotCapacityRepository(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public SlotRuleRow upsertSlotRule(
        UUID chefIdentityId,
        int isoDayOfWeek,
        String mealSlotCode,
        int totalCapacityUnits,
        int subscriptionCapacityUnits,
        boolean salesEnabled,
        UUID actorIdentityId
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
                "updated_by_identity_id = EXCLUDED.updated_by_identity_id, updated_at = now() RETURNING *",
            (rs, rowNum) -> mapSlotRuleRow(rs),
            id, chefIdentityId, isoDayOfWeek, mealSlotCode, totalCapacityUnits,
            subscriptionCapacityUnits, salesEnabled, actorIdentityId
        ).stream().findFirst().orElseThrow();
    }

    @Override
    public Optional<SlotRuleRow> findSlotRule(UUID chefIdentityId, int isoDayOfWeek, String mealSlotCode) {
        return jdbcTemplate.query(
            "SELECT * FROM subscription_schema.chef_capacity_rule WHERE chef_identity_id = ? AND iso_day_of_week = ? AND meal_slot_code = ?",
            (rs, rowNum) -> mapSlotRuleRow(rs), chefIdentityId, isoDayOfWeek, mealSlotCode
        ).stream().findFirst();
    }

    @Override
    public List<SlotRuleRow> listSlotRuleRows(UUID chefIdentityId) {
        return jdbcTemplate.query(
            "SELECT * FROM subscription_schema.chef_capacity_rule WHERE chef_identity_id = ? ORDER BY iso_day_of_week, meal_slot_code",
            (rs, rowNum) -> mapSlotRuleRow(rs), chefIdentityId
        );
    }

    @Override
    public MenuRuleRow upsertMenuRule(
        UUID chefIdentityId,
        UUID menuItemId,
        int isoDayOfWeek,
        String mealSlotCode,
        int maxSubscriptionUnits,
        boolean salesEnabled,
        UUID actorIdentityId
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
            id, chefIdentityId, menuItemId, isoDayOfWeek, mealSlotCode,
            maxSubscriptionUnits, salesEnabled, actorIdentityId
        ).stream().findFirst().orElseThrow();
    }

    @Override
    public Optional<MenuRuleRow> findMenuRule(UUID chefIdentityId, UUID menuItemId, int isoDayOfWeek, String mealSlotCode) {
        return jdbcTemplate.query(
            "SELECT * FROM subscription_schema.chef_menu_item_capacity_rule WHERE chef_identity_id = ? AND menu_item_id = ? " +
                "AND iso_day_of_week = ? AND meal_slot_code = ?",
            (rs, rowNum) -> mapMenuRuleRow(rs), chefIdentityId, menuItemId, isoDayOfWeek, mealSlotCode
        ).stream().findFirst();
    }

    @Override
    public List<MenuRuleRow> listMenuRuleRows(UUID chefIdentityId) {
        return jdbcTemplate.query(
            "SELECT * FROM subscription_schema.chef_menu_item_capacity_rule WHERE chef_identity_id = ? " +
                "ORDER BY iso_day_of_week, meal_slot_code, menu_item_id",
            (rs, rowNum) -> mapMenuRuleRow(rs), chefIdentityId
        );
    }

    @Override
    public DateOverrideRow upsertDateOverride(
        UUID chefIdentityId,
        LocalDate serviceDate,
        String mealSlotCode,
        int totalCapacityUnits,
        int subscriptionCapacityUnits,
        boolean closed,
        String reason,
        UUID actorIdentityId
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
            id, chefIdentityId, serviceDate, mealSlotCode, totalCapacityUnits,
            subscriptionCapacityUnits, closed, reason, actorIdentityId
        ).stream().findFirst().orElseThrow();
    }

    @Override
    public Optional<DateOverrideRow> findDateOverride(UUID chefIdentityId, LocalDate serviceDate, String mealSlotCode) {
        return jdbcTemplate.query(
            "SELECT * FROM subscription_schema.chef_capacity_override WHERE chef_identity_id = ? AND service_date = ? AND meal_slot_code = ?",
            (rs, rowNum) -> mapDateOverrideRow(rs), chefIdentityId, serviceDate, mealSlotCode
        ).stream().findFirst();
    }

    @Override
    public List<DateOverrideRow> listDateOverrideRows(UUID chefIdentityId, LocalDate fromDate, LocalDate throughDate) {
        return jdbcTemplate.query(
            "SELECT * FROM subscription_schema.chef_capacity_override WHERE chef_identity_id = ? AND service_date BETWEEN ? AND ? " +
                "ORDER BY service_date, meal_slot_code",
            (rs, rowNum) -> mapDateOverrideRow(rs), chefIdentityId, fromDate, throughDate
        );
    }

    @Override
    public MenuDateOverrideRow upsertMenuDateOverride(
        UUID chefIdentityId,
        UUID menuItemId,
        LocalDate serviceDate,
        String mealSlotCode,
        int maxSubscriptionUnits,
        boolean closed,
        String reason,
        UUID actorIdentityId
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
            id, chefIdentityId, menuItemId, serviceDate, mealSlotCode,
            maxSubscriptionUnits, closed, reason, actorIdentityId
        ).stream().findFirst().orElseThrow();
    }

    @Override
    public Optional<MenuDateOverrideRow> findMenuDateOverride(
        UUID chefIdentityId,
        UUID menuItemId,
        LocalDate serviceDate,
        String mealSlotCode
    ) {
        return jdbcTemplate.query(
            "SELECT * FROM subscription_schema.chef_menu_item_capacity_override WHERE chef_identity_id = ? AND menu_item_id = ? " +
                "AND service_date = ? AND meal_slot_code = ?",
            (rs, rowNum) -> mapMenuDateOverrideRow(rs), chefIdentityId, menuItemId, serviceDate, mealSlotCode
        ).stream().findFirst();
    }

    @Override
    public List<MenuDateOverrideRow> listMenuDateOverrideRows(UUID chefIdentityId, LocalDate fromDate, LocalDate throughDate) {
        return jdbcTemplate.query(
            "SELECT * FROM subscription_schema.chef_menu_item_capacity_override WHERE chef_identity_id = ? AND service_date BETWEEN ? AND ? " +
                "ORDER BY service_date, meal_slot_code, menu_item_id",
            (rs, rowNum) -> mapMenuDateOverrideRow(rs), chefIdentityId, fromDate, throughDate
        );
    }

    private SlotRuleRow mapSlotRuleRow(ResultSet rs) throws SQLException {
        return new SlotRuleRow(
            rs.getObject("id", UUID.class),
            rs.getObject("chef_identity_id", UUID.class),
            rs.getInt("iso_day_of_week"),
            rs.getString("meal_slot_code"),
            rs.getInt("total_capacity_units"),
            rs.getInt("subscription_capacity_units"),
            rs.getBoolean("sales_enabled"),
            rs.getInt("version"),
            instant(rs, "updated_at")
        );
    }

    private MenuRuleRow mapMenuRuleRow(ResultSet rs) throws SQLException {
        return new MenuRuleRow(
            rs.getObject("id", UUID.class),
            rs.getObject("chef_identity_id", UUID.class),
            rs.getObject("menu_item_id", UUID.class),
            rs.getInt("iso_day_of_week"),
            rs.getString("meal_slot_code"),
            rs.getInt("max_subscription_units"),
            rs.getBoolean("sales_enabled"),
            rs.getInt("version"),
            instant(rs, "updated_at")
        );
    }

    private DateOverrideRow mapDateOverrideRow(ResultSet rs) throws SQLException {
        return new DateOverrideRow(
            rs.getObject("id", UUID.class),
            rs.getObject("chef_identity_id", UUID.class),
            rs.getObject("service_date", LocalDate.class),
            rs.getString("meal_slot_code"),
            rs.getInt("total_capacity_units"),
            rs.getInt("subscription_capacity_units"),
            rs.getBoolean("closed"),
            rs.getString("reason"),
            instant(rs, "updated_at")
        );
    }

    private MenuDateOverrideRow mapMenuDateOverrideRow(ResultSet rs) throws SQLException {
        return new MenuDateOverrideRow(
            rs.getObject("id", UUID.class),
            rs.getObject("chef_identity_id", UUID.class),
            rs.getObject("menu_item_id", UUID.class),
            rs.getObject("service_date", LocalDate.class),
            rs.getString("meal_slot_code"),
            rs.getInt("max_subscription_units"),
            rs.getBoolean("closed"),
            rs.getString("reason"),
            instant(rs, "updated_at")
        );
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
