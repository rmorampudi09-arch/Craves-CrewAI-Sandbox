package in.craves.auth.admin;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Repository
public class InternalAdminRoleRepository {
    private static final long ROLE_CHANGE_LOCK = 435_728_104_191L;
    private static final List<String> MANAGED_ROLES = InternalAdminRoles.codes().stream().sorted().toList();
    private static final String ROLE_PLACEHOLDERS = String.join(",", MANAGED_ROLES.stream().map(ignored -> "?").toList());

    private final JdbcTemplate jdbcTemplate;

    public InternalAdminRoleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<InternalAdminUserResponse> list(int limit) {
        List<Object> arguments = new ArrayList<>(MANAGED_ROLES);
        arguments.add(limit);
        return jdbcTemplate.query(
            """
            SELECT identity.id, identity.phone_number, identity.email, identity.display_name,
                   identity.status, identity.token_version
              FROM auth_identity identity
             WHERE EXISTS (
                   SELECT 1 FROM auth_identity_role role
                    WHERE role.identity_id = identity.id
                      AND role.role_code IN (%s)
             )
             ORDER BY identity.display_name NULLS LAST, identity.id
             LIMIT ?
            """.formatted(ROLE_PLACEHOLDERS),
            (rs, rowNum) -> response(
                rs.getObject("id", UUID.class), rs.getString("phone_number"), rs.getString("email"),
                rs.getString("display_name"), rs.getString("status"), rs.getLong("token_version")
            ),
            arguments.toArray()
        );
    }

