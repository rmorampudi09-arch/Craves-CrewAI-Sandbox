package in.craves.notification.api;

import in.craves.notification.delivery.NotificationDeliveryModels.DeviceResponse;
import in.craves.notification.delivery.NotificationDeliveryModels.PreferenceRequest;
import in.craves.notification.delivery.NotificationDeliveryModels.PreferenceResponse;
import in.craves.notification.delivery.NotificationDeliveryModels.RegisterDeviceRequest;
import in.craves.notification.delivery.NotificationDeliveryRepository;
import in.craves.notification.security.CravesPrincipal;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/notifications")
public class NotificationDeliveryController {
    private static final Set<String> PLATFORMS = Set.of("ANDROID", "IOS");
    private static final Set<String> PREFERENCE_CHANNELS = Set.of("IN_APP", "PUSH", "EMAIL");

    private final NotificationDeliveryRepository repository;

    public NotificationDeliveryController(NotificationDeliveryRepository repository) {
        this.repository = repository;
    }

    @PostMapping("/devices")
    public DeviceResponse registerDevice(
        Authentication authentication,
        @Valid @RequestBody RegisterDeviceRequest request
    ) {
        String platform = request.platform().trim().toUpperCase(Locale.ROOT);
        if (!PLATFORMS.contains(platform)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "platform must be ANDROID or IOS");
        }
        return repository.register(
            principal(authentication).identityId(),
            new RegisterDeviceRequest(
                platform,
                request.deviceToken(),
                request.appInstanceId(),
                request.appVersion()
            )
        );
    }

    @GetMapping("/devices")
    public List<DeviceResponse> listDevices(Authentication authentication) {
        return repository.listDevices(principal(authentication).identityId());
    }

    @DeleteMapping("/devices/{deviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateDevice(Authentication authentication, @PathVariable UUID deviceId) {
        repository.deactivateDevice(principal(authentication).identityId(), deviceId);
    }

    @GetMapping("/preferences")
    public List<PreferenceResponse> listPreferences(Authentication authentication) {
        return repository.listPreferences(principal(authentication).identityId());
    }

    @PutMapping("/preferences/{channel}")
    public PreferenceResponse updatePreference(
        Authentication authentication,
        @PathVariable String channel,
        @Valid @RequestBody PreferenceRequest request
    ) {
        String normalized = channel.trim().toUpperCase(Locale.ROOT);
        if (!PREFERENCE_CHANNELS.contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported notification preference channel");
        }
        return repository.setPreference(principal(authentication).identityId(), normalized, request.enabled());
    }

    private static CravesPrincipal principal(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CravesPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Craves access token is required");
        }
        return principal;
    }
}
