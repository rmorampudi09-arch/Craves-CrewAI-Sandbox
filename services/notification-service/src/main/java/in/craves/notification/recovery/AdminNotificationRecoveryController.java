package in.craves.notification.recovery;

import in.craves.notification.recovery.NotificationRecoveryRepository.BacklogItem;
import in.craves.notification.recovery.NotificationRecoveryRepository.RecoveryResponse;
import in.craves.notification.security.CravesPrincipal;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/notifications/operations")
public class AdminNotificationRecoveryController {
    private final NotificationRecoveryProperties properties;
    private final NotificationRecoveryRepository repository;

    public AdminNotificationRecoveryController(
        NotificationRecoveryProperties properties,
        NotificationRecoveryRepository repository
    ) {
        this.properties = properties;
        this.repository = repository;
    }

    @GetMapping("/backlog")
    public ResponseEntity<List<BacklogItem>> backlog(
        Authentication authentication,
        @RequestParam(defaultValue = "DEAD_LETTER") String status,
        @RequestParam(defaultValue = "50") int limit
    ) {
        requireEnabled();
        requireNotificationReader(authentication);
        int boundedLimit = Math.min(Math.max(limit, 1), properties.getMaximumListSize());
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(repository.backlog(status, boundedLimit));
    }

    @PostMapping("/{requestId}/retry")
    public ResponseEntity<RecoveryResponse> retry(
        Authentication authentication,
        @PathVariable UUID requestId,
        @Valid @RequestBody RecoveryRequest request,
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationHeader
    ) {
        requireEnabled();
        CravesPrincipal principal = requireNotificationOperator(authentication);
        UUID correlationId = correlationId(correlationHeader);
        RecoveryResponse response = repository.requeue(
            requestId, principal.identityId(), normalizeReason(request.reason()), correlationId
        );
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header("X-Correlation-ID", correlationId.toString())
            .body(response);
    }

    private void requireEnabled() {
        if (!properties.isApiEnabled()) {
            throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Notification recovery operations are not enabled"
            );
        }
    }

    private static CravesPrincipal requireNotificationOperator(Authentication authentication) {
        CravesPrincipal principal = principal(authentication);
        if (!principal.hasAnyRole("PLATFORM_ADMIN", "NOTIFICATION_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Notification recovery role is required");
        }
        return principal;
    }

    private static CravesPrincipal requireNotificationReader(Authentication authentication) {
        CravesPrincipal principal = principal(authentication);
        if (!principal.hasAnyRole("PLATFORM_ADMIN", "NOTIFICATION_ADMIN", "AUDIT_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Notification recovery read role is required");
        }
        return principal;
    }

    private static CravesPrincipal principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CravesPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Craves access token is required");
        }
        return principal;
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

    public record RecoveryRequest(
        @NotBlank @Size(min = 10, max = 500) String reason
    ) {}
}
