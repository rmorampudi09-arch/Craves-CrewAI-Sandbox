package in.craves.order.launchpolicy;

import in.craves.order.launchpolicy.LaunchPolicyModels.LaunchPolicyResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class LaunchPolicyRepository {
    private final JdbcTemplate jdbcTemplate;

    public LaunchPolicyRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<LaunchPolicyResponse> list() {
        return jdbcTemplate.query(
            "SELECT * FROM order_schema.launch_policy ORDER BY created_at DESC",
            this::map
        );
    }

    public Optional<LaunchPolicyResponse> findById(UUID id) {
        return jdbcTemplate.query(
            "SELECT * FROM order_schema.launch_policy WHERE id = ?",
            this::map,
            id
        ).stream().findFirst();
    }

    public Optional<LaunchPolicyResponse> findActive() {
        return jdbcTemplate.query(
            "SELECT * FROM order_schema.launch_policy WHERE active = true ORDER BY activated_at DESC LIMIT 1",
            this::map
        ).stream().findFirst();
    }

    public LaunchPolicyResponse create(LaunchPolicyModels.CreateLaunchPolicyRequest request, UUID actorIdentityId) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO order_schema.launch_policy " +
                "(id, policy_name, minimum_order_amount, maximum_serviceability_radius_meters, " +
                "cancellation_cutoff_minutes, delivery_sla_minutes, currency, active, created_by_identity_id, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, false, ?, now())",
            id,
            request.policyName().trim(),
            request.minimumOrderAmount(),
            request.maximumServiceabilityRadiusMeters(),
            request.cancellationCutoffMinutes(),
            request.deliverySlaMinutes(),
            request.currency().trim().toUpperCase(),
            actorIdentityId
        );
        return findById(id).orElseThrow();
    }

    @Transactional
    public LaunchPolicyResponse activate(UUID policyId, UUID actorIdentityId, String reason) {
        LaunchPolicyResponse selected = findById(policyId).orElseThrow();
        UUID previousPolicyId = findActive().map(LaunchPolicyResponse::id).orElse(null);
        jdbcTemplate.update(
            "UPDATE order_schema.launch_policy SET active = false, activated_at = null WHERE active = true"
        );
        int updated = jdbcTemplate.update(
            "UPDATE order_schema.launch_policy SET active = true, activated_at = now() WHERE id = ?",
            policyId
        );
        if (updated != 1) {
            throw new IllegalStateException("Launch policy activation did not update exactly one row");
        }
        jdbcTemplate.update(
            "INSERT INTO order_schema.launch_policy_audit " +
                "(id, policy_id, actor_identity_id, action, previous_policy_id, reason, created_at) " +
                "VALUES (?, ?, ?, 'ACTIVATE', ?, ?, now())",
            UUID.randomUUID(),
            selected.id(),
            actorIdentityId,
            previousPolicyId,
            reason.trim()
        );
        return findById(policyId).orElseThrow();
    }

    private LaunchPolicyResponse map(ResultSet rs, int rowNum) throws SQLException {
        return new LaunchPolicyResponse(
            rs.getObject("id", UUID.class),
            rs.getString("policy_name"),
            rs.getBigDecimal("minimum_order_amount"),
            rs.getInt("maximum_serviceability_radius_meters"),
            rs.getInt("cancellation_cutoff_minutes"),
            rs.getInt("delivery_sla_minutes"),
            rs.getString("currency"),
            rs.getBoolean("active"),
            rs.getObject("created_by_identity_id", UUID.class),
            rs.getObject("created_at", Instant.class),
            rs.getObject("activated_at", Instant.class)
        );
    }
}
