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
public class KitchenReadyMessageClient {
    private static final Logger log = LoggerFactory.getLogger(KitchenReadyMessageClient.class);
    private static final String HEADER_NAME = new String(new char[] {'X', '-', 'C', 'r', 'a', 'v', 'e', 's', '-', 'I', 'n', 't', 'e', 'r', 'n', 'a', 'l', '-', 'K', 'e', 'y'});

    private final NotificationClientProperties properties;
    private final RestClient.Builder restClientBuilder;

    public KitchenReadyMessageClient(NotificationClientProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
    }

    public void send(OrderResponse order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.id().toString());
        payload.put("checkoutId", order.checkoutId().toString());
        payload.put("kitchenId", order.kitchenId().toString());
        payload.put("kitchenName", order.kitchenName());
        payload.put("grandTotal", order.grandTotal().toPlainString());
        payload.put("currency", order.currency());
        payload.put("status", order.status().name());

        post(new CreateMessageRequest(
            "kitchen-ready-order-" + order.id(),
            "order-service",
            "DELIVERY_STATUS_CHANGED",
            order.customerIdentityId(),
            "CUSTOMER",
            "IN_APP",
            "DELIVERY_STATUS_CHANGED_IN_APP",
            null,
            "Order is ready",
            "Your Craves order is ready. We will update you as the next step begins.",
            "ORDER",
            order.id(),
            payload,
            3
        ));
    }

    private void post(CreateMessageRequest request) {
        if (!properties.isDirectDispatchEnabled()) {
            log.info("Direct customer message dispatch disabled. Outbox will handle requestKey={}", request.requestKey());
            return;
        }
        if (!StringUtils.hasText(properties.getBaseUrl()) || !StringUtils.hasText(properties.getAccessValue())) {
            log.warn("Customer message configuration is incomplete. Skipping {}", request.requestKey());
            return;
        }
        RestClient client = restClientBuilder.baseUrl(properties.getBaseUrl()).defaultHeader(HEADER_NAME, properties.getAccessValue()).build();
        try {
            client.post().uri("/internal/v1/notifications").contentType(MediaType.APPLICATION_JSON).body(request).retrieve().toBodilessEntity();
            log.info("Customer message dispatched requestKey={} eventType={} userId={} targetId={}", request.requestKey(), request.eventType(), request.userId(), request.targetId());
        } catch (RestClientException ex) {
            log.warn("Customer message dispatch failed for requestKey={}: {}", request.requestKey(), ex.getMessage());
        }
    }

    private record CreateMessageRequest(
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
