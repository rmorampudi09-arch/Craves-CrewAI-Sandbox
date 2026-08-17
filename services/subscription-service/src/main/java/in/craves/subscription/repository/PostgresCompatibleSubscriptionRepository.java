package in.craves.subscription.repository;

import in.craves.subscription.web.ApiDtos.PlanResponse;
import in.craves.subscription.web.ApiDtos.SubscriptionResponse;
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

/**
 * pgJDBC maps PostgreSQL TIMESTAMP WITH TIME ZONE to OffsetDateTime.
 *
 * <p>The legacy SubscriptionRepository asks ResultSet#getObject for Instant directly,
 * which pgJDBC does not support. This primary repository preserves the existing write
 * behavior while overriding the timestamp-bearing reads with the supported
 * OffsetDateTime -> Instant conversion. It can be folded back into
 * SubscriptionRepository once that file can be edited directly.</p>
 */
@Primary
@Repository
public class PostgresCompatibleSubscriptionRepository extends SubscriptionRepository {
    private final JdbcTemplate jdbcTemplate;

    public PostgresCompatibleSubscriptionRepository(JdbcTemplate jdbcTemplate) {
        super(jdbcTemplate);
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
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

    @Override
    public List<PlanResponse> listPlansForChef(UUID chefIdentityId) {
        return jdbcTemplate.query(
            "SELECT id, plan_code, chef_identity_id, name, description, billing_period, amount, currency, status, created_at, updated_at " +
                "FROM subscription_schema.subscription_plan WHERE chef_identity_id = ? ORDER BY created_at DESC",
            this::mapPlan,
            chefIdentityId
        );
    }

    @Override
    public Optional<PlanResponse> findPlanById(UUID id) {
        return jdbcTemplate.query(
            "SELECT id, plan_code, chef_identity_id, name, description, billing_period, amount, currency, status, created_at, updated_at " +
                "FROM subscription_schema.subscription_plan WHERE id = ?",
            this::mapPlan,
            id
        ).stream().findFirst();
    }

    @Override
    public Optional<PlanResponse> findActivePlanById(UUID id) {
        return jdbcTemplate.query(
            "SELECT p.id, p.plan_code, p.chef_identity_id, p.name, p.description, p.billing_period, p.amount, " +
                "p.currency, p.status, p.created_at, p.updated_at FROM subscription_schema.subscription_plan p " +
                "WHERE p.id = ? AND p.status = 'ACTIVE' AND p.chef_identity_id IS NOT NULL " +
                "AND EXISTS (SELECT 1 FROM subscription_schema.subscription_plan_schedule s " +
                "WHERE s.plan_id = p.id AND s.status = 'ACTIVE') " +
                "AND EXISTS (SELECT 1 FROM subscription_schema.subscription_plan_policy pp " +
                "WHERE pp.plan_id = p.id AND pp.status = 'ACTIVE')",
            this::mapPlan,
            id
        ).stream().findFirst();
    }

    @Override
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

    @Override
    public List<SubscriptionResponse> listCustomerSubscriptions(UUID customerIdentityId) {
        return jdbcTemplate.query(
            "SELECT id, customer_identity_id, plan_id, chef_identity_id, status, start_date, end_date, next_service_date, delivery_address_id, notes, created_at, updated_at " +
                "FROM subscription_schema.customer_subscription WHERE customer_identity_id = ? ORDER BY created_at DESC LIMIT 200",
            this::mapSubscription,
            customerIdentityId
        );
    }

    @Override
    public Optional<SubscriptionResponse> findSubscriptionById(UUID id) {
        return jdbcTemplate.query(
            "SELECT id, customer_identity_id, plan_id, chef_identity_id, status, start_date, end_date, next_service_date, delivery_address_id, notes, created_at, updated_at " +
                "FROM subscription_schema.customer_subscription WHERE id = ?",
            this::mapSubscription,
            id
        ).stream().findFirst();
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
            instant(rs, "created_at"),
            instant(rs, "updated_at")
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
            instant(rs, "created_at"),
            instant(rs, "updated_at")
        );
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }
}
