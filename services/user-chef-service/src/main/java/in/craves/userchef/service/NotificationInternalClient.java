package in.craves.userchef.service;

import in.craves.userchef.config.AuthInternalClientProperties;
import in.craves.userchef.web.ApiDtos.ChefApplicationResponse;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class NotificationInternalClient {
    private static final Logger log = LoggerFactory.getLogger(NotificationInternalClient.class);
    private static final String INTERNAL_KEY_HEADER = "X-Craves-" + "Internal-Key";

    private final AuthInternalClientProperties properties;
    private final RestClient.Builder restClientBuilder;
    private final ReviewEventBuffer reviewEventBuffer;

    public NotificationInternalClient(AuthInternalClientProperties properties, RestClient.Builder restClientBuilder, ReviewEventBuffer reviewEventBuffer) {
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
        this.reviewEventBuffer = reviewEventBuffer;
    }

    public void chefApproved(ChefApplicationResponse application) {
        reviewEventBuffer.accepted(application);
        sendSafely(
            new CreateNotificationRequest(
                "chef-approved-" + application.id(),
                "user-chef-service",
                "CHEF_APPROVED",
                application.identityId(),
                "CHEF",
                "IN_APP",
                "CHEF_APPROVED_IN_APP",
                null,
                "Chef profile approved",
                "Your Craves chef profile is approved. You can now publish your kitchen and menu.",
                "CHEF_APPLICATION",
                application.id(),
                Map.of("applicationId", application.id().toString()),
                3
            )
        );
    }

    public void chefRejected(ChefApplicationResponse application) {
        reviewEventBuffer.returned(application);
        String reason = StringUtils.hasText(application.rejectionReason()) ? application.rejectionReason() : "Please review your application details and submit again.";
        sendSafely(
            new CreateNotificationRequest(
                "chef-rejected-" + application.id() + "-" + application.reviewedAt(),
                "user-chef-service",
                "CHEF_REJECTED",
                application.identityId(),
                "CHEF",
                "IN_APP",
                "CHEF_REJECTED_IN_APP",
                null,
                "Chef profile needs changes",
                "Your Craves chef profile was not approved. Reason: " + reason,
                "CHEF_APPLICATION",
                application.id(),
                Map.of("applicationId", application.id().toString(), "reason", reason),
                3
            )
        );
    }

    private void sendSafely(CreateNotificationRequest request) {
        if (!properties.isNotificationDirectDispatchEnabled()) {
            log.info("Direct notification dispatch disabled. Outbox will handle requestKey={}", request.requestKey());
            return;
        }
        if (!StringUtils.hasText(properties.getNotificationServiceBaseUrl())) {
            log.warn("Notification Service URL is not configured. Skipping notification {}", request.requestKey());
            return;
        }
        String key = properties.getNotificationServiceKey();
        if (!StringUtils.hasText(key)) {
            key = properties.getServiceSecret();
        }
        if (!StringUtils.hasText(key)) {
            log.warn("Notification internal key is not configured. Skipping notification {}", request.requestKey());
            return;
        }

        RestClient client = restClientBuilder
            .baseUrl(properties.getNotificationServiceBaseUrl())
            .defaultHeader(INTERNAL_KEY_HEADER, key)
            .build();

        try {
            client.post()
                .uri("/internal/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
            log.info(
                "Notification dispatched requestKey={} eventType={} userId={} channel={} targetType={} targetId={}",
                request.requestKey(),
                request.eventType(),
                request.userId(),
                request.channel(),
                request.targetType(),
                request.targetId()
            );
        } catch (RestClientException ex) {
            log.warn("Notification dispatch failed for requestKey={}: {}", request.requestKey(), ex.getMessage());
        }
    }

    private record CreateNotificationRequest(
        String requestKey,
        String sourceService,
        String eventType,
        UUID userId,
        String userRole,
        String channel,
        String templateCode,
        String address,
        String title,
        String body,
        String targetType,
        UUID targetId,
        Map<String, Object> payload,
        Integer priority
    ) {
    }
}
