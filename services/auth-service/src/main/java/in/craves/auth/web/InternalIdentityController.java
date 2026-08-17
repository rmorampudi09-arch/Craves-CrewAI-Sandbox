package in.craves.auth.web;

import in.craves.auth.api.InternalIdentityEmailResponse;
import in.craves.auth.config.InternalServiceProperties;
import in.craves.auth.exception.AuthException;
import in.craves.auth.service.AuthService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.UUID;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal/v1/identities")
public class InternalIdentityController {
    private static final String INTERNAL_SECRET_HEADER = "X-Craves-Internal-Secret";

    private final AuthService authService;
    private final InternalServiceProperties properties;

    public InternalIdentityController(AuthService authService, InternalServiceProperties properties) {
        this.authService = authService;
        this.properties = properties;
    }

    @GetMapping("/{identityId}/email")
    public InternalIdentityEmailResponse email(
        @PathVariable UUID identityId,
        @RequestHeader(name = INTERNAL_SECRET_HEADER, required = false) String providedSecret
    ) {
        requireInternalSecret(providedSecret);
        return authService.internalIdentityEmail(identityId);
    }

    private void requireInternalSecret(String providedSecret) {
        String expectedSecret = properties.getServiceSecret();
        if (!StringUtils.hasText(expectedSecret)) {
            throw AuthException.forbidden(
                "INTERNAL_SECRET_NOT_CONFIGURED",
                "Internal service secret is not configured"
            );
        }
        if (!StringUtils.hasText(providedSecret)) {
            throw AuthException.unauthorized(
                "INTERNAL_SECRET_REQUIRED",
                "Internal service secret is required"
            );
        }

        byte[] expected = expectedSecret.getBytes(StandardCharsets.UTF_8);
        byte[] actual = providedSecret.getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(expected, actual)) {
            throw AuthException.unauthorized(
                "INTERNAL_SECRET_INVALID",
                "Internal service secret is invalid"
            );
        }
    }
}
