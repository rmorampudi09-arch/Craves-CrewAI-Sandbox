package in.craves.integration.delivery.shiprocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.integration.config.ShiprocketProperties;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.StringJoiner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "craves.providers.shiprocket", name = "enabled", havingValue = "true")
public class ShiprocketTransport {
    private final ShiprocketProperties properties;
    private final ShiprocketAuthClient authClient;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ShiprocketTransport(ShiprocketProperties properties,
                               ShiprocketAuthClient authClient,
                               ObjectMapper objectMapper) {
        this(
            properties,
            authClient,
            objectMapper,
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
                .build()
        );
    }

    ShiprocketTransport(ShiprocketProperties properties,
                        ShiprocketAuthClient authClient,
                        ObjectMapper objectMapper,
                        HttpClient httpClient) {
        this.properties = Objects.requireNonNull(properties, "properties is required");
        this.authClient = Objects.requireNonNull(authClient, "authClient is required");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient is required");
    }

    public JsonNode get(String path, Map<String, String> query) {
        RuntimeException last = null;
        for (int attempt = 1; attempt <= properties.getReadRetryAttempts(); attempt++) {
            try {
                HttpResponse<String> response = send(
                    "GET",
                    uri(path, query),
                    null,
                    authClient.bearerToken()
                );
                if (response.statusCode() == 401 && attempt < properties.getReadRetryAttempts()) {
                    authClient.invalidate();
                    last = new ShiprocketApiException(401, "Shiprocket read request was unauthorized", false);
                    continue;
                }
                if (retryableReadStatus(response.statusCode())
                    && attempt < properties.getReadRetryAttempts()) {
                    sleepBackoff(attempt);
                    last = new ShiprocketApiException(
                        response.statusCode(),
                        "Shiprocket read request returned a transient HTTP status",
                        false
                    );
                    continue;
                }
                return requireSuccess(response, false);
            } catch (ShiprocketApiException ex) {
                if (!ex.isRetryableRead() || attempt >= properties.getReadRetryAttempts()) {
                    throw ex;
                }
                last = ex;
                sleepBackoff(attempt);
            } catch (IOException ex) {
                last = new ShiprocketApiException(null, "Shiprocket read request failed", false, ex);
                if (attempt >= properties.getReadRetryAttempts()) {
                    throw last;
                }
                sleepBackoff(attempt);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ShiprocketApiException(null, "Shiprocket read request was interrupted", false, ex);
            }
        }
        throw last == null
            ? new ShiprocketApiException(null, "Shiprocket read request failed", false)
            : last;
    }

    /**
     * Sends a mutating API request without blind retries. The only automatic retry is a single
     * refresh after an explicit HTTP 401, because that response proves the provider rejected the
     * request before accepting it. Network failures, rate limits and 5xx responses remain
     * uncertain and must be reconciled before any retry or provider fallback.
     */
    public JsonNode mutate(String path, JsonNode body) {
        try {
            URI requestUri = uri(path, Map.of());
            HttpResponse<String> response = send(
                "POST",
                requestUri,
                body,
                authClient.bearerToken()
            );
            if (response.statusCode() == 401) {
                authClient.invalidate();
                response = send(
                    "POST",
                    requestUri,
                    body,
                    authClient.bearerToken()
                );
            }
            boolean uncertain = response.statusCode() >= 500 || response.statusCode() == 429;
            return requireSuccess(response, uncertain);
        } catch (ShiprocketApiException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new ShiprocketApiException(
                null,
                "Shiprocket mutation response was not received",
                true,
                ex
            );
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ShiprocketApiException(
                null,
                "Shiprocket mutation was interrupted before outcome could be confirmed",
                true,
                ex
            );
        }
    }

    private HttpResponse<String> send(String method,
                                      URI uri,
                                      JsonNode body,
                                      String token) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
            .uri(uri)
            .timeout(Duration.ofSeconds(properties.getReadTimeoutSeconds()))
            .header("Accept", "application/json")
            .header("Authorization", "Bearer " + token);

