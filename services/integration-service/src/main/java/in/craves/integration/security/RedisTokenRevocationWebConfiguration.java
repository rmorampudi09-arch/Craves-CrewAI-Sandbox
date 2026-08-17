package in.craves.integration.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class RedisTokenRevocationWebConfiguration implements WebMvcConfigurer {
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final boolean failClosed;
    private final String keyPrefix;

    public RedisTokenRevocationWebConfiguration(
        StringRedisTemplate redisTemplate, ObjectMapper objectMapper,
        @Value("${CRAVES_TOKEN_REVOCATION_ENABLED:false}") boolean enabled,
        @Value("${CRAVES_TOKEN_REVOCATION_FAIL_CLOSED:true}") boolean failClosed,
        @Value("${CRAVES_TOKEN_REVOCATION_KEY_PREFIX:craves:auth:revocation}") String keyPrefix
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.failClosed = failClosed;
        this.keyPrefix = keyPrefix;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        if (enabled) registry.addInterceptor(new RevocationInterceptor()).addPathPatterns("/api/**");
    }

    private final class RevocationInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            String authorization = request.getHeader("Authorization");
            if (authorization == null || !authorization.startsWith("Bearer ")) return true;
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) return true;
            TokenIdentity token = tokenIdentity(authorization);
            String projection;
            try { projection = redisTemplate.opsForValue().get(keyPrefix + ":" + token.identityId()); }
            catch (RuntimeException exception) {
                if (failClosed) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Token revocation store is unavailable");
                return true;
            }
            if (projection == null || projection.isBlank()) return true;
            String[] values = projection.split("\\|", -1);
            if (values.length != 2) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Token revocation state is invalid");
            long minimumVersion;
            try { minimumVersion = Long.parseLong(values[1]); }
            catch (NumberFormatException exception) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Token revocation version is invalid"); }
            if ("SUSPENDED".equalsIgnoreCase(values[0]) || token.tokenVersion() < minimumVersion) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access token has been revoked");
            }
            return true;
        }
    }

    private TokenIdentity tokenIdentity(String authorization) {
        try {
            String[] parts = authorization.substring(7).trim().split("\\.");
            if (parts.length != 3) throw new IllegalArgumentException("JWT format is invalid");
            Map<String, Object> claims = objectMapper.readValue(Base64.getUrlDecoder().decode(pad(parts[1])), new TypeReference<Map<String, Object>>() {});
            Object version = claims.get("token_version");
            if (!(version instanceof Number number)) throw new IllegalArgumentException("token_version is missing");
            return new TokenIdentity(UUID.fromString(String.valueOf(claims.get("sub"))), number.longValue());
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access token revocation claims are invalid");
        }
    }

    private static String pad(String value) { return value + "=".repeat((4 - value.length() % 4) % 4); }
    private record TokenIdentity(UUID identityId, long tokenVersion) {}
}
