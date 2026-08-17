package in.craves.notification.api;

import in.craves.notification.security.CravesPrincipal;
import in.craves.notification.service.NotificationService;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/notifications")
public class AppNotificationController {
    private final NotificationService notificationService;

    public AppNotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/in-app")
    public List<AppNoticeResponse> list(Authentication authentication,
                                        @RequestParam(defaultValue = "50") int limit) {
        return notificationService.appNotices(currentIdentity(authentication), limit);
    }

    @PatchMapping("/in-app/{noticeId}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void markRead(Authentication authentication, @PathVariable UUID noticeId) {
        notificationService.markRead(currentIdentity(authentication), noticeId);
    }

    private static UUID currentIdentity(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CravesPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Craves access token is required");
        }
        return principal.identityId();
    }
}
