package in.craves.subscription.lifecycle;

import in.craves.subscription.lifecycle.SubscriptionLifecycleModels.AdminSubscriptionPage;
import in.craves.subscription.lifecycle.SubscriptionLifecycleModels.AdminSubscriptionSummary;
import in.craves.subscription.lifecycle.SubscriptionLifecycleModels.CustomerOccurrenceResponse;
import in.craves.subscription.lifecycle.SubscriptionLifecycleModels.OccurrenceItemResponse;
import in.craves.subscription.lifecycle.SubscriptionLifecycleModels.SkipRequestResponse;
import in.craves.subscription.lifecycle.SubscriptionLifecycleModels.SubscriptionStatusHistoryResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SubscriptionLifecycleRepository {
    private static final List<String> CANCELLABLE_OCCURRENCE_STATUSES = List.of(
        "BILLING_PENDING", "PAYMENT_PENDING", "READY_FOR_ORDER"
    );

    private final JdbcTemplate jdbcTemplate;

    public SubscriptionLifecycleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<OwnedSubscription> findOwned(UUID subscriptionId, UUID customerIdentityId) {
        return jdbcTemplate.query(
            "SELECT id, customer_identity_id, plan_id, status, next_service_date " +
                "FROM subscription_schema.customer_subscription WHERE id = ? AND customer_identity_id = ?",
            (rs, rowNum) -> new OwnedSubscription(
                rs.getObject("id", UUID.class),
                rs.getObject("customer_identity_id", UUID.class),
                rs.getObject("plan_id", UUID.class),
                rs.getString("status"),
                rs.getObject("next_service_date", LocalDate.class)
            ), subscriptionId, customerIdentityId
        ).stream().findFirst();
    }

    public Optional<ScheduleClock> findActiveScheduleClock(UUID planId) {
        return jdbcTemplate.query(
            "SELECT timezone, service_time FROM subscription_schema.subscription_plan_schedule " +
                "WHERE plan_id = ? AND status = 'ACTIVE'",
            (rs, rowNum) -> new ScheduleClock(
                rs.getString("timezone"), rs.getObject("service_time", LocalTime.class)
            ), planId
        ).stream().findFirst();
    }

    public boolean isScheduledServiceDate(UUID planId, LocalDate serviceDate) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM subscription_schema.subscription_plan_schedule s " +
                "JOIN subscription_schema.subscription_plan_schedule_item i ON i.plan_id = s.plan_id " +
                "WHERE s.plan_id = ? AND s.status = 'ACTIVE' AND " +
                "((s.recurrence_type = 'WEEKLY' AND i.iso_day_of_week = ?) " +
                "OR (s.recurrence_type = 'MONTHLY' AND i.day_of_month = ?))",
            Integer.class, planId, serviceDate.getDayOfWeek().getValue(), serviceDate.getDayOfMonth()
        );
        return count != null && count > 0;
    }

    public Optional<Instant> findOccurrenceServiceAt(UUID subscriptionId, LocalDate serviceDate) {
        return jdbcTemplate.query(
            "SELECT service_at FROM subscription_schema.subscription_occurrence " +
                "WHERE subscription_id = ? AND service_date = ? ORDER BY service_at, meal_slot_code LIMIT 1",
            (rs, rowNum) -> rs.getTimestamp("service_at").toInstant(), subscriptionId, serviceDate
        ).stream().findFirst();
    }

    public List<CustomerOccurrenceResponse> listOccurrences(UUID subscriptionId, UUID customerIdentityId, int limit) {
        if (findOwned(subscriptionId, customerIdentityId).isEmpty()) {
            return List.of();
        }
        return jdbcTemplate.query(
            "SELECT id, service_date, meal_slot_code, service_at, status " +
                "FROM subscription_schema.subscription_occurrence WHERE subscription_id = ? " +
                "ORDER BY service_at DESC, meal_slot_code, id DESC LIMIT ?",
            (rs, rowNum) -> {
                UUID occurrenceId = rs.getObject("id", UUID.class);
                return new CustomerOccurrenceResponse(
                    occurrenceId,
                    rs.getObject("service_date", LocalDate.class),
                    rs.getString("meal_slot_code"),
                    rs.getTimestamp("service_at").toInstant(),
                    rs.getString("status"),
                    listOccurrenceItems(occurrenceId)
                );
            }, subscriptionId, limit
        );
    }

    @Transactional
    public boolean pause(UUID subscriptionId, UUID customerIdentityId, String reason) {
        int updated = jdbcTemplate.update(
            "UPDATE subscription_schema.customer_subscription SET status = 'PAUSED', updated_at = now(), " +
                "generation_lock_token = NULL, generation_locked_at = NULL " +
                "WHERE id = ? AND customer_identity_id = ? AND status = 'ACTIVE'",
            subscriptionId, customerIdentityId
        );
        if (updated != 1) return false;
        insertSubscriptionHistory(subscriptionId, "ACTIVE", "PAUSED", reason, customerIdentityId);
        cancelUndispatchedOccurrences(subscriptionId, customerIdentityId, "CUSTOMER_PAUSE");
        return true;
    }

    @Transactional
    public boolean cancel(UUID subscriptionId, UUID customerIdentityId, String reason) {
        OwnedSubscription locked = lockOwned(subscriptionId, customerIdentityId).orElse(null);
        if (locked == null || !("ACTIVE".equals(locked.status()) || "PAUSED".equals(locked.status()))) return false;
        int updated = jdbcTemplate.update(
            "UPDATE subscription_schema.customer_subscription SET status = 'CANCELLED', end_date = current_date, " +
                "next_service_date = NULL, generation_lock_token = NULL, generation_locked_at = NULL, updated_at = now() " +
                "WHERE id = ? AND customer_identity_id = ? AND status = ?",
            subscriptionId, customerIdentityId, locked.status()
        );
        if (updated != 1) return false;
        insertSubscriptionHistory(subscriptionId, locked.status(), "CANCELLED", reason, customerIdentityId);
        cancelUndispatchedOccurrences(subscriptionId, customerIdentityId, "CUSTOMER_CANCEL");
        return true;
    }

    @Transactional
    public boolean resume(UUID subscriptionId, UUID customerIdentityId, LocalDate resumeDate, String reason) {
        int updated = jdbcTemplate.update(
            "UPDATE subscription_schema.customer_subscription SET status = 'ACTIVE', next_service_date = ?, " +
                "generation_lock_token = NULL, generation_locked_at = NULL, updated_at = now() " +
                "WHERE id = ? AND customer_identity_id = ? AND status = 'PAUSED'",
            resumeDate, subscriptionId, customerIdentityId
        );
        if (updated != 1) return false;
        insertSubscriptionHistory(subscriptionId, "PAUSED", "ACTIVE", reason, customerIdentityId);
        return true;
    }

    @Transactional
    public SkipRequestResponse requestSkip(UUID subscriptionId, UUID customerIdentityId, LocalDate serviceDate, String reason) {
        OwnedSubscription subscription = lockOwned(subscriptionId, customerIdentityId)
            .orElseThrow(() -> new IllegalStateException("SUBSCRIPTION_NOT_FOUND"));
        if (!"ACTIVE".equals(subscription.status())) throw new IllegalStateException("SUBSCRIPTION_NOT_ACTIVE");
        Optional<SkipRequestResponse> existing = findSkipRequest(subscriptionId, serviceDate);
        if (existing.isPresent()) return existing.get();

        List<OccurrenceState> occurrences = lockOccurrences(subscriptionId, serviceDate);
        UUID requestId = UUID.randomUUID();
        if (occurrences.isEmpty()) {
            jdbcTemplate.update(
                "INSERT INTO subscription_schema.subscription_skip_request " +
                    "(id, subscription_id, service_date, status, reason, actor_identity_id, created_at, updated_at) " +
                    "VALUES (?, ?, ?, 'REQUESTED', ?, ?, now(), now())",
                requestId, subscriptionId, serviceDate, reason, customerIdentityId
            );
            return findSkipRequest(subscriptionId, serviceDate).orElseThrow();
        }

        boolean allSkipped = occurrences.stream().allMatch(item -> "SKIPPED".equals(item.status()));
        boolean allSkippable = occurrences.stream().allMatch(item ->
            "SKIPPED".equals(item.status()) || CANCELLABLE_OCCURRENCE_STATUSES.contains(item.status())
        );
        if (!allSkippable) throw new IllegalStateException("OCCURRENCE_NOT_SKIPPABLE");

        UUID firstOccurrenceId = occurrences.getFirst().id();
        if (!allSkipped) {
            for (OccurrenceState current : occurrences) {
                if ("SKIPPED".equals(current.status())) continue;
                jdbcTemplate.update(
                    "UPDATE subscription_schema.subscription_occurrence SET status = 'SKIPPED', " +
                        "order_dispatch_lock_token = NULL, order_dispatch_locked_at = NULL, updated_at = now() WHERE id = ?",
                    current.id()
                );
                jdbcTemplate.update(
                    "INSERT INTO subscription_schema.subscription_occurrence_history " +
                        "(id, occurrence_id, old_status, new_status, reason, actor_identity_id, source, created_at) " +
                        "VALUES (?, ?, ?, 'SKIPPED', ?, ?, 'CUSTOMER_SKIP', now())",
                    UUID.randomUUID(), current.id(), current.status(), reason, customerIdentityId
                );
            }
        }
        jdbcTemplate.update(
            "INSERT INTO subscription_schema.subscription_skip_request " +
                "(id, subscription_id, service_date, status, reason, actor_identity_id, occurrence_id, created_at, applied_at, updated_at) " +
                "VALUES (?, ?, ?, 'APPLIED', ?, ?, ?, now(), now(), now())",
            requestId, subscriptionId, serviceDate, reason, customerIdentityId, firstOccurrenceId
        );
        return findSkipRequest(subscriptionId, serviceDate).orElseThrow();
    }

    public Optional<SkipRequestResponse> findSkipRequest(UUID subscriptionId, LocalDate serviceDate) {
        return jdbcTemplate.query(
            "SELECT id, subscription_id, service_date, status, reason, occurrence_id, created_at, applied_at, updated_at " +
                "FROM subscription_schema.subscription_skip_request WHERE subscription_id = ? AND service_date = ?",
            this::mapSkip, subscriptionId, serviceDate
        ).stream().findFirst();
    }

    public List<SubscriptionStatusHistoryResponse> listHistory(UUID subscriptionId, int limit) {
        return jdbcTemplate.query(
            "SELECT id, old_status, new_status, reason, actor_identity_id, created_at " +
                "FROM subscription_schema.subscription_status_history WHERE subscription_id = ? " +
                "ORDER BY created_at DESC, id DESC LIMIT ?",
            (rs, rowNum) -> new SubscriptionStatusHistoryResponse(
                rs.getObject("id", UUID.class), rs.getString("old_status"), rs.getString("new_status"),
                rs.getString("reason"), rs.getObject("actor_identity_id", UUID.class), rs.getObject("created_at", Instant.class)
            ), subscriptionId, limit
        );
    }

    public AdminSubscriptionPage listAdmin(String status, UUID planId, Instant afterCreatedAt, UUID afterId, int limit) {
        String sql = "SELECT id, customer_identity_id, plan_id, chef_identity_id, status, start_date, end_date, " +
            "next_service_date, delivery_address_id, created_at, updated_at FROM subscription_schema.customer_subscription " +
            "WHERE (CAST(? AS VARCHAR) IS NULL OR status = ?) " +
            "AND (CAST(? AS UUID) IS NULL OR plan_id = ?) " +
            "AND (CAST(? AS TIMESTAMPTZ) IS NULL OR created_at < ? " +
            "OR (created_at = ? AND (CAST(? AS UUID) IS NULL OR id < ?))) " +
            "ORDER BY created_at DESC, id DESC LIMIT ?";
        List<AdminSubscriptionSummary> rows = jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new AdminSubscriptionSummary(
                rs.getObject("id", UUID.class), rs.getObject("customer_identity_id", UUID.class),
                rs.getObject("plan_id", UUID.class), rs.getObject("chef_identity_id", UUID.class),
                rs.getString("status"), rs.getObject("start_date", LocalDate.class),
                rs.getObject("end_date", LocalDate.class), rs.getObject("next_service_date", LocalDate.class),
                rs.getObject("delivery_address_id", UUID.class), rs.getObject("created_at", Instant.class),
                rs.getObject("updated_at", Instant.class)
            ),
            status, status, planId, planId, afterCreatedAt, afterCreatedAt, afterCreatedAt, afterId, afterId, limit + 1
        );
        boolean hasMore = rows.size() > limit;
        List<AdminSubscriptionSummary> items = hasMore ? new ArrayList<>(rows.subList(0, limit)) : rows;
        AdminSubscriptionSummary last = items.isEmpty() ? null : items.getLast();
        return new AdminSubscriptionPage(
            List.copyOf(items), hasMore && last != null ? last.createdAt() : null,
            hasMore && last != null ? last.id() : null, hasMore
        );
    }

    private Optional<OwnedSubscription> lockOwned(UUID subscriptionId, UUID customerIdentityId) {
        return jdbcTemplate.query(
            "SELECT id, customer_identity_id, plan_id, status, next_service_date " +
                "FROM subscription_schema.customer_subscription WHERE id = ? AND customer_identity_id = ? FOR UPDATE",
            (rs, rowNum) -> new OwnedSubscription(
                rs.getObject("id", UUID.class), rs.getObject("customer_identity_id", UUID.class),
                rs.getObject("plan_id", UUID.class), rs.getString("status"), rs.getObject("next_service_date", LocalDate.class)
            ), subscriptionId, customerIdentityId
        ).stream().findFirst();
    }

    private List<OccurrenceState> lockOccurrences(UUID subscriptionId, LocalDate serviceDate) {
        return jdbcTemplate.query(
            "SELECT id, status FROM subscription_schema.subscription_occurrence " +
                "WHERE subscription_id = ? AND service_date = ? ORDER BY service_at, meal_slot_code FOR UPDATE",
            (rs, rowNum) -> new OccurrenceState(rs.getObject("id", UUID.class), rs.getString("status")),
            subscriptionId, serviceDate
        );
    }

    private void cancelUndispatchedOccurrences(UUID subscriptionId, UUID actorIdentityId, String source) {
        List<OccurrenceState> candidates = jdbcTemplate.query(
            "SELECT id, status FROM subscription_schema.subscription_occurrence WHERE subscription_id = ? " +
                "AND status IN ('BILLING_PENDING', 'PAYMENT_PENDING', 'READY_FOR_ORDER') FOR UPDATE",
            (rs, rowNum) -> new OccurrenceState(rs.getObject("id", UUID.class), rs.getString("status")), subscriptionId
        );
        for (OccurrenceState candidate : candidates) {
            jdbcTemplate.update(
                "UPDATE subscription_schema.subscription_occurrence SET status = 'CANCELLED', " +
                    "order_dispatch_lock_token = NULL, order_dispatch_locked_at = NULL, updated_at = now() WHERE id = ?",
                candidate.id()
            );
            jdbcTemplate.update(
                "INSERT INTO subscription_schema.subscription_occurrence_history " +
                    "(id, occurrence_id, old_status, new_status, reason, actor_identity_id, source, created_at) " +
                    "VALUES (?, ?, ?, 'CANCELLED', ?, ?, ?, now())",
                UUID.randomUUID(), candidate.id(), candidate.status(),
                "Subscription lifecycle transition prevented future order dispatch", actorIdentityId, source
            );
        }
    }

    private void insertSubscriptionHistory(UUID subscriptionId, String oldStatus, String newStatus, String reason, UUID actorIdentityId) {
        jdbcTemplate.update(
            "INSERT INTO subscription_schema.subscription_status_history " +
                "(subscription_id, old_status, new_status, reason, actor_identity_id, created_at) VALUES (?, ?, ?, ?, ?, now())",
            subscriptionId, oldStatus, newStatus, reason, actorIdentityId
        );
    }

    private List<OccurrenceItemResponse> listOccurrenceItems(UUID occurrenceId) {
        return jdbcTemplate.query(
            "SELECT menu_item_id, quantity, sequence_number FROM subscription_schema.subscription_occurrence_item " +
                "WHERE occurrence_id = ? ORDER BY sequence_number, id",
            (rs, rowNum) -> new OccurrenceItemResponse(
                rs.getObject("menu_item_id", UUID.class), rs.getInt("quantity"), rs.getInt("sequence_number")
            ), occurrenceId
        );
    }

    private SkipRequestResponse mapSkip(ResultSet rs, int rowNum) throws SQLException {
        return new SkipRequestResponse(
            rs.getObject("id", UUID.class), rs.getObject("subscription_id", UUID.class),
            rs.getObject("service_date", LocalDate.class), rs.getString("status"), rs.getString("reason"),
            rs.getObject("occurrence_id", UUID.class), rs.getObject("created_at", Instant.class),
            rs.getObject("applied_at", Instant.class), rs.getObject("updated_at", Instant.class)
        );
    }

    public record OwnedSubscription(UUID id, UUID customerIdentityId, UUID planId, String status, LocalDate nextServiceDate) {}
    public record ScheduleClock(String timezone, LocalTime serviceTime) {}
    private record OccurrenceState(UUID id, String status) {}
}
