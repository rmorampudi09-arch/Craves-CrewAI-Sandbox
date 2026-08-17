package in.craves.auth.admin;

import in.craves.auth.admin.AdminAccountInterventionRepository.InterventionResponse;
import in.craves.auth.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/accounts")
public class AdminAccountInterventionController {
    private final AdminAccountInterventionProperties properties;
    private final AdminAccountInterventionRepository repository;

    public AdminAccountInterventionController(
        AdminAccountInterventionProperties properties,
        AdminAccountInterventionRepository repository
    ) {
        this.properties = properties;
        this.repository = repository;
    }

    @PostMapping("/{identityId}/suspend")
    public ResponseEntity<InterventionResponse> suspend(
        Authentication authentication,
        @PathVariable UUID identityId,
        @Valid @RequestBody InterventionRequest request,
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationHeader
    ) {
        return intervene(authentication, identityId, "SUSPEND", request, correlationHeader);
    }

    @PostMapping("/{identityId}/reactivate")
    public ResponseEntity<InterventionResponse> reactivate(
        Authentication authentication,
        @PathVariable UUID identityId,
        @Valid @RequestBody InterventionRequest request,
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationHeader
    ) {
        return intervene(authentication, identityId, "REACTIVATE", request, correlationHeader);
    }

    @GetMapping("/{identityId}/intervention-status")
    public ResponseEntity<InterventionResponse> status(
        Authentication authentication,
        @PathVariable UUID identityId,
        @RequestHeader("X-Admin-Reason") String reason,
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationHeader
    ) {
        requireEnabled();
        CurrentUser actor = requireAccountReader(authentication);
        UUID correlationId = correlationId(correlationHeader);
        try {
            InterventionResponse response = repository.find(identityId);
            repository.auditStatusRead(
                actor.identityId(), identityId, normalizeReason(reason), correlationId
            );
            return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header("X-Correlation-ID", correlationId.toString())
                .body(response);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        }
    }

    private ResponseEntity<InterventionResponse> intervene(
        Authentication authentication,
        UUID identityId,
        String action,
        InterventionRequest request,
        String correlationHeader
    ) {
        requireEnabled();
        CurrentUser actor = requirePlatformAdmin(authentication);
        UUID correlationId = correlationId(correlationHeader);
        try {
            return noStore(repository.request(
                actor.identityId(), identityId, action, normalizeReason(request.reason()), correlationId
            ));
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
    }

    private void requireEnabled() {
        if (!properties.isApiEnabled()) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Administrative account intervention is not enabled"
            );
        }
    }

    private static CurrentUser requirePlatformAdmin(Authentication authentication) {
        CurrentUser user = principal(authentication);
        if (!user.hasRole(InternalAdminRoles.PLATFORM_ADMIN)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "PLATFORM_ADMIN role is required");
        }
        return user;
    }

    private static CurrentUser requireAccountReader(Authentication authentication) {
        CurrentUser user = principal(authentication);
        if (!user.hasAnyRole(
            InternalAdminRoles.PLATFORM_ADMIN,
            InternalAdminRoles.SUPPORT_ADMIN,
            InternalAdminRoles.AUDIT_ADMIN
        )) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account status access is required");
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

    public record InterventionRequest(
        @NotBlank @Size(min = 10, max = 500) String reason
    ) {}
}
