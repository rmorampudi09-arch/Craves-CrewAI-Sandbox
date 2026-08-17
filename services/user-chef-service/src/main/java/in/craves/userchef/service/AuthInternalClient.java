package in.craves.userchef.service;

import in.craves.userchef.config.AuthInternalClientProperties;
import in.craves.userchef.exception.ApiException;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
public class AuthInternalClient {
    private static final String INTERNAL_SECRET_HEADER = "X-Craves-Internal-Secret";

    private final AuthInternalClientProperties properties;
    private final RestClient.Builder restClientBuilder;

    public AuthInternalClient(AuthInternalClientProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
    }

    public void grantChefRole(UUID identityId, UUID sourceApplicationId) {
        if (!StringUtils.hasText(properties.getAuthServiceBaseUrl())) {
            throw new ApiException(500, "AUTH_INTERNAL_URL_NOT_CONFIGURED", "Auth internal base URL is not configured");
        }
        if (!StringUtils.hasText(properties.getServiceSecret())) {
            throw new ApiException(500, "AUTH_INTERNAL_SECRET_NOT_CONFIGURED", "Internal service secret is not configured");
        }

        RestClient client = restClientBuilder
            .baseUrl(properties.getAuthServiceBaseUrl())
            .defaultHeader(INTERNAL_SECRET_HEADER, properties.getServiceSecret())
            .build();

        try {
            client.post()
                .uri("/internal/v1/roles/chef/grant")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new GrantChefRoleRequest(identityId, sourceApplicationId))
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new ApiException(502, "AUTH_INTERNAL_ROLE_GRANT_FAILED", "Auth Service rejected chef role grant: HTTP " + ex.getStatusCode().value());
        } catch (RestClientException ex) {
            throw new ApiException(502, "AUTH_INTERNAL_ROLE_GRANT_FAILED", "Auth Service chef role grant failed");
        }
    }

    private record GrantChefRoleRequest(UUID identityId, UUID sourceApplicationId) {
    }
}
