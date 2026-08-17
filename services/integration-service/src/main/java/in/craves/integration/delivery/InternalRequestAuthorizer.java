package in.craves.integration.delivery;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Component
public class InternalRequestAuthorizer {
    private final String expectedKey;

    public InternalRequestAuthorizer(@Value("${CRAVES_INTERNAL_SERVICE_KEY:}") String expectedKey) {
        this.expectedKey = expectedKey;
    }

    public void requireValid(String suppliedKey) {
        if (!StringUtils.hasText(expectedKey)) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Internal service authentication is not configured");
        }
        if (!StringUtils.hasText(suppliedKey)
            || !MessageDigest.isEqual(expectedKey.getBytes(StandardCharsets.UTF_8),
                                      suppliedKey.getBytes(StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid internal service credential");
        }
    }
}
