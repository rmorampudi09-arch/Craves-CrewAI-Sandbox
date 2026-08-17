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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class StaffRoleGrantService {
    private static final long ROLE_CHANGE_LOCK = 435_728_104_191L;
    private static final String CHEF_ROLE = "CHEF";
    private static final String LEGACY_ADMIN_ROLE = "ADMIN";

    private final JdbcTemplate jdbcTemplate;

    public StaffRoleGrantService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public BatchStaffRoleGrantResponse grant(
        UUID actorIdentityId,
        long actorTokenVersion,
        Set<String> requestedPhoneNumbers,
        boolean grantChef,
        Set<String> requestedInternalRoles,
        String reason,
        UUID correlationId
    ) {
        jdbcTemplate.execute("SELECT pg_advisory_xact_lock(" + ROLE_CHANGE_LOCK + ")");
        requireCurrentPlatformActor(actorIdentityId, actorTokenVersion);

        Set<String> phoneNumbers = normalizePhones(requestedPhoneNumbers);
        Set<String> requestedRoles = normalizeRoles(requestedInternalRoles);
        if (!grantChef && requestedRoles.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one role grant is required");
        }

        List<TargetIdentity> targets = lockTargets(phoneNumbers);
        if (targets.size() != phoneNumbers.size()) {
            Set<String> found = targets.stream()
                .map(TargetIdentity::phoneNumber)
                .collect(java.util.stream.Collectors.toSet());
            List<String> missing = phoneNumbers.stream()
                .filter(phone -> !found.contains(phone))
                .sorted()
                .toList();
            throw new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "One or more target identities were not found: " + String.join(",", missing)
            );
        }

        List<String> inactive = targets.stream()
            .filter(target -> !"ACTIVE".equals(target.status()))
            .map(TargetIdentity::phoneNumber)
            .sorted()
            .toList();
        if (!inactive.isEmpty()) {
            throw new ResponseStatusException(
                HttpStatus.CONFLICT,
                "All target identities must be ACTIVE: " + String.join(",", inactive)
            );
        }

        List<StaffRoleGrantResult> results = new ArrayList<>();
        for (TargetIdentity target : targets.stream().sorted(Comparator.comparing(TargetIdentity::phoneNumber)).toList()) {
            Set<String> previousInternalRoles = internalRoles(target.identityId());
            Set<String> newInternalRoles = new LinkedHashSet<>(previousInternalRoles);
            newInternalRoles.addAll(requestedRoles);
            newInternalRoles = new LinkedHashSet<>(newInternalRoles.stream().sorted().toList());

            boolean hadChef = hasRole(target.identityId(), CHEF_ROLE);
            boolean hadLegacyAdmin = hasRole(target.identityId(), LEGACY_ADMIN_ROLE);
            boolean internalChanged = !previousInternalRoles.equals(newInternalRoles);
            boolean chefChanged = grantChef && !hadChef;
            boolean legacyAdminChanged = !newInternalRoles.isEmpty() && !hadLegacyAdmin;
            boolean changed = internalChanged || chefChanged || legacyAdminChanged;

            if (internalChanged) {
                for (String role : requestedRoles.stream().sorted().toList()) {
                    jdbcTemplate.update(
                        """
                        INSERT INTO auth_identity_role (identity_id, role_code, created_at)
                        VALUES (?, ?, now()) ON CONFLICT (identity_id, role_code) DO NOTHING
                        """,
                        target.identityId(),
                        role
                    );
                }
            }

            if (legacyAdminChanged) {
                jdbcTemplate.update(
                    """
                    INSERT INTO auth_identity_role (identity_id, role_code, created_at)
                    VALUES (?, 'ADMIN', now()) ON CONFLICT (identity_id, role_code) DO NOTHING
                    """,
                    target.identityId()
                );
            }

            if (chefChanged) {
                jdbcTemplate.update(
                    """
                    INSERT INTO auth_identity_role (identity_id, role_code, created_at)
                    VALUES (?, 'CHEF', now()) ON CONFLICT (identity_id, role_code) DO NOTHING
                    """,
                    target.identityId()
                );
            }

            long newTokenVersion = target.tokenVersion();
            if (changed) {
                newTokenVersion = incrementTokenVersion(target.identityId(), target.tokenVersion());
                revokeRefreshSessions(target.identityId());
            }

            UUID internalAuditId = UUID.randomUUID();
            insertInternalRoleAudit(
                internalAuditId,
                target.identityId(),
                actorIdentityId,
                previousInternalRoles,
                newInternalRoles,
                target.tokenVersion(),
                newTokenVersion,
                internalChanged,
                reason,
                correlationId
            );
            insertAuthAudit(
                target.identityId(),
                actorIdentityId,
                previousInternalRoles,
                newInternalRoles,
                chefChanged,
                changed,
                reason,
                correlationId
            );

            results.add(new StaffRoleGrantResult(
                internalAuditId,
                target.identityId(),
                maskPhone(target.phoneNumber()),
                target.tokenVersion(),
                newTokenVersion,
                hadChef || grantChef,
                newInternalRoles.stream().sorted().toList(),
                changed
            ));
        }

        return new BatchStaffRoleGrantResponse(
            correlationId,
            results,
            OffsetDateTime.now()
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

    private List<TargetIdentity> lockTargets(Set<String> phoneNumbers) {
        String placeholders = String.join(",", phoneNumbers.stream().map(ignored -> "?").toList());
        return jdbcTemplate.query(
            """
            SELECT id, phone_number, status, token_version
              FROM auth_identity
             WHERE phone_number IN (%s)
             ORDER BY phone_number
             FOR UPDATE
            """.formatted(placeholders),
            (rs, rowNum) -> new TargetIdentity(
                rs.getObject("id", UUID.class),
                rs.getString("phone_number"),
                rs.getString("status"),
                rs.getLong("token_version")
            ),
            phoneNumbers.toArray()
        );
    }

    private Set<String> internalRoles(UUID identityId) {
        List<String> managedRoles = InternalAdminRoles.codes().stream().sorted().toList();
        String placeholders = String.join(",", managedRoles.stream().map(ignored -> "?").toList());
        List<Object> arguments = new ArrayList<>();
        arguments.add(identityId);
        arguments.addAll(managedRoles);
        return new LinkedHashSet<>(jdbcTemplate.query(
            """
            SELECT role_code
              FROM auth_identity_role
             WHERE identity_id = ?
               AND role_code IN (%s)
             ORDER BY role_code
            """.formatted(placeholders),
            (rs, rowNum) -> rs.getString("role_code"),
            arguments.toArray()
        ));
    }

    private boolean hasRole(UUID identityId, String roleCode) {
        Boolean present = jdbcTemplate.queryForObject(
            """
            SELECT EXISTS(
                SELECT 1 FROM auth_identity_role
                 WHERE identity_id = ? AND role_code = ?
            )
            """,
            Boolean.class,
            identityId,
            roleCode
        );
        return Boolean.TRUE.equals(present);
    }

    private long incrementTokenVersion(UUID identityId, long expectedVersion) {
        int updated = jdbcTemplate.update(
            """
            UPDATE auth_identity
               SET token_version = token_version + 1,
                   updated_at = now()
             WHERE id = ? AND token_version = ?
            """,
            identityId,
            expectedVersion
        );
        if (updated != 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Identity changed during role grant");
        }
        return expectedVersion + 1L;
    }

    private void revokeRefreshSessions(UUID identityId) {
        jdbcTemplate.update(
            """
            UPDATE refresh_session
               SET revoked_at = COALESCE(revoked_at, now()),
                   revoke_reason = CASE WHEN revoked_at IS NULL THEN 'STAFF_ROLE_GRANT' ELSE revoke_reason END
             WHERE identity_id = ? AND revoked_at IS NULL
            """,
            identityId
        );
    }

    private void insertInternalRoleAudit(
        UUID auditId,
        UUID targetIdentityId,
        UUID actorIdentityId,
        Set<String> previousRoles,
        Set<String> newRoles,
        long previousTokenVersion,
        long newTokenVersion,
        boolean internalChanged,
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
            auditId,
            targetIdentityId,
            actorIdentityId,
            csv(previousRoles),
            csv(newRoles),
            previousTokenVersion,
            newTokenVersion,
            internalChanged,
            reason,
            correlationId
        );
    }

    private void insertAuthAudit(
        UUID targetIdentityId,
        UUID actorIdentityId,
        Set<String> previousInternalRoles,
        Set<String> newInternalRoles,
        boolean chefGranted,
        boolean changed,
        String reason,
        UUID correlationId
    ) {
        String details = "previousInternal=" + csv(previousInternalRoles)
            + ";newInternal=" + csv(newInternalRoles)
            + ";chefGranted=" + chefGranted
            + ";changed=" + changed
            + ";reason=" + reason;
        jdbcTemplate.update(
            """
            INSERT INTO auth_audit (
                id, identity_id, action, actor_identity_id, details, correlation_id, created_at
            ) VALUES (?, ?, 'STAFF_ROLES_GRANTED', ?, ?, ?, now())
            """,
            UUID.randomUUID(),
            targetIdentityId,
            actorIdentityId,
            details,
            correlationId.toString()
        );
    }

    private static Set<String> normalizePhones(Set<String> phoneNumbers) {
        if (phoneNumbers == null || phoneNumbers.isEmpty() || phoneNumbers.size() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "phoneNumbers must contain 1 to 20 values");
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String phoneNumber : phoneNumbers) {
            String value = phoneNumber == null ? "" : phoneNumber.replaceAll("\\s", "").trim();
            if (!value.matches("\\+[1-9][0-9]{7,14}")) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "phoneNumbers must use E.164 format");
            }
            normalized.add(value);
        }
        return normalized.stream()
            .sorted()
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private static Set<String> normalizeRoles(Set<String> roles) {
        if (roles == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "internalRoles is required");
        }
        if (roles.size() > InternalAdminRoles.codes().size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Too many internal roles were requested");
        }
        try {
            return roles.stream()
                .map(InternalAdminRoles::normalize)
                .sorted()
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage());
        }
    }

    private static String csv(Set<String> roles) {
        return String.join(",", roles.stream().sorted().toList());
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

    private record TargetIdentity(
        UUID identityId,
        String phoneNumber,
        String status,
        long tokenVersion
    ) {
    }

    public record StaffRoleGrantResult(
        UUID auditId,
        UUID identityId,
        String maskedPhoneNumber,
        long previousTokenVersion,
        long newTokenVersion,
        boolean chefRolePresent,
        List<String> internalRoles,
        boolean changed
    ) {
    }

    public record BatchStaffRoleGrantResponse(
        UUID correlationId,
        List<StaffRoleGrantResult> users,
        OffsetDateTime changedAt
    ) {
    }
}
