package in.craves.auth.admin;

import in.craves.auth.admin.InternalAdminRoleRepository.InternalAdminUserResponse;
import in.craves.auth.admin.InternalAdminRoleRepository.RoleChangeAuditResponse;
import in.craves.auth.admin.InternalAdminRoleRepository.RoleReplacementResponse;
import in.craves.auth.admin.StaffRoleGrantService.BatchStaffRoleGrantResponse;
import in.craves.auth.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/internal-access")
public class InternalAdminRoleController {
    private final InternalAdminRbacProperties properties;
    private final InternalAdminRoleRepository repository;
    private final StaffRoleGrantService staffRoleGrantService;

    public InternalAdminRoleController(
        InternalAdminRbacProperties properties,
        InternalAdminRoleRepository repository,
        StaffRoleGrantService staffRoleGrantService
    ) {
        this.properties = properties;
        this.repository = repository;
        this.staffRoleGrantService = staffRoleGrantService;
    }

    @GetMapping("/roles")
    public ResponseEntity<List<InternalAdminRoles.RoleDefinition>> catalog(Authentication authentication) {
        requireEnabled();
        requireAnyInternalAdmin(authentication);
        return noStore(InternalAdminRoles.catalog());
    }

    @GetMapping("/users")
    public ResponseEntity<List<InternalAdminUserResponse>> users(
        Authentication authentication,
        @RequestParam(defaultValue = "100") int limit,
        @RequestHeader("X-Admin-Reason") String reason,
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationHeader
    ) {
        requireEnabled();
        CurrentUser actor = requireInternalAccessReader(authentication);
        UUID correlationId = correlationId(correlationHeader);
        repository.auditRead(actor.identityId(), "INTERNAL_ADMIN_USERS_READ", null, normalizeReason(reason), correlationId);
        return auditedNoStore(correlationId, repository.list(bounded(limit)));
    }

    @GetMapping("/users/{identityId}")
    public ResponseEntity<InternalAdminUserResponse> user(
        Authentication authentication,
        @PathVariable UUID identityId,
        @RequestHeader("X-Admin-Reason") String reason,
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationHeader
    ) {
        requireEnabled();
        CurrentUser actor = requireInternalAccessReader(authentication);
        UUID correlationId = correlationId(correlationHeader);
        InternalAdminUserResponse response = repository.find(identityId);
        repository.auditRead(
            actor.identityId(), "INTERNAL_ADMIN_USER_READ", identityId, normalizeReason(reason), correlationId
        );
        return auditedNoStore(correlationId, response);
    }

    @PutMapping("/users/{identityId}/roles")
    public ResponseEntity<RoleReplacementResponse> replaceRoles(
        Authentication authentication,
        @PathVariable UUID identityId,
        @Valid @RequestBody RoleReplacementRequest request,
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationHeader
    ) {
        requireEnabled();
        CurrentUser actor = requirePlatformAdmin(authentication);
        UUID correlationId = correlationId(correlationHeader);
        RoleReplacementResponse response = repository.replaceRoles(
            actor.identityId(), actor.tokenVersion(), identityId, request.roles(),
            request.expectedTokenVersion(), normalizeReason(request.reason()), correlationId
        );
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header("X-Correlation-ID", correlationId.toString())
            .body(response);
    }

    @PutMapping("/staff-role-grants")
    public ResponseEntity<BatchStaffRoleGrantResponse> grantStaffRoles(
        Authentication authentication,
        @Valid @RequestBody StaffRoleGrantRequest request,
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationHeader
    ) {
        requireEnabled();
        CurrentUser actor = requirePlatformAdmin(authentication);
        UUID correlationId = correlationId(correlationHeader);
        BatchStaffRoleGrantResponse response = staffRoleGrantService.grant(
            actor.identityId(),
            actor.tokenVersion(),
            request.phoneNumbers(),
            request.grantChef(),
            request.internalRoles(),
            normalizeReason(request.reason()),
            correlationId
        );
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header("X-Correlation-ID", correlationId.toString())
            .body(response);
    }

    @GetMapping("/role-changes")
    public ResponseEntity<List<RoleChangeAuditResponse>> roleChanges(
        Authentication authentication,
        @RequestParam(required = false) UUID identityId,
        @RequestParam(defaultValue = "100") int limit,
        @RequestHeader("X-Admin-Reason") String reason,
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationHeader
    ) {
        requireEnabled();
        CurrentUser actor = requireInternalAccessReader(authentication);
        UUID correlationId = correlationId(correlationHeader);
        repository.auditRead(
            actor.identityId(), "INTERNAL_ROLE_AUDIT_READ", identityId, normalizeReason(reason), correlationId
        );
        return auditedNoStore(correlationId, repository.audit(identityId, bounded(limit)));
    }

    private void requireEnabled() {
        if (!properties.isApiEnabled()) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Internal administrator role management is not enabled"
            );
        }
    }

    private int bounded(int limit) {
        return Math.min(Math.max(limit, 1), properties.getMaximumListSize());
    }

    private static CurrentUser requirePlatformAdmin(Authentication authentication) {
        CurrentUser user = principal(authentication);
        if (!user.hasRole(InternalAdminRoles.PLATFORM_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "PLATFORM_ADMIN role is required");
        }
        return user;
    }

    private static CurrentUser requireInternalAccessReader(Authentication authentication) {
        CurrentUser user = principal(authentication);
        if (!user.hasAnyRole(InternalAdminRoles.PLATFORM_ADMIN, InternalAdminRoles.AUDIT_ADMIN)) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "PLATFORM_ADMIN or AUDIT_ADMIN role is required"
            );
        }
        return user;
    }

    private static CurrentUser requireAnyInternalAdmin(Authentication authentication) {
        CurrentUser user = principal(authentication);
        boolean allowed = user.roles() != null && user.roles().stream()
            .map(role -> role == null ? "" : role.trim().toUpperCase(Locale.ROOT))
            .anyMatch(InternalAdminRoles.codes()::contains);
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Internal administrator role is required");
        }
        return user;
    }

    private static CurrentUser principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CurrentUser currentUser)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Craves access token is required");
        }
        return currentUser;
    }

    private static String normalizeReason(String value) {
        String normalized = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() < 10 || normalized.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "reason must contain 10 to 500 characters");
        }
        return normalized;
    }

    private static UUID correlationId(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Correlation-ID must be a UUID");
        }
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    private static <T> ResponseEntity<T> auditedNoStore(UUID correlationId, T body) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header("X-Correlation-ID", correlationId.toString())
            .body(body);
    }

    public record RoleReplacementRequest(
        @NotNull @Size(max = 9) Set<@NotBlank String> roles,
        @Min(1) long expectedTokenVersion,
        @NotBlank @Size(min = 10, max = 500) String reason
    ) {
    }

    public record StaffRoleGrantRequest(
        @NotNull @Size(min = 1, max = 20) Set<@NotBlank String> phoneNumbers,
        boolean grantChef,
        @NotNull @Size(max = 9) Set<@NotBlank String> internalRoles,
        @NotBlank @Size(min = 10, max = 500) String reason
    ) {
    }
}