    public InternalAdminUserResponse find(UUID identityId) {
        return jdbcTemplate.query(
            """
            SELECT id, phone_number, email, display_name, status, token_version
              FROM auth_identity
             WHERE id = ?
            """,
            (rs, rowNum) -> response(
                rs.getObject("id", UUID.class), rs.getString("phone_number"), rs.getString("email"),
                rs.getString("display_name"), rs.getString("status"), rs.getLong("token_version")
            ),
            identityId
        ).stream().findFirst().orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Identity was not found")
        );
    }

    @Transactional
    public RoleReplacementResponse replaceRoles(
        UUID actorIdentityId,
        long actorTokenVersion,
        UUID targetIdentityId,
        Set<String> requestedRoles,
        long expectedTargetTokenVersion,
        String reason,
        UUID correlationId
    ) {
        jdbcTemplate.execute("SELECT pg_advisory_xact_lock(" + ROLE_CHANGE_LOCK + ")");
        requireCurrentPlatformActor(actorIdentityId, actorTokenVersion);

        IdentityRow target = lockIdentity(targetIdentityId);
        if (target.tokenVersion() != expectedTargetTokenVersion) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "Identity roles changed after they were read; reload before retrying"
            );
        }

        Set<String> normalizedRoles = normalize(requestedRoles);
        Set<String> previousRoles = internalRoles(targetIdentityId);
        if (actorIdentityId.equals(targetIdentityId)
            && previousRoles.contains(InternalAdminRoles.PLATFORM_ADMIN)
            && !normalizedRoles.contains(InternalAdminRoles.PLATFORM_ADMIN)) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "A platform administrator cannot remove their own platform role"
            );
        }
        protectLastActivePlatformAdministrator(target, previousRoles, normalizedRoles);

        boolean changed = !previousRoles.equals(normalizedRoles);
        long newTokenVersion = target.tokenVersion();
        if (changed) {
            replaceManagedRoleRows(targetIdentityId, normalizedRoles);
            newTokenVersion = incrementTokenVersion(targetIdentityId, target.tokenVersion());
            jdbcTemplate.update(
                """
                UPDATE refresh_session
                   SET revoked_at = COALESCE(revoked_at, now()),
                       revoke_reason = CASE WHEN revoked_at IS NULL THEN 'ADMIN_ROLE_CHANGE' ELSE revoke_reason END
                 WHERE identity_id = ? AND revoked_at IS NULL
                """,
                targetIdentityId
            );
        }

        UUID auditId = UUID.randomUUID();
        insertRoleChangeAudit(
            auditId, targetIdentityId, actorIdentityId, previousRoles, normalizedRoles,
            target.tokenVersion(), newTokenVersion, changed, reason, correlationId
        );
        jdbcTemplate.update(
            """
            INSERT INTO auth_audit (
                id, identity_id, action, actor_identity_id, details, correlation_id, created_at
            ) VALUES (?, ?, 'INTERNAL_ROLES_REPLACED', ?, ?, ?, now())
            """,
            UUID.randomUUID(), targetIdentityId, actorIdentityId,
            "previous=" + csv(previousRoles) + ";new=" + csv(normalizedRoles) + ";reason=" + reason,
            correlationId.toString()
        );

        return new RoleReplacementResponse(
            auditId, targetIdentityId, previousRoles.stream().sorted().toList(),
            normalizedRoles.stream().sorted().toList(), target.tokenVersion(), newTokenVersion,
            changed, correlationId, OffsetDateTime.now()
        );
    }

    public List<RoleChangeAuditResponse> audit(UUID targetIdentityId, int limit) {
        String targetPredicate = targetIdentityId == null ? "" : " WHERE target_identity_id = ? ";
        Object[] arguments = targetIdentityId == null ? new Object[]{limit} : new Object[]{targetIdentityId, limit};
        return jdbcTemplate.query(
            """
            SELECT id, target_identity_id, actor_identity_id, previous_roles, new_roles,
                   previous_token_version, new_token_version, changed, reason, correlation_id, created_at
              FROM auth_internal_role_change_audit
            """ + targetPredicate + " ORDER BY created_at DESC, id DESC LIMIT ?",
            (rs, rowNum) -> new RoleChangeAuditResponse(
                rs.getObject("id", UUID.class), rs.getObject("target_identity_id", UUID.class),
                rs.getObject("actor_identity_id", UUID.class), splitCsv(rs.getString("previous_roles")),
                splitCsv(rs.getString("new_roles")), rs.getLong("previous_token_version"),
                rs.getLong("new_token_version"), rs.getBoolean("changed"), rs.getString("reason"),
                rs.getObject("correlation_id", UUID.class), rs.getObject("created_at", OffsetDateTime.class)
            ),
            arguments
        );
    }

    public void auditRead(
        UUID actorIdentityId,
        String action,
        UUID targetIdentityId,
        String reason,
        UUID correlationId
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO auth_audit (
                id, identity_id, action, actor_identity_id, details, correlation_id, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, now())
            """,
            UUID.randomUUID(), targetIdentityId, action, actorIdentityId, reason, correlationId.toString()
        );
    }

    private void requireCurrentPlatformActor(UUID actorIdentityId, long actorTokenVersion) {
        Boolean authorized = jdbcTemplate.query(
            """
            SELECT identity.status = 'ACTIVE'
                   AND identity.token_version = ?
                   AND EXISTS (
                       SELECT 1 FROM auth_identity_role role
                        WHERE role.identity_id = identity.id AND role.role_code = 'PLATFORM_ADMIN'
                   )
              FROM auth_identity identity
             WHERE identity.id = ?
            """,
            (rs, rowNum) -> rs.getBoolean(1),
            actorTokenVersion,
            actorIdentityId
        ).stream().findFirst().orElse(false);
        if (!Boolean.TRUE.equals(authorized)) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "Current PLATFORM_ADMIN authorization is required"
            );
        }
    }

    private IdentityRow lockIdentity(UUID identityId) {
        return jdbcTemplate.query(
            "SELECT id, status, token_version FROM auth_identity WHERE id = ? FOR UPDATE",
            (rs, rowNum) -> new IdentityRow(
                rs.getObject("id", UUID.class), rs.getString("status"), rs.getLong("token_version")
            ),
            identityId
        ).stream().findFirst().orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Identity was not found")
        );
    }

    private void protectLastActivePlatformAdministrator(
        IdentityRow target,
        Set<String> previousRoles,
        Set<String> newRoles
    ) {
        if (!"ACTIVE".equals(target.status())
            || !previousRoles.contains(InternalAdminRoles.PLATFORM_ADMIN)
            || newRoles.contains(InternalAdminRoles.PLATFORM_ADMIN)) {
            return;
        }
        Long activePlatformAdministrators = jdbcTemplate.queryForObject(
            """
            SELECT count(*)
              FROM auth_identity identity
              JOIN auth_identity_role role ON role.identity_id = identity.id
             WHERE identity.status = 'ACTIVE' AND role.role_code = 'PLATFORM_ADMIN'
            """,
            Long.class
        );
        if (activePlatformAdministrators == null || activePlatformAdministrators <= 1L) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "The last active platform administrator cannot be removed"
            );
        }
    }

    private void replaceManagedRoleRows(UUID identityId, Set<String> newRoles) {
        List<Object> deleteArguments = new ArrayList<>();
        deleteArguments.add(identityId);
        deleteArguments.addAll(MANAGED_ROLES);
        jdbcTemplate.update(
            "DELETE FROM auth_identity_role WHERE identity_id = ? AND role_code IN (" + ROLE_PLACEHOLDERS + ")",
            deleteArguments.toArray()
        );
        for (String role : newRoles.stream().sorted().toList()) {
            jdbcTemplate.update(
                """
                INSERT INTO auth_identity_role (identity_id, role_code, created_at)
                VALUES (?, ?, now()) ON CONFLICT (identity_id, role_code) DO NOTHING
                """,
                identityId,
                role
            );
        }
        if (newRoles.isEmpty()) {
            jdbcTemplate.update(
                "DELETE FROM auth_identity_role WHERE identity_id = ? AND role_code = 'ADMIN'",
                identityId
            );
        } else {
            jdbcTemplate.update(
                """
                INSERT INTO auth_identity_role (identity_id, role_code, created_at)
                VALUES (?, 'ADMIN', now()) ON CONFLICT (identity_id, role_code) DO NOTHING
                """,
                identityId
            );
        }
    }

    private long incrementTokenVersion(UUID identityId, long expectedVersion) {
        int updated = jdbcTemplate.update(
            """
            UPDATE auth_identity
               SET token_version = token_version + 1, updated_at = now()
             WHERE id = ? AND token_version = ?
            """,
            identityId,
            expectedVersion
        );
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Identity changed during role replacement");
        }
        return expectedVersion + 1L;
    }

    private void insertRoleChangeAudit(
        UUID id,
        UUID targetIdentityId,
        UUID actorIdentityId,
        Set<String> previousRoles,
        Set<String> newRoles,
        long previousTokenVersion,
        long newTokenVersion,
        boolean changed,
        String reason,
        UUID correlationId
    ) {
        jdbcTemplate.update(
            """
            INSERT INTO auth_internal_role_change_audit (
                id, target_identity_id, actor_identity_id, previous_roles, new_roles,
                previous_token_version, new_token_version, changed, reason, correlation_id, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())
            """,
            id, targetIdentityId, actorIdentityId, csv(previousRoles), csv(newRoles),
            previousTokenVersion, newTokenVersion, changed, reason, correlationId
        );
    }

    private InternalAdminUserResponse response(
        UUID identityId,
        String phoneNumber,
        String email,
        String displayName,
        String status,
        long tokenVersion
    ) {
        List<String> internalRoles = internalRoles(identityId).stream().sorted().toList();
        return new InternalAdminUserResponse(
            identityId, maskPhone(phoneNumber), maskEmail(email), displayName, status,
            tokenVersion, !internalRoles.isEmpty(), internalRoles
        );
    }

    private Set<String> internalRoles(UUID identityId) {
        List<Object> arguments = new ArrayList<>();
        arguments.add(identityId);
        arguments.addAll(MANAGED_ROLES);
        return new LinkedHashSet<>(jdbcTemplate.query(
            """
            SELECT role_code FROM auth_identity_role
             WHERE identity_id = ? AND role_code IN (%s)
             ORDER BY role_code
            """.formatted(ROLE_PLACEHOLDERS),
            (rs, rowNum) -> rs.getString("role_code"),
            arguments.toArray()
        ));
    }

    private static Set<String> normalize(Set<String> roles) {
        if (roles == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roles is required");
        }
        try {
            return roles.stream()
                .map(InternalAdminRoles::normalize)
                .sorted(Comparator.naturalOrder())
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    private static String csv(Set<String> roles) {
        return String.join(",", roles.stream().sorted().toList());
    }

    private static List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split(","));
    }

    private static String maskPhone(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.replaceAll("\\s", "");
        int visible = Math.min(4, normalized.length());
        return "*".repeat(Math.max(0, normalized.length() - visible))
            + normalized.substring(normalized.length() - visible);
    }

    private static String maskEmail(String value) {
        if (value == null || value.isBlank() || !value.contains("@")) {
            return null;
        }
        String normalized = value.trim();
        int separator = normalized.indexOf('@');
        String local = normalized.substring(0, separator);
        String domain = normalized.substring(separator + 1);
        String maskedLocal = local.length() <= 2
            ? "*".repeat(Math.max(1, local.length()))
            : local.substring(0, 2) + "*".repeat(Math.min(8, local.length() - 2));
        return maskedLocal + "@" + domain;
    }

    private record IdentityRow(UUID id, String status, long tokenVersion) {
    }

    public record InternalAdminUserResponse(
        UUID identityId,
        String maskedPhoneNumber,
        String maskedEmail,
        String displayName,
        String status,
        long tokenVersion,
        boolean backofficeEnabled,
        List<String> internalRoles
    ) {
    }

    public record RoleReplacementResponse(
        UUID auditId,
        UUID targetIdentityId,
        List<String> previousRoles,
        List<String> newRoles,
        long previousTokenVersion,
        long newTokenVersion,
        boolean changed,
        UUID correlationId,
        OffsetDateTime changedAt
    ) {
    }

    public record RoleChangeAuditResponse(
        UUID auditId,
        UUID targetIdentityId,
        UUID actorIdentityId,
        List<String> previousRoles,
        List<String> newRoles,
        long previousTokenVersion,
        long newTokenVersion,
        boolean changed,
        String reason,
        UUID correlationId,
        OffsetDateTime createdAt
    ) {
    }
}
