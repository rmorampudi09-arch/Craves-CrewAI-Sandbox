package in.craves.subscription.occurrence;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class OccurrenceRepository {
    private final JdbcTemplate jdbcTemplate;

    public OccurrenceRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public List<ClaimedSubscription> claimDue(int horizonDays, int staleLockMinutes, int batchSize) {
        UUID lockToken = UUID.randomUUID();
        String sql = """
            WITH candidates AS (
                SELECT cs.id
                  FROM subscription_schema.customer_subscription cs
                  JOIN subscription_schema.subscription_plan_schedule ps ON ps.plan_id = cs.plan_id
                 WHERE cs.status = 'ACTIVE'
                   AND cs.next_service_date IS NOT NULL
                   AND cs.next_service_date <= current_date + ?
                   AND ps.status = 'ACTIVE'
                   AND (cs.generation_lock_token IS NULL OR cs.generation_locked_at < now() - (? * INTERVAL '1 minute'))
                 ORDER BY cs.next_service_date, cs.created_at
                 FOR UPDATE OF cs SKIP LOCKED
                 LIMIT ?
            )
            UPDATE subscription_schema.customer_subscription cs
               SET generation_lock_token = ?, generation_locked_at = now()
              FROM candidates c
             WHERE cs.id = c.id
            RETURNING cs.id, cs.customer_identity_id, cs.plan_id, cs.chef_identity_id,
                      cs.delivery_address_id, cs.next_service_date
            """;
        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new ClaimedSubscription(
                rs.getObject("id", UUID.class),
                rs.getObject("customer_identity_id", UUID.class),
                rs.getObject("plan_id", UUID.class),
                rs.getObject("chef_identity_id", UUID.class),
                rs.getObject("delivery_address_id", UUID.class),
                rs.getObject("next_service_date", LocalDate.class),
                lockToken
            ),
            horizonDays,
            staleLockMinutes,
            batchSize,
            lockToken
        );
    }

    public Optional<ActiveSchedule> findActiveSchedule(UUID planId) {
        return jdbcTemplate.query(
            "SELECT plan_id, recurrence_type, timezone, service_time, generation_lead_hours, version " +
                "FROM subscription_schema.subscription_plan_schedule WHERE plan_id = ? AND status = 'ACTIVE'",
            (rs, rowNum) -> new ActiveSchedule(
                rs.getObject("plan_id", UUID.class),
                rs.getString("recurrence_type"),
                rs.getString("timezone"),
                rs.getObject("service_time", LocalTime.class),
                rs.getInt("generation_lead_hours"),
                rs.getInt("version"),
                findScheduleItems(planId)
            ),
            planId
        ).stream().findFirst();
    }

    public Optional<SkipRequest> findRequestedSkip(UUID subscriptionId, LocalDate serviceDate) {
        return jdbcTemplate.query(
            "SELECT id, actor_identity_id, reason FROM subscription_schema.subscription_skip_request " +
                "WHERE subscription_id = ? AND service_date = ? AND status IN ('REQUESTED','APPLIED')",
            (rs, rowNum) -> new SkipRequest(
                rs.getObject("id", UUID.class),
                rs.getObject("actor_identity_id", UUID.class),
                rs.getString("reason")
            ),
            subscriptionId,
            serviceDate
        ).stream().findFirst();
    }

    @Transactional
    public UUID createOccurrence(
        ClaimedSubscription subscription,
        ActiveSchedule schedule,
        LocalDate serviceDate,
        String mealSlotCode,
        Instant serviceAt,
        List<ScheduleItem> matchingItems,
        SkipRequest skipRequest
    ) {
        lockActiveSubscription(subscription.subscriptionId());
        boolean paidCycle = skipRequest == null && hasPaidInvoiceCovering(subscription.subscriptionId(), serviceDate);
        UUID occurrenceId = UUID.randomUUID();
        String initialStatus = skipRequest != null ? "SKIPPED" : paidCycle ? "READY_FOR_ORDER" : "BILLING_PENDING";
        int inserted = jdbcTemplate.update(
            "INSERT INTO subscription_schema.subscription_occurrence " +
                "(id, subscription_id, plan_id, customer_identity_id, chef_identity_id, delivery_address_id, service_date, meal_slot_code, service_at, schedule_version, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now()) " +
                "ON CONFLICT (subscription_id, service_date, meal_slot_code) DO NOTHING",
            occurrenceId,
            subscription.subscriptionId(),
            subscription.planId(),
            subscription.customerIdentityId(),
            subscription.chefIdentityId(),
            subscription.deliveryAddressId(),
            serviceDate,
            mealSlotCode,
            serviceAt,
            schedule.version(),
            initialStatus
        );
        if (inserted != 1) {
            return null;
        }
        for (ScheduleItem item : matchingItems) {
            jdbcTemplate.update(
                "INSERT INTO subscription_schema.subscription_occurrence_item " +
                    "(id, occurrence_id, menu_item_id, quantity, sequence_number, created_at) VALUES (?, ?, ?, ?, ?, now())",
                UUID.randomUUID(), occurrenceId, item.menuItemId(), item.quantity(), item.sequenceNumber()
            );
        }
        if (skipRequest == null) {
            String reason = paidCycle
                ? "Occurrence generated inside an already-paid billing cycle"
                : "Occurrence generated and awaiting billing-cycle payment";
            jdbcTemplate.update(
                "INSERT INTO subscription_schema.subscription_occurrence_history " +
                    "(id, occurrence_id, old_status, new_status, reason, source, created_at) " +
                    "VALUES (?, ?, NULL, ?, ?, 'SCHEDULER', now())",
                UUID.randomUUID(), occurrenceId, initialStatus, reason
            );
        } else {
            jdbcTemplate.update(
                "INSERT INTO subscription_schema.subscription_occurrence_history " +
                    "(id, occurrence_id, old_status, new_status, reason, actor_identity_id, source, created_at) " +
                    "VALUES (?, ?, NULL, 'SKIPPED', ?, ?, 'CUSTOMER_SKIP', now())",
                UUID.randomUUID(), occurrenceId,
                skipRequest.reason() == null ? "Customer skip request applied during occurrence generation" : skipRequest.reason(),
                skipRequest.actorIdentityId()
            );
            jdbcTemplate.update(
                "UPDATE subscription_schema.subscription_skip_request SET status = 'APPLIED', " +
                    "occurrence_id = COALESCE(occurrence_id, ?), applied_at = COALESCE(applied_at, now()), updated_at = now() " +
                    "WHERE id = ? AND status IN ('REQUESTED', 'APPLIED')",
                occurrenceId,
                skipRequest.id()
            );
        }
        return occurrenceId;
    }

    boolean hasPaidInvoiceCovering(UUID subscriptionId, LocalDate serviceDate) {
        Boolean paid = jdbcTemplate.queryForObject(
            "SELECT EXISTS (" +
                "SELECT 1 FROM subscription_schema.subscription_invoice " +
                "WHERE subscription_id = ? AND status = 'PAID' AND cycle_start <= ? AND cycle_end > ?" +
                ")",
            Boolean.class,
            subscriptionId,
            serviceDate,
            serviceDate
        );
        return Boolean.TRUE.equals(paid);
    }

    private void lockActiveSubscription(UUID subscriptionId) {
        String status = jdbcTemplate.queryForObject(
            "SELECT status FROM subscription_schema.customer_subscription WHERE id = ? FOR UPDATE",
            String.class,
            subscriptionId
        );
        if (!"ACTIVE".equals(status)) {
            throw new IllegalStateException("Subscription is no longer ACTIVE during occurrence generation");
        }
    }

    public void releaseAndAdvance(ClaimedSubscription subscription, LocalDate nextServiceDate) {
        int updated = jdbcTemplate.update(
            "UPDATE subscription_schema.customer_subscription SET next_service_date = ?, generation_lock_token = NULL, " +
                "generation_locked_at = NULL, updated_at = now() WHERE id = ? AND generation_lock_token = ?",
            nextServiceDate,
            subscription.subscriptionId(),
            subscription.lockToken()
        );
        if (updated != 1) {
            throw new IllegalStateException("Subscription generation claim was lost");
        }
    }

    public void releaseAfterFailure(ClaimedSubscription subscription) {
        jdbcTemplate.update(
            "UPDATE subscription_schema.customer_subscription SET generation_lock_token = NULL, generation_locked_at = NULL " +
                "WHERE id = ? AND generation_lock_token = ?",
            subscription.subscriptionId(), subscription.lockToken()
        );
    }

    private List<ScheduleItem> findScheduleItems(UUID planId) {
        return jdbcTemplate.query(
            "SELECT menu_item_id, quantity, iso_day_of_week, day_of_month, meal_slot_code, service_time, sequence_number " +
                "FROM subscription_schema.subscription_plan_schedule_item WHERE plan_id = ? " +
                "ORDER BY COALESCE(iso_day_of_week, day_of_month), service_time, meal_slot_code, sequence_number",
            (rs, rowNum) -> new ScheduleItem(
                rs.getObject("menu_item_id", UUID.class),
                rs.getInt("quantity"),
                nullableInteger(rs, "iso_day_of_week"),
                nullableInteger(rs, "day_of_month"),
                rs.getString("meal_slot_code"),
                rs.getObject("service_time", LocalTime.class),
                rs.getInt("sequence_number")
            ),
            planId
        );
    }

    private static Integer nullableInteger(java.sql.ResultSet rs, String column) throws java.sql.SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    public record ClaimedSubscription(
        UUID subscriptionId,
        UUID customerIdentityId,
        UUID planId,
        UUID chefIdentityId,
        UUID deliveryAddressId,
        LocalDate serviceDate,
        UUID lockToken
    ) {
    }

    public record ActiveSchedule(
        UUID planId,
        String recurrenceType,
        String timezone,
        LocalTime serviceTime,
        int generationLeadHours,
        int version,
        List<ScheduleItem> items
    ) {
    }

    public record ScheduleItem(
        UUID menuItemId,
        int quantity,
        Integer isoDayOfWeek,
        Integer dayOfMonth,
        String mealSlotCode,
        LocalTime serviceTime,
        int sequenceNumber
    ) {
    }

    public record SkipRequest(UUID id, UUID actorIdentityId, String reason) {
    }
}
