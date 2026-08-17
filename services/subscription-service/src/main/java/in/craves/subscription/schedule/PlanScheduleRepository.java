package in.craves.subscription.schedule;

import in.craves.subscription.schedule.PlanScheduleModels.PlanScheduleResponse;
import in.craves.subscription.schedule.PlanScheduleModels.ScheduleItemRequest;
import in.craves.subscription.schedule.PlanScheduleModels.ScheduleItemResponse;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class PlanScheduleRepository {
    private final JdbcTemplate jdbcTemplate;

    public PlanScheduleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<PlanOwner> findPlanOwner(UUID planId) {
        return jdbcTemplate.query(
            "SELECT id, chef_identity_id, status, billing_period FROM subscription_schema.subscription_plan WHERE id = ?",
            (rs, rowNum) -> new PlanOwner(
                rs.getObject("id", UUID.class),
                rs.getObject("chef_identity_id", UUID.class),
                rs.getString("status"),
                rs.getString("billing_period")
            ),
            planId
        ).stream().findFirst();
    }

    public Optional<PlanScheduleResponse> find(UUID planId) {
        Optional<PlanScheduleResponse> draft = findDraft(planId);
        return draft.isPresent() ? draft : findCurrent(planId);
    }

    public Optional<PlanScheduleResponse> findActive(UUID planId) {
        return jdbcTemplate.query(
            "SELECT * FROM subscription_schema.subscription_plan_schedule WHERE plan_id = ? AND status = 'ACTIVE'",
            (rs, rowNum) -> mapCurrent(rs, listCurrentItems(planId)),
            planId
        ).stream().findFirst();
    }

    @Transactional
    public PlanScheduleResponse replaceDraft(
        UUID planId,
        String recurrenceType,
        String timezone,
        int generationLeadHours,
        List<PreparedScheduleItem> items,
        UUID actor
    ) {
        int nextVersion = findDraft(planId)
            .map(PlanScheduleResponse::version)
            .orElseGet(() -> findCurrent(planId).map(value -> value.version() + 1).orElse(1));
        LocalTime earliestServiceTime = items.stream()
            .map(item -> item.request().serviceTime())
            .min(Comparator.naturalOrder())
            .orElseThrow(() -> new IllegalArgumentException("At least one schedule item is required"));

        jdbcTemplate.update(
            "INSERT INTO subscription_schema.subscription_plan_schedule_draft " +
                "(plan_id, recurrence_type, timezone, service_time, generation_lead_hours, version, created_by_identity_id, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, now(), now()) " +
                "ON CONFLICT (plan_id) DO UPDATE SET recurrence_type = EXCLUDED.recurrence_type, timezone = EXCLUDED.timezone, " +
                "service_time = EXCLUDED.service_time, generation_lead_hours = EXCLUDED.generation_lead_hours, " +
                "created_by_identity_id = EXCLUDED.created_by_identity_id, updated_at = now()",
            planId, recurrenceType, timezone, earliestServiceTime, generationLeadHours, nextVersion, actor
        );
        jdbcTemplate.update(
            "DELETE FROM subscription_schema.subscription_plan_schedule_draft_item WHERE plan_id = ?",
            planId
        );
        for (PreparedScheduleItem prepared : items) {
            ScheduleItemRequest item = prepared.request();
            jdbcTemplate.update(
                "INSERT INTO subscription_schema.subscription_plan_schedule_draft_item " +
                    "(id, plan_id, menu_item_id, menu_item_name_snapshot, menu_item_category_snapshot, menu_item_food_type_snapshot, " +
                    "menu_item_price_snapshot, menu_item_currency_snapshot, quantity, iso_day_of_week, day_of_month, meal_slot_code, service_time, sequence_number, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())",
                UUID.randomUUID(),
                planId,
                item.menuItemId(),
                prepared.menuItemName(),
                prepared.menuItemCategory(),
                prepared.menuItemFoodType(),
                prepared.menuItemPrice(),
                prepared.menuItemCurrency(),
                item.quantity(),
                item.isoDayOfWeek(),
                item.dayOfMonth(),
                item.mealSlotCode().trim().toUpperCase(Locale.ROOT),
                item.serviceTime(),
                item.sequenceNumber()
            );
        }
        jdbcTemplate.update(
            "INSERT INTO subscription_schema.subscription_plan_schedule_audit " +
                "(id, plan_id, actor_identity_id, action, schedule_version, reason, created_at) " +
                "VALUES (?, ?, ?, 'PUT_DRAFT', ?, 'Chef meal schedule draft saved with catalog snapshots', now())",
            UUID.randomUUID(), planId, actor, nextVersion
        );
        return findDraft(planId).orElseThrow();
    }

    @Transactional
    public PlanScheduleResponse activate(UUID planId, UUID actor, String reason) {
        PlanScheduleResponse draft = findDraft(planId)
            .orElseThrow(() -> new IllegalStateException("Only a draft schedule can be activated"));

        jdbcTemplate.update(
            "INSERT INTO subscription_schema.subscription_plan_schedule " +
                "(plan_id, recurrence_type, timezone, service_time, generation_lead_hours, status, version, created_by_identity_id, created_at, updated_at, activated_at) " +
                "SELECT plan_id, recurrence_type, timezone, service_time, generation_lead_hours, 'ACTIVE', version, created_by_identity_id, created_at, now(), now() " +
                "FROM subscription_schema.subscription_plan_schedule_draft WHERE plan_id = ? " +
                "ON CONFLICT (plan_id) DO UPDATE SET recurrence_type = EXCLUDED.recurrence_type, timezone = EXCLUDED.timezone, " +
                "service_time = EXCLUDED.service_time, generation_lead_hours = EXCLUDED.generation_lead_hours, status = 'ACTIVE', " +
                "version = EXCLUDED.version, created_by_identity_id = EXCLUDED.created_by_identity_id, updated_at = now(), activated_at = now()",
            planId
        );
        jdbcTemplate.update("DELETE FROM subscription_schema.subscription_plan_schedule_item WHERE plan_id = ?", planId);
        jdbcTemplate.update(
            "INSERT INTO subscription_schema.subscription_plan_schedule_item " +
                "(id, plan_id, menu_item_id, menu_item_name_snapshot, menu_item_category_snapshot, menu_item_food_type_snapshot, " +
                "menu_item_price_snapshot, menu_item_currency_snapshot, quantity, iso_day_of_week, day_of_month, meal_slot_code, service_time, sequence_number, created_at) " +
                "SELECT id, plan_id, menu_item_id, menu_item_name_snapshot, menu_item_category_snapshot, menu_item_food_type_snapshot, " +
                "menu_item_price_snapshot, menu_item_currency_snapshot, quantity, iso_day_of_week, day_of_month, meal_slot_code, service_time, sequence_number, created_at " +
                "FROM subscription_schema.subscription_plan_schedule_draft_item WHERE plan_id = ?",
            planId
        );
        jdbcTemplate.update("DELETE FROM subscription_schema.subscription_plan_schedule_draft WHERE plan_id = ?", planId);
        jdbcTemplate.update(
            "INSERT INTO subscription_schema.subscription_plan_schedule_audit " +
                "(id, plan_id, actor_identity_id, action, schedule_version, reason, created_at) VALUES (?, ?, ?, 'ACTIVATE', ?, ?, now())",
            UUID.randomUUID(), planId, actor, draft.version(), reason
        );
        return findActive(planId).orElseThrow();
    }

    private Optional<PlanScheduleResponse> findDraft(UUID planId) {
        return jdbcTemplate.query(
            "SELECT * FROM subscription_schema.subscription_plan_schedule_draft WHERE plan_id = ?",
            (rs, rowNum) -> mapDraft(rs, listDraftItems(planId)), planId
        ).stream().findFirst();
    }

    private Optional<PlanScheduleResponse> findCurrent(UUID planId) {
        return jdbcTemplate.query(
            "SELECT * FROM subscription_schema.subscription_plan_schedule WHERE plan_id = ?",
            (rs, rowNum) -> mapCurrent(rs, listCurrentItems(planId)), planId
        ).stream().findFirst();
    }

    private List<ScheduleItemResponse> listCurrentItems(UUID planId) {
        return jdbcTemplate.query(
            "SELECT * FROM subscription_schema.subscription_plan_schedule_item WHERE plan_id = ? " +
                "ORDER BY COALESCE(iso_day_of_week, day_of_month), service_time, meal_slot_code, sequence_number, created_at",
            this::mapItem, planId
        );
    }

    private List<ScheduleItemResponse> listDraftItems(UUID planId) {
        return jdbcTemplate.query(
            "SELECT * FROM subscription_schema.subscription_plan_schedule_draft_item WHERE plan_id = ? " +
                "ORDER BY COALESCE(iso_day_of_week, day_of_month), service_time, meal_slot_code, sequence_number, created_at",
            this::mapItem, planId
        );
    }

    private ScheduleItemResponse mapItem(ResultSet rs, int rowNum) throws SQLException {
        return new ScheduleItemResponse(
            rs.getObject("id", UUID.class),
            rs.getObject("menu_item_id", UUID.class),
            rs.getString("menu_item_name_snapshot"),
            rs.getString("menu_item_category_snapshot"),
            rs.getString("menu_item_food_type_snapshot"),
            rs.getBigDecimal("menu_item_price_snapshot"),
            rs.getString("menu_item_currency_snapshot"),
            rs.getInt("quantity"),
            integer(rs, "iso_day_of_week"),
            integer(rs, "day_of_month"),
            rs.getString("meal_slot_code"),
            rs.getObject("service_time", LocalTime.class),
            rs.getInt("sequence_number")
        );
    }

    private PlanScheduleResponse mapCurrent(ResultSet rs, List<ScheduleItemResponse> items) throws SQLException {
        return new PlanScheduleResponse(
            rs.getObject("plan_id", UUID.class), rs.getString("recurrence_type"), rs.getString("timezone"),
            rs.getObject("service_time", LocalTime.class), rs.getInt("generation_lead_hours"), rs.getString("status"),
            rs.getInt("version"), items, instant(rs, "created_at"), instant(rs, "updated_at"),
            instant(rs, "activated_at")
        );
    }

    private PlanScheduleResponse mapDraft(ResultSet rs, List<ScheduleItemResponse> items) throws SQLException {
        return new PlanScheduleResponse(
            rs.getObject("plan_id", UUID.class), rs.getString("recurrence_type"), rs.getString("timezone"),
            rs.getObject("service_time", LocalTime.class), rs.getInt("generation_lead_hours"), "DRAFT",
            rs.getInt("version"), items, instant(rs, "created_at"), instant(rs, "updated_at"), null
        );
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static Integer integer(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    public record PreparedScheduleItem(
        ScheduleItemRequest request,
        String menuItemName,
        String menuItemCategory,
        String menuItemFoodType,
        BigDecimal menuItemPrice,
        String menuItemCurrency
    ) {
    }

    public record PlanOwner(UUID planId, UUID chefIdentityId, String status, String billingPeriod) {}
}
