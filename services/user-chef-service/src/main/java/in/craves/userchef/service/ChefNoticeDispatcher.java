package in.craves.userchef.service;

import in.craves.userchef.config.AuthInternalClientProperties;
import in.craves.userchef.config.ChefNoticeDispatcherProperties;
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
public class ChefNoticeDispatcher {
    private static final Logger log = LoggerFactory.getLogger(ChefNoticeDispatcher.class);
    private static final String HEADER_NAME = new String(new char[] {'X', '-', 'C', 'r', 'a', 'v', 'e', 's', '-', 'I', 'n', 't', 'e', 'r', 'n', 'a', 'l', '-', 'K', 'e', 'y'});

    private final ChefNoticeDispatcherProperties dispatcherProperties;
    private final AuthInternalClientProperties internalProperties;
    private final ChefNoticeOutboxRepository repository;
    private final RestClient.Builder restClientBuilder;

    public ChefNoticeDispatcher(
        ChefNoticeDispatcherProperties dispatcherProperties,
        AuthInternalClientProperties internalProperties,
        ChefNoticeOutboxRepository repository,
        RestClient.Builder restClientBuilder
    ) {
        this.dispatcherProperties = dispatcherProperties;
        this.internalProperties = internalProperties;
        this.repository = repository;
        this.restClientBuilder = restClientBuilder;
    }

    @Scheduled(fixedDelayString = "${craves.internal.notice-dispatcher.fixed-delay-ms:30000}")
    public void dispatchDue() {
        if (!dispatcherProperties.isEnabled()) {
            return;
        }
        String key = internalProperties.getNotificationServiceKey();
        if (!StringUtils.hasText(key)) {
            key = internalProperties.getServiceSecret();
        }
        if (!StringUtils.hasText(internalProperties.getNotificationServiceBaseUrl()) || !StringUtils.hasText(key)) {
            log.warn("Chef notice dispatcher is enabled but notification configuration is incomplete");
            return;
        }
        int batchSize = Math.max(1, dispatcherProperties.getBatchSize());
        int maxAttempts = Math.max(1, dispatcherProperties.getMaxAttempts());
        for (PendingChefNoticeOutboxEvent event : repository.findDue(batchSize, maxAttempts)) {
            if (repository.markProcessing(event.id())) {
                dispatchOne(event, key, maxAttempts);
            }
        }
    }

    private void dispatchOne(PendingChefNoticeOutboxEvent event, String key, int maxAttempts) {
        try {
            RestClient client = restClientBuilder
                .baseUrl(internalProperties.getNotificationServiceBaseUrl())
                .defaultHeader(HEADER_NAME, key)
                .build();
            client.post()
                .uri("/internal/v1/notifications")
                .contentType(MediaType.APPLICATION_JSON)
                .body(toRequest(event))
                .retrieve()
                .toBodilessEntity();
            repository.markSent(event.id());
            log.info("Chef notice outbox event sent eventKey={} eventType={} attempt={}", event.eventKey(), event.eventType(), event.attemptCount() + 1);
        } catch (RestClientException ex) {
            handleFailure(event, maxAttempts, ex.getMessage());
        } catch (RuntimeException ex) {
            handleFailure(event, maxAttempts, ex.getMessage());
        }
    }

    private void handleFailure(PendingChefNoticeOutboxEvent event, int maxAttempts, String message) {
        int nextAttemptCount = event.attemptCount() + 1;
        int retryDelaySeconds = retryDelaySeconds(nextAttemptCount);
        repository.markFailed(event.id(), message, retryDelaySeconds);
        if (nextAttemptCount >= maxAttempts) {
            log.warn("Chef notice outbox event reached max attempts eventKey={} attempts={} error={}", event.eventKey(), nextAttemptCount, message);
        } else {
            log.warn("Chef notice outbox event failed eventKey={} nextRetrySeconds={} error={}", event.eventKey(), retryDelaySeconds, message);
        }
    }

    private int retryDelaySeconds(int attemptCount) {
        int base = Math.max(10, dispatcherProperties.getRetryBaseDelaySeconds());
        return Math.min(3600, base * Math.max(1, attemptCount));
    }

    private DispatchRequest toRequest(PendingChefNoticeOutboxEvent event) {
        return new DispatchRequest(
            event.eventKey(),
            "user-chef-service-outbox",
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
