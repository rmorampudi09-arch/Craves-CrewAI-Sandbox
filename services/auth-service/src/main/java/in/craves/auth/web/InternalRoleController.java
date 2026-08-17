package in.craves.auth.web;

import in.craves.auth.api.IdentityResponse;
import in.craves.auth.api.InternalChefRoleGrantRequest;
import in.craves.auth.api.InternalRoleGrantResponse;
import in.craves.auth.config.InternalServiceProperties;
import in.craves.auth.exception.AuthException;
import in.craves.auth.service.AuthService;
import jakarta.validation.Valid;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/roles")
public class InternalRoleController {
    private static final String INTERNAL_SECRET_HEADER = "X-Craves-Internal-Secret";

    private final AuthService authService;
    private final InternalServiceProperties properties;

    public InternalRoleController(AuthService authService, InternalServiceProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    @PostMapping("/chef/grant")
    public InternalRoleGrantResponse grantChefRole(
        @RequestHeader(name = INTERNAL_SECRET_HEADER, required = false) String providedSecret,
        @Valid @RequestBody InternalChefRoleGrantRequest request
    ) {
        requireInternalSecret(providedSecret);
        IdentityResponse identity = authService.grantChefRole(request.identityId(), request.sourceApplicationId());
        return new InternalRoleGrantResponse(identity.id(), identity.roles());
    }

    private void requireInternalSecret(String providedSecret) {
        String expectedSecret = properties.getServiceSecret();
        if (!StringUtils.hasText(expectedSecret)) {
            throw AuthException.forbidden("INTERNAL_SECRET_NOT_CONFIGURED", "Internal service secret is not configured");
        }
        if (!StringUtils.hasText(providedSecret)) {
            throw AuthException.unauthorized("INTERNAL_SECRET_REQUIRED", "Internal service secret is required");
        }

        byte[] expected = expectedSecret.getBytes(StandardCharsets.UTF_8);
        byte[] actual = providedSecret.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw AuthException.unauthorized("INTERNAL_SECRET_INVALID", "Internal service secret is invalid");
        }
    }
}
