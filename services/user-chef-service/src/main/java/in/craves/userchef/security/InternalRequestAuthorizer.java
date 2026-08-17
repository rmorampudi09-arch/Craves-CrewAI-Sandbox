package in.craves.userchef.security;

import in.craves.userchef.config.AuthInternalClientProperties;
import in.craves.userchef.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class InternalRequestAuthorizer {
    private final AuthInternalClientProperties properties;

    public InternalRequestAuthorizer(AuthInternalClientProperties properties) {
        this.properties = properties;
    }

    public void requireValid(String suppliedSecret) {
        String expectedSecret = properties.getServiceSecret();
        if (!StringUtils.hasText(expectedSecret)) {
            throw new ApiException(
                503,
                "INTERNAL_AUTH_NOT_CONFIGURED",
                "Internal service authentication is not configured"
            );
        }
        if (!StringUtils.hasText(suppliedSecret)
            || !MessageDigest.isEqual(
                expectedSecret.getBytes(StandardCharsets.UTF_8),
                suppliedSecret.getBytes(StandardCharsets.UTF_8)
            )) {
            throw ApiException.unauthorized(
                "INVALID_INTERNAL_SERVICE_CREDENTIAL",
                "Invalid internal service credential"
            );
        }
    }
}
