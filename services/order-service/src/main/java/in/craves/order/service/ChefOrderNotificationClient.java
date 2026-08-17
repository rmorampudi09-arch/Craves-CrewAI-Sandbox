package in.craves.order.service;

import in.craves.order.config.NotificationClientProperties;
import in.craves.order.web.ApiDtos.OrderResponse;
import java.util.LinkedHashMap;
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
public class ChefOrderNotificationClient {
    private static final Logger log = LoggerFactory.getLogger(ChefOrderNotificationClient.class);
    private static final String HEADER_NAME = new String(new char[] {'X', '-', 'C', 'r', 'a', 'v', 'e', 's', '-', 'I', 'n', 't', 'e', 'r', 'n', 'a', 'l', '-', 'K', 'e', 'y'});

    private final NotificationClientProperties properties;
    private final RestClient.Builder restClientBuilder;

    public ChefOrderNotificationClient(NotificationClientProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
    }

    public void chefAcceptedOrder(OrderResponse order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.id().toString());
        payload.put("checkoutId", order.checkoutId().toString());
        payload.put("kitchenId", order.kitchenId().toString());
        payload.put("kitchenName", order.kitchenName());
        payload.put("prepTimeMinutes", order.prepTimeMinutes());
        payload.put("grandTotal", order.grandTotal().toPlainString());
        payload.put("currency", order.currency());

        sendSafely(
            new CreateNotificationRequest(
                "chef-accepted-order-" + order.id(),
                "order-service",
                "CHEF_ACCEPTED_ORDER",
                order.customerIdentityId(),
                "CUSTOMER",
                "IN_APP",
                "CHEF_ACCEPTED_ORDER_IN_APP",
                null,
                "Chef accepted your order",
                "Chef accepted your Craves order. We will keep you updated as it is prepared.",
                "ORDER",
                order.id(),
                payload,
                3
            )
        );
    }

    private void sendSafely(CreateNotificationRequest request) {
        if (!properties.isDirectDispatchEnabled()) {
            log.info("Direct notification dispatch disabled. Outbox will handle requestKey={}", request.requestKey());
            return;
        }
        if (!StringUtils.hasText(properties.getBaseUrl())) {
            log.warn("Notification URL is not configured. Skipping notification {}", request.requestKey());
            return;
        }
        if (!StringUtils.hasText(properties.getAccessValue())) {
            log.warn("Notification configuration is incomplete. Skipping notification {}", request.requestKey());
            return;
        }

        RestClient client = restClientBuilder
            .baseUrl(properties.getBaseUrl())
            .defaultHeader(HEADER_NAME, properties.getAccessValue())
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
