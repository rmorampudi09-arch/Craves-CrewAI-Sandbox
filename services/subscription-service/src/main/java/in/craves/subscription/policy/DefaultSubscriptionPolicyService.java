package in.craves.subscription.policy;

import in.craves.subscription.exception.ApiException;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DefaultSubscriptionPolicyService {
    private static final String DEFAULT_NOTE =
        "Craves platform default: customer self-service pause, resume, cancel and skip are disabled until explicitly configured.";

    private final JdbcTemplate jdbcTemplate;
    private final SubscriptionPolicyRepository repository;

    public DefaultSubscriptionPolicyService(JdbcTemplate jdbcTemplate, SubscriptionPolicyRepository repository) {
        this.jdbcTemplate = jdbcTemplate;
        this.repository = repository;
    }

    @Transactional
    public void ensureActiveDefault(UUID planId, UUID actorIdentityId) {
        jdbcTemplate.queryForObject(
            "SELECT id FROM subscription_schema.subscription_plan WHERE id = ? FOR UPDATE",
            UUID.class,
            planId
        );

        if (repository.findActive(planId).isPresent()) {
            return;
        }
        if (repository.findLatest(planId).isPresent()) {
            throw ApiException.conflict(
                "PLAN_POLICY_DRAFT_EXISTS",
                "A custom lifecycle policy draft exists and must be activated before this plan can be approved"
            );
        }

        UUID policyId = UUID.randomUUID();
        jdbcTemplate.update(
            """
            INSERT INTO subscription_schema.subscription_plan_policy (
                id, plan_id, version, status,
                customer_pause_enabled, customer_resume_enabled, customer_cancel_enabled, customer_skip_enabled,
                pause_cutoff_minutes, resume_lead_minutes, cancel_cutoff_minutes, skip_cutoff_minutes,
                holiday_policy_reference, unused_meal_policy_reference, refund_policy_reference, notes,
                created_by_identity_id, created_at, updated_at, activated_at
            ) VALUES (
                ?, ?, 1, 'ACTIVE',
                FALSE, FALSE, FALSE, FALSE,
                NULL, NULL, NULL, NULL,
                NULL, NULL, NULL, ?,
                ?, now(), now(), now()
            )
            """,
            policyId,
            planId,
            DEFAULT_NOTE,
            actorIdentityId
        );
        jdbcTemplate.update(
            """
            INSERT INTO subscription_schema.subscription_plan_policy_audit (
                id, plan_id, policy_id, actor_identity_id, action, policy_version, reason, created_at
            ) VALUES (?, ?, ?, ?, 'PLATFORM_DEFAULT_ACTIVATE', 1, ?, now())
            """,
            UUID.randomUUID(),
            planId,
            policyId,
            actorIdentityId,
            DEFAULT_NOTE
        );
    }
}
