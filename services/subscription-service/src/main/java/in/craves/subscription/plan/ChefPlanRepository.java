package in.craves.subscription.plan;

import in.craves.subscription.exception.ApiException;
import in.craves.subscription.plan.ChefPlanModels.ChefPlanInput;
import in.craves.subscription.plan.ChefPlanModels.ChefPlanResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ChefPlanRepository {
    private final JdbcTemplate jdbcTemplate;

    public ChefPlanRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public ChefPlanResponse create(UUID chefIdentityId, String planCode, ChefPlanInput input) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO subscription_schema.subscription_plan " +
                "(id, plan_code, chef_identity_id, name, description, billing_period, amount, currency, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', now(), now())",
            id, planCode, chefIdentityId, input.name().trim(), trim(input.description()),
            input.billingPeriod().trim().toUpperCase(), input.amount(), currency(input.currency())
        );
        audit(id, chefIdentityId, "CHEF_CREATE", null, "DRAFT", "Chef created meal plan draft");
        return findOwned(id, chefIdentityId).orElseThrow();
    }

    public List<ChefPlanResponse> listOwned(UUID chefIdentityId) {
        return jdbcTemplate.query(
            SELECT + " WHERE chef_identity_id = ? ORDER BY created_at DESC",
            this::map,
            chefIdentityId
        );
    }

    public Optional<ChefPlanResponse> findOwned(UUID planId, UUID chefIdentityId) {
        return jdbcTemplate.query(
            SELECT + " WHERE id = ? AND chef_identity_id = ?",
            this::map,
            planId,
            chefIdentityId
        ).stream().findFirst();
    }

    public Optional<ChefPlanResponse> find(UUID planId) {
        return jdbcTemplate.query(
            SELECT + " WHERE id = ?",
            this::map,
            planId
        ).stream().findFirst();
    }

    @Transactional
    public ChefPlanResponse update(UUID planId, UUID chefIdentityId, ChefPlanInput input) {
        ChefPlanResponse existing = requireOwned(planId, chefIdentityId);
        if (!"DRAFT".equals(existing.status()) && !"REJECTED".equals(existing.status())) {
            throw ApiException.conflict(
                "PLAN_NOT_EDITABLE",
                "Only draft or rejected plans can be edited"
            );
        }
        int updated = jdbcTemplate.update(
            "UPDATE subscription_schema.subscription_plan SET name = ?, description = ?, billing_period = ?, amount = ?, currency = ?, " +
                "status = 'DRAFT', submitted_at = NULL, reviewed_at = NULL, reviewed_by_identity_id = NULL, review_reason = NULL, updated_at = now() " +
                "WHERE id = ? AND chef_identity_id = ? AND status IN ('DRAFT','REJECTED')",
            input.name().trim(), trim(input.description()), input.billingPeriod().trim().toUpperCase(), input.amount(),
            currency(input.currency()), planId, chefIdentityId
        );
        if (updated != 1) {
            throw ApiException.conflict("PLAN_EDIT_CONFLICT", "Meal plan changed while it was being edited");
        }
        audit(planId, chefIdentityId, "CHEF_EDIT", existing.status(), "DRAFT", "Chef updated meal plan content");
        return requireOwned(planId, chefIdentityId);
    }

    @Transactional
    public ChefPlanResponse submit(UUID planId, UUID chefIdentityId, String note) {
        ChefPlanResponse existing = requireOwned(planId, chefIdentityId);
        if (!"DRAFT".equals(existing.status()) && !"REJECTED".equals(existing.status())) {
            throw ApiException.conflict(
                "PLAN_NOT_SUBMITTABLE",
                "Only draft or rejected plans can be submitted for review"
            );
        }
        int updated = jdbcTemplate.update(
            "UPDATE subscription_schema.subscription_plan SET status = 'PENDING_APPROVAL', submitted_at = now(), " +
                "reviewed_at = NULL, reviewed_by_identity_id = NULL, review_reason = NULL, updated_at = now() " +
                "WHERE id = ? AND chef_identity_id = ? AND status IN ('DRAFT','REJECTED')",
            planId, chefIdentityId
        );
        if (updated != 1) {
            throw ApiException.conflict("PLAN_SUBMIT_CONFLICT", "Meal plan changed while it was being submitted");
        }
        audit(planId, chefIdentityId, "CHEF_SUBMIT", existing.status(), "PENDING_APPROVAL", trim(note));
        return requireOwned(planId, chefIdentityId);
    }

    @Transactional
    public ChefPlanResponse review(UUID planId, UUID adminIdentityId, boolean approved, String reason) {
        ChefPlanResponse existing = find(planId)
            .orElseThrow(() -> ApiException.notFound("PLAN_NOT_FOUND", "Subscription meal plan was not found"));
        if (!"PENDING_APPROVAL".equals(existing.status())) {
            throw ApiException.conflict("PLAN_NOT_PENDING_APPROVAL", "Only submitted plans can be reviewed");
        }
        String newStatus = approved ? "ACTIVE" : "REJECTED";
        int updated = jdbcTemplate.update(
            "UPDATE subscription_schema.subscription_plan SET status = ?, reviewed_at = now(), reviewed_by_identity_id = ?, " +
                "review_reason = ?, updated_at = now() WHERE id = ? AND status = 'PENDING_APPROVAL'",
            newStatus, adminIdentityId, reason.trim(), planId
        );
        if (updated != 1) {
            throw ApiException.conflict("PLAN_REVIEW_CONFLICT", "Meal plan was reviewed by another request");
        }
        audit(planId, adminIdentityId, approved ? "ADMIN_APPROVE" : "ADMIN_REJECT", existing.status(), newStatus, reason.trim());
        return find(planId).orElseThrow();
    }

    public ChefPlanResponse requireOwned(UUID planId, UUID chefIdentityId) {
        return findOwned(planId, chefIdentityId)
            .orElseThrow(() -> ApiException.notFound("PLAN_NOT_FOUND", "Meal plan was not found in your Chef workspace"));
    }

    private void audit(UUID planId, UUID actor, String action, String oldStatus, String newStatus, String reason) {
        jdbcTemplate.update(
            "INSERT INTO subscription_schema.subscription_plan_audit " +
                "(id, plan_id, actor_identity_id, action, old_status, new_status, reason, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, now())",
            UUID.randomUUID(), planId, actor, action, oldStatus, newStatus, trim(reason)
        );
    }

    private ChefPlanResponse map(ResultSet rs, int rowNum) throws SQLException {
        return new ChefPlanResponse(
            rs.getObject("id", UUID.class),
            rs.getString("plan_code"),
            rs.getString("name"),
            rs.getString("description"),
            rs.getString("billing_period"),
            rs.getBigDecimal("amount"),
            rs.getString("currency"),
            rs.getString("status"),
            rs.getString("review_reason"),
            instant(rs, "submitted_at"),
            instant(rs, "reviewed_at"),
            instant(rs, "created_at"),
            instant(rs, "updated_at")
        );
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
        return value == null ? null : value.toInstant();
    }

    private static String trim(String value) {
        if (value == null) return null;
        String result = value.trim();
        return result.isEmpty() ? null : result;
    }

    private static String currency(String value) {
        return value == null || value.isBlank() ? "INR" : value.trim().toUpperCase();
    }

    private static final String SELECT =
        "SELECT id, plan_code, chef_identity_id, name, description, billing_period, amount, currency, status, " +
        "submitted_at, reviewed_at, reviewed_by_identity_id, review_reason, created_at, updated_at " +
        "FROM subscription_schema.subscription_plan";
}
