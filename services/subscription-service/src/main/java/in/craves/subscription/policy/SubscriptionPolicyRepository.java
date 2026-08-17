package in.craves.subscription.policy;

import in.craves.subscription.policy.SubscriptionPolicyModels.PolicyReadinessResponse;
import in.craves.subscription.policy.SubscriptionPolicyModels.PutPolicyRequest;
import in.craves.subscription.policy.SubscriptionPolicyModels.SubscriptionPolicyResponse;
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
public class SubscriptionPolicyRepository {
    private final JdbcTemplate jdbcTemplate;

    public SubscriptionPolicyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<SubscriptionPolicyResponse> findLatest(UUID planId) {
        return jdbcTemplate.query(
            "SELECT * FROM subscription_schema.subscription_plan_policy WHERE plan_id = ? ORDER BY version DESC LIMIT 1",
            this::map,
            planId
        ).stream().findFirst();
    }

    public Optional<SubscriptionPolicyResponse> findActive(UUID planId) {
        return jdbcTemplate.query(
            "SELECT * FROM subscription_schema.subscription_plan_policy WHERE plan_id = ? AND status = 'ACTIVE'",
            this::map,
            planId
        ).stream().findFirst();
    }

    public Optional<SubscriptionPolicyResponse> findPublicActive(UUID planId) {
        return jdbcTemplate.query(
            "SELECT pp.* FROM subscription_schema.subscription_plan_policy pp " +
                "JOIN subscription_schema.subscription_plan p ON p.id = pp.plan_id " +
                "WHERE pp.plan_id = ? AND pp.status = 'ACTIVE' AND p.status = 'ACTIVE'",
            this::map,
            planId
        ).stream().findFirst();
    }

    @Transactional
    public SubscriptionPolicyResponse putDraft(UUID planId, PutPolicyRequest request, UUID actorIdentityId) {
        requirePlan(planId);
        List<SubscriptionPolicyResponse> drafts = jdbcTemplate.query(
            "SELECT * FROM subscription_schema.subscription_plan_policy WHERE plan_id = ? AND status = 'DRAFT' " +
                "ORDER BY version DESC LIMIT 1 FOR UPDATE",
            this::map,
            planId
        );
        UUID policyId;
        int version;
        if (drafts.isEmpty()) {
            Integer current = jdbcTemplate.queryForObject(
                "SELECT COALESCE(MAX(version), 0) FROM subscription_schema.subscription_plan_policy WHERE plan_id = ?",
                Integer.class,
                planId
            );
            version = (current == null ? 0 : current) + 1;
            policyId = UUID.randomUUID();
            jdbcTemplate.update(
                "INSERT INTO subscription_schema.subscription_plan_policy " +
                    "(id, plan_id, version, status, customer_pause_enabled, customer_resume_enabled, " +
                    "customer_cancel_enabled, customer_skip_enabled, pause_cutoff_minutes, resume_lead_minutes, " +
                    "cancel_cutoff_minutes, skip_cutoff_minutes, holiday_policy_reference, unused_meal_policy_reference, " +
                    "refund_policy_reference, notes, created_by_identity_id, created_at, updated_at, activated_at) " +
                    "VALUES (?, ?, ?, 'DRAFT', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now(), NULL)",
                policyId,
                planId,
                version,
                request.customerPauseEnabled(),
                request.customerResumeEnabled(),
                request.customerCancelEnabled(),
                request.customerSkipEnabled(),
                request.pauseCutoffMinutes(),
                request.resumeLeadMinutes(),
                request.cancelCutoffMinutes(),
                request.skipCutoffMinutes(),
                trim(request.holidayPolicyReference()),
                trim(request.unusedMealPolicyReference()),
                trim(request.refundPolicyReference()),
                trim(request.notes()),
                actorIdentityId
            );
        } else {
            SubscriptionPolicyResponse draft = drafts.getFirst();
            policyId = draft.id();
            version = draft.version();
            jdbcTemplate.update(
                "UPDATE subscription_schema.subscription_plan_policy SET " +
                    "customer_pause_enabled = ?, customer_resume_enabled = ?, customer_cancel_enabled = ?, customer_skip_enabled = ?, " +
                    "pause_cutoff_minutes = ?, resume_lead_minutes = ?, cancel_cutoff_minutes = ?, skip_cutoff_minutes = ?, " +
                    "holiday_policy_reference = ?, unused_meal_policy_reference = ?, refund_policy_reference = ?, notes = ?, " +
                    "created_by_identity_id = ?, updated_at = now() WHERE id = ? AND status = 'DRAFT'",
                request.customerPauseEnabled(),
                request.customerResumeEnabled(),
                request.customerCancelEnabled(),
                request.customerSkipEnabled(),
                request.pauseCutoffMinutes(),
                request.resumeLeadMinutes(),
                request.cancelCutoffMinutes(),
                request.skipCutoffMinutes(),
                trim(request.holidayPolicyReference()),
                trim(request.unusedMealPolicyReference()),
                trim(request.refundPolicyReference()),
                trim(request.notes()),
                actorIdentityId,
                policyId
            );
        }
        insertAudit(planId, policyId, actorIdentityId, "PUT_DRAFT", version, "Policy draft saved");
        return findById(policyId).orElseThrow();
    }

