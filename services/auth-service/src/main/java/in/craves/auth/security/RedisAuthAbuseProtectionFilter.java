package in.craves.auth.security;

import jakarta.annotation.PostConstruct;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class RedisAuthAbuseProtectionFilter extends OncePerRequestFilter {
    private static final String EXCHANGE_PATH = "/api/v1/auth/firebase/exchange";
    private static final String REFRESH_PATH = "/api/v1/auth/refresh";
    private static final DefaultRedisScript<Long> INCREMENT_WITH_EXPIRY = new DefaultRedisScript<>(
        "local value = redis.call('INCR', KEYS[1]); " +
            "if value == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]); end; " +
            "return value;",
        Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final boolean enabled;
    private final int exchangeLimit;
    private final int refreshLimit;
    private final int windowSeconds;
    private final boolean trustForwardedFor;
    private final String keyPrefix;

    public RedisAuthAbuseProtectionFilter(
        StringRedisTemplate redisTemplate,
        @Value("${CRAVES_AUTH_RATE_LIMIT_ENABLED:false}") boolean enabled,
        @Value("${CRAVES_AUTH_RATE_LIMIT_EXCHANGE_LIMIT:0}") int exchangeLimit,
        @Value("${CRAVES_AUTH_RATE_LIMIT_REFRESH_LIMIT:0}") int refreshLimit,
        @Value("${CRAVES_AUTH_RATE_LIMIT_WINDOW_SECONDS:60}") int windowSeconds,
        @Value("${CRAVES_AUTH_RATE_LIMIT_TRUST_FORWARDED_FOR:false}") boolean trustForwardedFor,
        @Value("${CRAVES_AUTH_RATE_LIMIT_KEY_PREFIX:craves:auth:rate}") String keyPrefix
    ) {
        this.redisTemplate = redisTemplate;
        this.enabled = enabled;
        this.exchangeLimit = exchangeLimit;
        this.refreshLimit = refreshLimit;
        this.windowSeconds = windowSeconds;
        this.trustForwardedFor = trustForwardedFor;
        this.keyPrefix = keyPrefix;
    }

    @PostConstruct
    void validate() {
        if (!enabled) {
            return;
        }
        if (exchangeLimit < 1 || refreshLimit < 1) {
            throw new IllegalStateException(
                "Explicit positive exchange and refresh rate limits are required when Auth rate limiting is enabled"
            );
        }
        if (windowSeconds < 1 || windowSeconds > 3600) {
            throw new IllegalStateException("Auth rate-limit window must be between 1 and 3600 seconds");
        }
        if (!StringUtils.hasText(keyPrefix)) {
            throw new IllegalStateException("Auth rate-limit key prefix is required");
        }
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled || !"POST".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        return !EXCHANGE_PATH.equals(uri) && !REFRESH_PATH.equals(uri);
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        String operation = EXCHANGE_PATH.equals(request.getRequestURI()) ? "exchange" : "refresh";
        int limit = "exchange".equals(operation) ? exchangeLimit : refreshLimit;
        String key = keyPrefix + ":" + operation + ":" + sha256(clientIp(request));
        Long count;
        try {
            count = redisTemplate.execute(
                INCREMENT_WITH_EXPIRY,
                List.of(key),
                Integer.toString(windowSeconds)
            );
        } catch (RuntimeException exception) {
            writeError(
                response,
                HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                "AUTH_RATE_LIMIT_UNAVAILABLE",
                "Authentication protection is temporarily unavailable"
            );
            return;
        }
        if (count == null) {
            writeError(
                response,
                HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                "AUTH_RATE_LIMIT_UNAVAILABLE",
                "Authentication protection is temporarily unavailable"
            );
            return;
        }
        response.setHeader("X-RateLimit-Limit", Integer.toString(limit));
        response.setHeader("X-RateLimit-Remaining", Long.toString(Math.max(0L, limit - count)));
        if (count > limit) {
            response.setHeader("Retry-After", Integer.toString(windowSeconds));
            writeError(
                response,
                429,
                "AUTH_RATE_LIMITED",
                "Too many authentication attempts. Try again later."
            );
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String clientIp(HttpServletRequest request) {
        if (trustForwardedFor) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (StringUtils.hasText(forwarded)) {
                return forwarded.split(",")[0].trim();
            }
        }
        String remote = request.getRemoteAddr();
        return StringUtils.hasText(remote) ? remote : "unknown";
    }

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Rate-limit key could not be hashed", exception);
        }
    }

    private static void writeError(
        HttpServletResponse response,
        int status,
        String code,
        String message
    ) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write(
            "{\"code\":\"" + code + "\",\"message\":\"" + message + "\"}"
        );
    }
}
