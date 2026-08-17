package in.craves.notification.delivery;

import in.craves.notification.delivery.NotificationDeliveryModels.DeliveryWorkItem;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

@Component
public class AuthRecipientEmailResolver {
    private static final String INTERNAL_SECRET_HEADER = "X-Craves-Internal-Secret";

    private final NotificationDeliveryProperties properties;
    private final RestClient.Builder restClientBuilder;

    public AuthRecipientEmailResolver(
        NotificationDeliveryProperties properties,
        RestClient.Builder restClientBuilder
    ) {
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
    }

    public String resolve(DeliveryWorkItem item) {
        if (isEmail(item.deliveryAddress())) {
            return item.deliveryAddress().trim();
        }
        if (item.recipientIdentityId() == null) {
            throw new IllegalArgumentException("Notification recipient identity is required for email lookup");
        }
        if (!StringUtils.hasText(properties.getAuthInternalBaseUrl())
            || !StringUtils.hasText(properties.getAuthInternalServiceSecret())) {
            throw new IllegalStateException("Auth email lookup endpoint and secret are not configured");
        }

        RestClient client = restClientBuilder
            .baseUrl(trimTrailingSlash(properties.getAuthInternalBaseUrl()))
            .defaultHeader(INTERNAL_SECRET_HEADER, properties.getAuthInternalServiceSecret())
            .build();
        InternalIdentityEmailResponse response = client.get()
            .uri("/internal/v1/identities/{identityId}/email", item.recipientIdentityId())
            .retrieve()
            .body(InternalIdentityEmailResponse.class);

        if (response == null
            || !item.recipientIdentityId().equals(response.identityId())
            || !"ACTIVE".equals(response.status())
            || !response.emailVerified()
            || !isEmail(response.email())) {
            throw new IllegalStateException("Recipient does not have an active verified email address");
        }
        return response.email().trim();
    }

    private static boolean isEmail(String value) {
        return StringUtils.hasText(value)
            && value.indexOf('@') > 0
            && value.indexOf('@') < value.length() - 1;
    }

    private static String trimTrailingSlash(String value) {
        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    private record InternalIdentityEmailResponse(
        UUID identityId,
        String email,
        boolean emailVerified,
        String status
    ) {
    }
}
