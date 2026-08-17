package in.craves.integration.delivery.shiprocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.integration.config.ShiprocketProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "craves.providers.shiprocket", name = "enabled", havingValue = "true")
public class ShiprocketAuthClient {
    private static final Duration DOCUMENTED_TOKEN_LIFETIME = Duration.ofDays(10);

    private final ShiprocketProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private volatile CachedToken cachedToken;

    public ShiprocketAuthClient(ShiprocketProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
            .build();
    }

    public String bearerToken() {
        CachedToken current = cachedToken;
        Instant refreshBefore = Instant.now().plus(properties.getAuthRefreshSkewMinutes(), ChronoUnit.MINUTES);
        if (current != null && refreshBefore.isBefore(current.expiresAt())) {
            return current.value();
        }
        synchronized (this) {
            current = cachedToken;
            refreshBefore = Instant.now().plus(properties.getAuthRefreshSkewMinutes(), ChronoUnit.MINUTES);
            if (current != null && refreshBefore.isBefore(current.expiresAt())) {
                return current.value();
            }
            cachedToken = login();
            return cachedToken.value();
        }
    }

    public void invalidate() {
        cachedToken = null;
    }

    private CachedToken login() {
        try {
            JsonNode body = objectMapper.createObjectNode()
                .put("email", properties.getEmail())
                .put("password", properties.getPassword());
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(properties.normalizedBaseUrl() + "/auth/login"))
                .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                .build();
            HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ShiprocketAuthenticationException(
                    "Shiprocket authentication failed with HTTP " + response.statusCode()
                );
            }
            JsonNode json = objectMapper.readTree(response.body());
            String token = json.path("token").asText("").trim();
            if (token.isBlank()) {
                throw new ShiprocketAuthenticationException(
                    "Shiprocket authentication response did not contain a token"
                );
            }
            return new CachedToken(token, Instant.now().plus(DOCUMENTED_TOKEN_LIFETIME));
        } catch (ShiprocketAuthenticationException ex) {
            throw ex;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ShiprocketAuthenticationException("Shiprocket authentication was interrupted", ex);
        } catch (Exception ex) {
            throw new ShiprocketAuthenticationException("Shiprocket authentication failed", ex);
        }
    }

    private record CachedToken(String value, Instant expiresAt) {}

    public static class ShiprocketAuthenticationException extends RuntimeException {
        public ShiprocketAuthenticationException(String message) {
            super(message);
        }

        public ShiprocketAuthenticationException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
