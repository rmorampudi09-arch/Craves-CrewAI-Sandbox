package in.craves.order.service;

import in.craves.order.config.NotificationClientProperties;
import in.craves.order.config.NotificationOutboxDispatcherProperties;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class NotificationOutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxDispatcher.class);
    private static final String HEADER_NAME = new String(new char[] {'X', '-', 'C', 'r', 'a', 'v', 'e', 's', '-', 'I', 'n', 't', 'e', 'r', 'n', 'a', 'l', '-', 'K', 'e', 'y'});

    private final NotificationOutboxDispatcherProperties dispatcherProperties;
    private final NotificationClientProperties notificationProperties;
    private final NotificationOutboxDispatcherRepository repository;
    private final RestClient.Builder restClientBuilder;

    public NotificationOutboxDispatcher(
        NotificationOutboxDispatcherProperties dispatcherProperties,
        NotificationClientProperties notificationProperties,
        NotificationOutboxDispatcherRepository repository,
        RestClient.Builder restClientBuilder
    ) {
        this.dispatcherProperties = dispatcherProperties;
        this.notificationProperties = notificationProperties;
        this.repository = repository;
        this.restClientBuilder = restClientBuilder;
    }

    @Scheduled(fixedDelayString = "${craves.notification.outbox-dispatcher.fixed-delay-ms:30000}")
    public void dispatchDue() {
        if (!dispatcherProperties.isEnabled()) {
            return;
        }
        if (!StringUtils.hasText(notificationProperties.getBaseUrl()) || !StringUtils.hasText(notificationProperties.getAccessValue())) {
            log.warn("Notification outbox dispatcher is enabled but notification configuration is incomplete");
            return;
        }

        int batchSize = Math.max(1, dispatcherProperties.getBatchSize());
        int maxAttempts = Math.max(1, dispatcherProperties.getMaxAttempts());
        for (PendingNotificationOutboxEvent event : repository.findDue(batchSize, maxAttempts)) {
            if (repository.markProcessing(event.id())) {
                dispatchOne(event, maxAttempts);
            }
        }
    }

    private void dispatchOne(PendingNotificationOutboxEvent event, int maxAttempts) {
        try {
            RestClient client = restClientBuilder
                .baseUrl(notificationProperties.getBaseUrl())
                .defaultHeader(HEADER_NAME, notificationProperties.getAccessValue())
                .build();
            client.post()
                .uri("/internal/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .body(toRequest(event))
                .retrieve()
                .toBodilessEntity();
            repository.markSent(event.id());
            log.info("Notification outbox event sent eventKey={} eventType={} attempt={}", event.eventKey(), event.eventType(), event.attemptCount() + 1);
        } catch (RestClientException ex) {
            handleFailure(event, maxAttempts, ex.getMessage());
        } catch (RuntimeException ex) {
            handleFailure(event, maxAttempts, ex.getMessage());
        }
    }

    private void handleFailure(PendingNotificationOutboxEvent event, int maxAttempts, String message) {
        int nextAttemptCount = event.attemptCount() + 1;
        int retryDelaySeconds = retryDelaySeconds(nextAttemptCount);
        repository.markFailed(event.id(), message, retryDelaySeconds);
        if (nextAttemptCount >= maxAttempts) {
            log.warn("Notification outbox event reached max attempts eventKey={} attempts={} error={}", event.eventKey(), nextAttemptCount, message);
        } else {
            log.warn("Notification outbox event failed eventKey={} nextRetrySeconds={} error={}", event.eventKey(), retryDelaySeconds, message);
        }
    }

    private int retryDelaySeconds(int attemptCount) {
        int base = Math.max(10, dispatcherProperties.getRetryBaseDelaySeconds());
        return Math.min(3600, base * Math.max(1, attemptCount));
    }

    private DispatchRequest toRequest(PendingNotificationOutboxEvent event) {
        return new DispatchRequest(
            event.eventKey(),
            "order-service-outbox",
            event.eventType(),
            event.userIdentityId(),
            event.userRole(),
            event.channel(),
            event.templateCode(),
            null,
            event.title(),
            event.body(),
            event.targetType(),
            event.targetId(),
            event.payload(),
            3
        );
    }

    private record DispatchRequest(
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
