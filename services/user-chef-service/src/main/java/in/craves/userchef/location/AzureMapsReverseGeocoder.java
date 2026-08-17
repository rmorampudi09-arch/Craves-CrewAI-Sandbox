package in.craves.userchef.location;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.userchef.exception.ApiException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.springframework.stereotype.Component;

@Component
public class AzureMapsReverseGeocoder {
    private static final String AZURE_MAPS_RESOURCE = "https://atlas.microsoft.com/";
    private static final String DEFAULT_MAPS_ENDPOINT = "https://atlas.microsoft.com";
    private static final String MAPS_API_VERSION = "2026-01-01";

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private volatile CachedToken cachedToken;

    public AzureMapsReverseGeocoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    public ReverseGeocodedAddress reverseGeocode(BigDecimal latitude, BigDecimal longitude) {
        try {
            String mapsClientId = requiredEnvironment("AZURE_MAPS_CLIENT_ID");
            String endpoint = environmentOrDefault("AZURE_MAPS_ENDPOINT", DEFAULT_MAPS_ENDPOINT)
                .replaceAll("/+$", "");
            String accessToken = managedIdentityToken();
            String coordinates = encode(longitude.toPlainString() + "," + latitude.toPlainString());
            URI uri = URI.create(
                endpoint + "/reverseGeocode?api-version=" + MAPS_API_VERSION
                    + "&coordinates=" + coordinates
                    + "&view=IN"
            );

            HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(7))
                .header("Authorization", "Bearer " + accessToken)
                .header("x-ms-client-id", mapsClientId)
                .header("Accept-Language", "en-IN")
                .header("Accept", "application/geo+json, application/json")
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(
                request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw unavailable();
            }

            ReverseGeocodedAddress parsed = parseResponse(objectMapper.readTree(response.body()));
            if (parsed == null) {
                throw unavailable();
            }
            return parsed;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw unavailable();
        } catch (ApiException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unavailable();
        }
    }

    private synchronized String managedIdentityToken() throws Exception {
        CachedToken current = cachedToken;
        if (current != null && current.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return current.accessToken();
        }

        String identityEndpoint = requiredEnvironment("IDENTITY_ENDPOINT");
        String identityHeader = requiredEnvironment("IDENTITY_HEADER");
        String separator = identityEndpoint.contains("?") ? "&" : "?";
        URI uri = URI.create(
            identityEndpoint + separator
                + "resource=" + encode(AZURE_MAPS_RESOURCE)
                + "&api-version=2019-08-01"
        );
        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(5))
            .header("X-IDENTITY-HEADER", identityHeader)
            .GET()
            .build();
        HttpResponse<String> response = httpClient.send(
            request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw unavailable();
        }

        JsonNode body = objectMapper.readTree(response.body());
        String token = text(body == null ? null : body.get("access_token"));
        if (token == null) {
            throw unavailable();
        }
        Instant expiresAt = parseExpiry(body == null ? null : body.get("expires_on"));
        cachedToken = new CachedToken(token, expiresAt);
        return token;
    }

    static ReverseGeocodedAddress parseResponse(JsonNode root) {
        JsonNode features = root == null ? null : root.get("features");
        if (features == null || !features.isArray() || features.isEmpty()) {
            return null;
        }
        JsonNode feature = features.get(0);
        JsonNode properties = feature == null ? null : feature.get("properties");
        JsonNode address = properties == null ? null : properties.get("address");
        if (properties == null || address == null || !address.isObject()) {
            return null;
        }

        String formattedAddress = text(address.get("formattedAddress"));
        if (formattedAddress == null) {
            return null;
        }

        JsonNode districts = address.get("adminDistricts");
        String state = firstText(
            districtField(districts, 0, "name"),
            districtField(districts, 0, "shortName")
        );
        String district = firstText(
            districtField(districts, 1, "name"),
            districtField(districts, 2, "name"),
            districtField(districts, 3, "name")
        );
        String houseNumber = text(address.get("streetNumber"));
        String street = text(address.get("streetName"));
        String locality = text(address.get("locality"));
        String area = firstText(text(address.get("neighborhood")), locality, district);
        String city = firstText(locality, district);
        JsonNode countryRegion = address.get("countryRegion");
        String confidence = text(properties.get("confidence"));
        if (!"High".equals(confidence) && !"Medium".equals(confidence) && !"Low".equals(confidence)) {
            confidence = null;
        }

        return new ReverseGeocodedAddress(
            formattedAddress,
            houseNumber,
            street,
            area,
            city,
            district,
            state,
            text(address.get("postalCode")),
            countryRegion != null && countryRegion.isObject() ? text(countryRegion.get("name")) : null,
            confidence,
            houseNumber != null
        );
    }

    private static String districtField(JsonNode districts, int index, String field) {
        if (districts == null || !districts.isArray() || districts.size() <= index) {
            return null;
        }
        JsonNode district = districts.get(index);
        return district != null && district.isObject() ? text(district.get(field)) : null;
    }

    private static String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static String text(JsonNode node) {
        if (node == null || !node.isTextual()) {
            return null;
        }
        String value = node.asText().trim();
        return value.isEmpty() ? null : value;
    }

    private static Instant parseExpiry(JsonNode node) {
        if (node == null || node.isNull()) {
            return Instant.now().plusSeconds(300);
        }
        if (node.isNumber()) {
            return Instant.ofEpochSecond(node.asLong());
        }
        String value = text(node);
        if (value == null) {
            return Instant.now().plusSeconds(300);
        }
        try {
            return Instant.ofEpochSecond(Long.parseLong(value));
        } catch (NumberFormatException ignored) {
            try {
                return Instant.parse(value);
            } catch (DateTimeParseException ignoredAgain) {
                return Instant.now().plusSeconds(300);
            }
        }
    }

    private static String requiredEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw unavailable();
        }
        return value.trim();
    }

    private static String environmentOrDefault(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static ApiException unavailable() {
        return new ApiException(
            503,
            "REVERSE_GEOCODING_UNAVAILABLE",
            "Craves could not identify this address right now. Please try again."
        );
    }

    private record CachedToken(String accessToken, Instant expiresAt) {
    }

    public record ReverseGeocodedAddress(
        String formattedAddress,
        String houseNumber,
        String street,
        String area,
        String city,
        String district,
        String state,
        String postalCode,
        String country,
        String confidence,
        boolean preciseHouseNumber
    ) {
    }
}