    @Transactional
    public SubscriptionPolicyResponse activate(UUID planId, UUID actorIdentityId, String reason) {
        requirePlan(planId);
        SubscriptionPolicyResponse draft = jdbcTemplate.query(
            "SELECT * FROM subscription_schema.subscription_plan_policy WHERE plan_id = ? AND status = 'DRAFT' " +
                "ORDER BY version DESC LIMIT 1 FOR UPDATE",
            this::map,
            planId
        ).stream().findFirst().orElseThrow(() -> new IllegalStateException("No draft policy exists for this plan"));

        jdbcTemplate.update(
            "UPDATE subscription_schema.subscription_plan_policy SET status = 'INACTIVE', updated_at = now() " +
                "WHERE plan_id = ? AND status = 'ACTIVE'",
            planId
        );
        int updated = jdbcTemplate.update(
            "UPDATE subscription_schema.subscription_plan_policy SET status = 'ACTIVE', activated_at = now(), updated_at = now() " +
                "WHERE id = ? AND status = 'DRAFT'",
            draft.id()
        );
        if (updated != 1) {
            throw new IllegalStateException("Draft policy activation lost its state lock");
        }
        insertAudit(planId, draft.id(), actorIdentityId, "ACTIVATE", draft.version(), reason);
        return findById(draft.id()).orElseThrow();
    }

    public PolicyReadinessResponse readiness(UUID planId) {
        return jdbcTemplate.query(
            "SELECT p.id, (p.chef_identity_id IS NOT NULL) AS chef_assigned, " +
                "EXISTS (SELECT 1 FROM subscription_schema.subscription_plan_schedule s WHERE s.plan_id = p.id AND s.status = 'ACTIVE') AS active_schedule, " +
                "EXISTS (SELECT 1 FROM subscription_schema.subscription_plan_policy pp WHERE pp.plan_id = p.id AND pp.status = 'ACTIVE') AS active_policy " +
                "FROM subscription_schema.subscription_plan p WHERE p.id = ?",
            (rs, rowNum) -> {
                boolean schedule = rs.getBoolean("active_schedule");
                boolean policy = rs.getBoolean("active_policy");
                boolean chef = rs.getBoolean("chef_assigned");
                return new PolicyReadinessResponse(planId, schedule, policy, chef, schedule && policy && chef);
            },
            planId
        ).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("Subscription plan was not found"));
    }

    private Optional<SubscriptionPolicyResponse> findById(UUID id) {
        return jdbcTemplate.query(
            "SELECT * FROM subscription_schema.subscription_plan_policy WHERE id = ?",
            this::map,
            id
        ).stream().findFirst();
    }

    private void requirePlan(UUID planId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM subscription_schema.subscription_plan WHERE id = ?",
            Integer.class,
            planId
        );
        if (count == null || count != 1) {
            throw new IllegalArgumentException("Subscription plan was not found");
        }
    }

    private void insertAudit(
        UUID planId,
        UUID policyId,
        UUID actorIdentityId,
        String action,
        int version,
        String reason
    ) {
        jdbcTemplate.update(
            "INSERT INTO subscription_schema.subscription_plan_policy_audit " +
                "(id, plan_id, policy_id, actor_identity_id, action, policy_version, reason, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, now())",
            UUID.randomUUID(), planId, policyId, actorIdentityId, action, version, reason
        );
    }

    private SubscriptionPolicyResponse map(ResultSet rs, int rowNum) throws SQLException {
        return new SubscriptionPolicyResponse(
            rs.getObject("id", UUID.class),
            rs.getObject("plan_id", UUID.class),
            rs.getInt("version"),
            rs.getString("status"),
            rs.getBoolean("customer_pause_enabled"),
            rs.getBoolean("customer_resume_enabled"),
            rs.getBoolean("customer_cancel_enabled"),
            rs.getBoolean("customer_skip_enabled"),
            integer(rs, "pause_cutoff_minutes"),
            integer(rs, "resume_lead_minutes"),
            integer(rs, "cancel_cutoff_minutes"),
            integer(rs, "skip_cutoff_minutes"),
            rs.getString("holiday_policy_reference"),
            rs.getString("unused_meal_policy_reference"),
            rs.getString("refund_policy_reference"),
            rs.getString("notes"),
            instant(rs, "created_at"),
            instant(rs, "updated_at"),
            instant(rs, "activated_at")
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

    private static String trim(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