        if ("POST".equals(method)) {
            String serialized;
            try {
                serialized = objectMapper.writeValueAsString(body);
            } catch (Exception ex) {
                throw new IllegalArgumentException("Shiprocket request body could not be serialized", ex);
            }
            builder.header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(serialized));
        } else {
            builder.GET();
        }
        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private JsonNode requireSuccess(HttpResponse<String> response, boolean uncertain) {
        JsonNode body = parseBody(response.body());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return body;
        }
        String providerMessage = providerMessage(body);
        boolean retryable = retryableReadStatus(response.statusCode());
        throw new ShiprocketApiException(
            response.statusCode(),
            "Shiprocket API HTTP " + response.statusCode()
                + (providerMessage.isBlank() ? "" : ": " + providerMessage),
            uncertain,
            retryable
        );
    }

    private JsonNode parseBody(String raw) {
        try {
            if (raw == null || raw.isBlank()) {
                return objectMapper.createObjectNode();
            }
            return objectMapper.readTree(raw);
        } catch (Exception ex) {
            throw new ShiprocketApiException(
                null,
                "Shiprocket returned a non-JSON response",
                false,
                ex
            );
        }
    }

    private URI uri(String path, Map<String, String> query) {
        StringBuilder value = new StringBuilder(properties.normalizedBaseUrl()).append(path);
        if (query != null && !query.isEmpty()) {
            StringJoiner joiner = new StringJoiner("&");
            query.forEach((key, item) -> {
                if (item != null && !item.isBlank()) {
                    joiner.add(encode(key) + "=" + encode(item));
                }
            });
            String encoded = joiner.toString();
            if (!encoded.isBlank()) {
                value.append('?').append(encoded);
            }
        }
        return URI.create(value.toString());
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static boolean retryableReadStatus(int status) {
        return status == 429 || status == 500 || status == 502 || status == 503 || status == 504;
    }

    private static String providerMessage(JsonNode body) {
        for (String field : new String[]{"message", "error", "errors"}) {
            JsonNode value = body.get(field);
            if (value != null && !value.isNull()) {
                String text = value.isTextual() ? value.asText() : value.toString();
                if (!text.isBlank()) {
                    return text.length() <= 400 ? text : text.substring(0, 400);
                }
            }
        }
        return "";
    }

    private static void sleepBackoff(int attempt) {
        long millis = Math.min(2000L, 250L * (1L << Math.max(0, attempt - 1)));
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ShiprocketApiException(null, "Shiprocket retry backoff was interrupted", false, ex);
        }
    }

    public static class ShiprocketApiException extends RuntimeException {
        private final Integer httpStatus;
        private final boolean uncertainMutation;
        private final boolean retryableRead;

        public ShiprocketApiException(Integer httpStatus, String message, boolean uncertainMutation) {
            this(httpStatus, message, uncertainMutation, false, null);
        }

        public ShiprocketApiException(Integer httpStatus,
                                      String message,
                                      boolean uncertainMutation,
                                      Throwable cause) {
            this(httpStatus, message, uncertainMutation, false, cause);
        }

        public ShiprocketApiException(Integer httpStatus,
                                      String message,
                                      boolean uncertainMutation,
                                      boolean retryableRead) {
            this(httpStatus, message, uncertainMutation, retryableRead, null);
        }

        private ShiprocketApiException(Integer httpStatus,
                                       String message,
                                       boolean uncertainMutation,
                                       boolean retryableRead,
                                       Throwable cause) {
            super(message, cause);
            this.httpStatus = httpStatus;
            this.uncertainMutation = uncertainMutation;
            this.retryableRead = retryableRead;
        }

        public Integer httpStatus() { return httpStatus; }
        public boolean uncertainMutation() { return uncertainMutation; }
        public boolean isRetryableRead() { return retryableRead; }
    }
}
