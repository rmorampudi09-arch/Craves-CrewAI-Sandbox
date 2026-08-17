package in.craves.subscription.repository;

import in.craves.subscription.exception.ApiException;
import in.craves.subscription.web.ApiDtos.PlanResponse;
import in.craves.subscription.web.ApiDtos.SubscriptionResponse;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SubscriptionRepository {
    private final JdbcTemplate jdbcTemplate;

    public SubscriptionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public PlanResponse createPlan(
        String planCode,
        UUID chefIdentityId,
        String name,
        String description,
        String billingPeriod,
        BigDecimal amount,
        String currency,
        UUID actorIdentityId
    ) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO subscription_schema.subscription_plan " +
                "(id, plan_code, chef_identity_id, name, description, billing_period, amount, currency, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', now(), now())",
            id,
            planCode,
            chefIdentityId,
            name,
            description,
            billingPeriod,
            amount,
            currency
        );
        insertPlanAudit(id, actorIdentityId, "CREATE", null, "DRAFT");
        return findPlanById(id)
            .orElseThrow(() -> ApiException.notFound("PLAN_NOT_FOUND", "Subscription plan was not found after creation"));
    }

    public List<PlanResponse> listPlans(boolean activeOnly) {
        String sql = "SELECT p.id, p.plan_code, p.chef_identity_id, p.name, p.description, p.billing_period, " +
            "p.amount, p.currency, p.status, p.created_at, p.updated_at " +
            "FROM subscription_schema.subscription_plan p ";
        if (activeOnly) {
            sql += "WHERE p.status = 'ACTIVE' AND p.chef_identity_id IS NOT NULL " +
                "AND EXISTS (SELECT 1 FROM subscription_schema.subscription_plan_schedule s " +
                "WHERE s.plan_id = p.id AND s.status = 'ACTIVE') " +
                "AND EXISTS (SELECT 1 FROM subscription_schema.subscription_plan_policy pp " +
                "WHERE pp.plan_id = p.id AND pp.status = 'ACTIVE') ";
        }
        sql += "ORDER BY p.created_at DESC";
        return jdbcTemplate.query(sql, this::mapPlan);
    }

    public List<PlanResponse> listPlansForChef(UUID chefIdentityId) {
        return jdbcTemplate.query(
            "SELECT id, plan_code, chef_identity_id, name, description, billing_period, amount, currency, status, created_at, updated_at " +
                "FROM subscription_schema.subscription_plan WHERE chef_identity_id = ? ORDER BY created_at DESC",
            this::mapPlan,
            chefIdentityId
        );
    }

    public Optional<PlanResponse> findPlanById(UUID id) {
        List<PlanResponse> rows = jdbcTemplate.query(
            "SELECT id, plan_code, chef_identity_id, name, description, billing_period, amount, currency, status, created_at, updated_at " +
                "FROM subscription_schema.subscription_plan WHERE id = ?",
            this::mapPlan,
            id
        );
        return rows.stream().findFirst();
    }

    public Optional<PlanResponse> findActivePlanById(UUID id) {
        List<PlanResponse> rows = jdbcTemplate.query(
            "SELECT p.id, p.plan_code, p.chef_identity_id, p.name, p.description, p.billing_period, p.amount, " +
                "p.currency, p.status, p.created_at, p.updated_at FROM subscription_schema.subscription_plan p " +
                "WHERE p.id = ? AND p.status = 'ACTIVE' AND p.chef_identity_id IS NOT NULL " +
                "AND EXISTS (SELECT 1 FROM subscription_schema.subscription_plan_schedule s " +
                "WHERE s.plan_id = p.id AND s.status = 'ACTIVE') " +
                "AND EXISTS (SELECT 1 FROM subscription_schema.subscription_plan_policy pp " +
                "WHERE pp.plan_id = p.id AND pp.status = 'ACTIVE')",
            this::mapPlan,
            id
        );
        return rows.stream().findFirst();
    }

    @Transactional
    public PlanResponse updatePlanStatus(UUID planId, String status, UUID actorIdentityId) {
        PlanResponse existing = findPlanById(planId)
            .orElseThrow(() -> ApiException.notFound("PLAN_NOT_FOUND", "Subscription plan was not found"));
        if ("ACTIVE".equals(status)) {
            assertActivationReady(planId);
        }
        int updated = jdbcTemplate.update(
            "UPDATE subscription_schema.subscription_plan SET status = ?, updated_at = now() WHERE id = ?",
            status,
            planId
        );
        if (updated == 0) {
            throw ApiException.notFound("PLAN_NOT_FOUND", "Subscription plan was not found");
        }
        if (!existing.status().equals(status)) {
            insertPlanAudit(planId, actorIdentityId, "STATUS_CHANGE", existing.status(), status);
        }
        return findPlanById(planId)
            .orElseThrow(() -> ApiException.notFound("PLAN_NOT_FOUND", "Subscription plan was not found"));
    }

    @Transactional
    public PlanResponse updatePlanStatusForChef(UUID planId, UUID chefIdentityId, String status, UUID actorIdentityId) {
        PlanResponse existing = findPlanById(planId)
            .orElseThrow(() -> ApiException.notFound("PLAN_NOT_FOUND", "Subscription plan was not found"));
        if (existing.chefIdentityId() == null || !existing.chefIdentityId().equals(chefIdentityId)) {
            throw ApiException.forbidden("PLAN_ACCESS_DENIED", "You cannot manage another chef's subscription plan");
        }
        if ("ACTIVE".equals(status)) {
            assertActivationReady(planId);
        }
        int updated = jdbcTemplate.update(
            "UPDATE subscription_schema.subscription_plan SET status = ?, updated_at = now() WHERE id = ? AND chef_identity_id = ?",
            status,
            planId,
            chefIdentityId
        );
        if (updated == 0) {
            throw ApiException.forbidden("PLAN_ACCESS_DENIED", "You cannot manage another chef's subscription plan");
        }
        if (!existing.status().equals(status)) {
            insertPlanAudit(planId, actorIdentityId, "STATUS_CHANGE", existing.status(), status);
        }
        return findPlanById(planId)
            .orElseThrow(() -> ApiException.notFound("PLAN_NOT_FOUND", "Subscription plan was not found"));
    }

    @Transactional
    public SubscriptionResponse createSubscription(
        UUID customerIdentityId,
        PlanResponse plan,
        LocalDate startDate,
        UUID deliveryAddressId,
        String notes,
        String enrollmentIdempotencyKey
    ) {
        Optional<SubscriptionResponse> existing = findSubscriptionByEnrollmentKey(customerIdentityId, enrollmentIdempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        UUID id = UUID.randomUUID();
        int inserted = jdbcTemplate.update(
            "INSERT INTO subscription_schema.customer_subscription " +
                "(id, customer_identity_id, plan_id, chef_identity_id, status, start_date, next_service_date, " +
                "delivery_address_id, notes, enrollment_idempotency_key, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 'PENDING_PAYMENT', ?, ?, ?, ?, ?, now(), now()) " +
                "ON CONFLICT (customer_identity_id, enrollment_idempotency_key) " +
                "WHERE enrollment_idempotency_key IS NOT NULL DO NOTHING",
            id,
            customerIdentityId,
            plan.id(),
            plan.chefIdentityId(),
            startDate,
            startDate,
            deliveryAddressId,
            notes,
            enrollmentIdempotencyKey
        );
        if (inserted == 1) {
            insertHistory(id, null, "PENDING_PAYMENT", "Subscription created and waiting for payment verification", customerIdentityId);
            return findSubscriptionById(id)
                .orElseThrow(() -> ApiException.notFound("SUBSCRIPTION_NOT_FOUND", "Subscription was not found after creation"));
        }
        return findSubscriptionByEnrollmentKey(customerIdentityId, enrollmentIdempotencyKey)
            .orElseThrow(() -> ApiException.conflict(
                "SUBSCRIPTION_IDEMPOTENCY_CONFLICT",
                "Enrollment idempotency key was claimed by another request"
            ));
    }

    public Optional<SubscriptionResponse> findSubscriptionByEnrollmentKey(UUID customerIdentityId, String key) {
        if (key == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
            "SELECT id, customer_identity_id, plan_id, chef_identity_id, status, start_date, end_date, " +
                "next_service_date, delivery_address_id, notes, created_at, updated_at " +
                "FROM subscription_schema.customer_subscription WHERE customer_identity_id = ? AND enrollment_idempotency_key = ?",
            this::mapSubscription,
            customerIdentityId,
            key
        ).stream().findFirst();
    }

    public List<SubscriptionResponse> listCustomerSubscriptions(UUID customerIdentityId) {
        return jdbcTemplate.query(
            "SELECT id, customer_identity_id, plan_id, chef_identity_id, status, start_date, end_date, next_service_date, delivery_address_id, notes, created_at, updated_at " +
                "FROM subscription_schema.customer_subscription WHERE customer_identity_id = ? ORDER BY created_at DESC LIMIT 200",
            this::mapSubscription,
            customerIdentityId
        );
    }

    public Optional<SubscriptionResponse> findSubscriptionById(UUID id) {
        List<SubscriptionResponse> rows = jdbcTemplate.query(
            "SELECT id, customer_identity_id, plan_id, chef_identity_id, status, start_date, end_date, next_service_date, delivery_address_id, notes, created_at, updated_at " +
                "FROM subscription_schema.customer_subscription WHERE id = ?",
            this::mapSubscription,
            id
        );
        return rows.stream().findFirst();
    }

    @Transactional
    public SubscriptionResponse updateSubscriptionStatus(UUID id, String newStatus, String reason, UUID actorIdentityId) {
        SubscriptionResponse existing = findSubscriptionById(id)
            .orElseThrow(() -> ApiException.notFound("SUBSCRIPTION_NOT_FOUND", "Subscription was not found"));
        int updated = jdbcTemplate.update(
            "UPDATE subscription_schema.customer_subscription SET status = ?, updated_at = now() WHERE id = ?",
            newStatus,
            id
        );
        if (updated == 0) {
            throw ApiException.notFound("SUBSCRIPTION_NOT_FOUND", "Subscription was not found");
        }
        if (!existing.status().equals(newStatus)) {
            insertHistory(id, existing.status(), newStatus, reason, actorIdentityId);
        }
        return findSubscriptionById(id)
            .orElseThrow(() -> ApiException.notFound("SUBSCRIPTION_NOT_FOUND", "Subscription was not found"));
    }

    private void assertActivationReady(UUID planId) {
        Integer ready = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM subscription_schema.subscription_plan p WHERE p.id = ? " +
                "AND p.chef_identity_id IS NOT NULL " +
                "AND EXISTS (SELECT 1 FROM subscription_schema.subscription_plan_schedule s " +
                "WHERE s.plan_id = p.id AND s.status = 'ACTIVE') " +
                "AND EXISTS (SELECT 1 FROM subscription_schema.subscription_plan_policy pp " +
                "WHERE pp.plan_id = p.id AND pp.status = 'ACTIVE')",
            Integer.class,
            planId
        );
        if (ready == null || ready != 1) {
            throw ApiException.conflict(
                "PLAN_NOT_READY_FOR_ACTIVATION",
                "Plan activation requires an assigned chef, active meal schedule and active administrator policy"
            );
        }
    }

    private void insertPlanAudit(UUID planId, UUID actorIdentityId, String action, String oldStatus, String newStatus) {
        jdbcTemplate.update(
            "INSERT INTO subscription_schema.subscription_plan_audit " +
                "(id, plan_id, actor_identity_id, action, old_status, new_status, created_at) VALUES (?, ?, ?, ?, ?, ?, now())",
            UUID.randomUUID(),
            planId,
            actorIdentityId,
            action,
            oldStatus,
            newStatus
        );
    }

    private void insertHistory(UUID subscriptionId, String oldStatus, String newStatus, String reason, UUID actorIdentityId) {
        jdbcTemplate.update(
            "INSERT INTO subscription_schema.subscription_status_history " +
                "(subscription_id, old_status, new_status, reason, actor_identity_id, created_at) VALUES (?, ?, ?, ?, ?, now())",
            subscriptionId,
            oldStatus,
            newStatus,
            reason,
            actorIdentityId
        );
    }

    private PlanResponse mapPlan(ResultSet rs, int rowNum) throws SQLException {
        return new PlanResponse(
            rs.getObject("id", UUID.class),
            rs.getString("plan_code"),
            rs.getObject("chef_identity_id", UUID.class),
            rs.getString("name"),
            rs.getString("description"),
            rs.getString("billing_period"),
            rs.getBigDecimal("amount"),
            rs.getString("currency"),
            rs.getString("status"),
            rs.getObject("created_at", Instant.class),
            rs.getObject("updated_at", Instant.class)
        );
    }

    private SubscriptionResponse mapSubscription(ResultSet rs, int rowNum) throws SQLException {
        return new SubscriptionResponse(
            rs.getObject("id", UUID.class),
            rs.getObject("customer_identity_id", UUID.class),
            rs.getObject("plan_id", UUID.class),
            rs.getObject("chef_identity_id", UUID.class),
            rs.getString("status"),
            rs.getObject("start_date", LocalDate.class),
            rs.getObject("end_date", LocalDate.class),
            rs.getObject("next_service_date", LocalDate.class),
            rs.getObject("delivery_address_id", UUID.class),
            rs.getString("notes"),
            rs.getObject("created_at", Instant.class),
            rs.getObject("updated_at", Instant.class)
        );
    }
}
