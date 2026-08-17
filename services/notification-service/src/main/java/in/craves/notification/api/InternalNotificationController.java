package in.craves.notification.api;

import in.craves.notification.service.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/v1/notifications")
public class InternalNotificationController {
    private final NotificationService notificationService;
    private final String configuredKey;

    public InternalNotificationController(NotificationService notificationService,
                                          @Value("${craves.internal.service-key:}") String configuredKey) {
        this.notificationService = notificationService;
        this.configuredKey = configuredKey;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public NotificationRequestResponse create(@RequestHeader(value = "X-Craves-" + "Internal-Key", required = false) String providedKey,
                                              @RequestBody CreateNotificationRequest request) {
        if (configuredKey == null || configuredKey.isBlank() || !configuredKey.equals(providedKey)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Internal key is invalid");
        }
        return notificationService.create(request);
    }
}
