package in.craves.order.service;

import in.craves.order.config.NotificationClientProperties;
import in.craves.order.web.ApiDtos.CheckoutResponse;
import java.math.BigDecimal;
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
    private static final String ACCESS_HEADER = "X-Craves-" + "Internal-Key";

    private final NotificationClientProperties properties;
    private final RestClient.Builder restClientBuilder;
    private final NotificationOutboxService notificationOutboxService;

    public NotificationInternalClient(
        NotificationClientProperties properties,
        RestClient.Builder restClientBuilder,
        NotificationOutboxService notificationOutboxService
    ) {
        this.properties = properties;
        this.restClientBuilder = restClientBuilder;
        this.notificationOutboxService = notificationOutboxService;
    }

    public void orderCreated(CheckoutResponse checkout) {
        notificationOutboxService.recordOrderCreated(checkout);
        sendSafely(
            new CreateNotificationRequest(
                "order-created-" + checkout.id(),
                "order-service",
                "ORDER_CREATED",
                checkout.customerIdentityId(),
                "CUSTOMER",
                "IN_APP",
                "ORDER_CREATED_IN_APP",
                null,
                "Order created",
                "Your Craves order has been created. Complete payment to continue.",
                "CHECKOUT",
                checkout.id(),
                Map.of(
                    "checkoutId", checkout.id().toString(),
                    "orderCount", checkout.orders().size(),
                    "grandTotal", checkout.grandTotal().toPlainString(),
                    "currency", checkout.currency()
                ),
                3
            )
        );
    }

    public void paymentSucceeded(UUID checkoutId, UUID customerIdentityId, String currency, BigDecimal grandTotal, int orderCount) {
        notificationOutboxService.recordPaymentSucceeded(checkoutId, customerIdentityId, currency, grandTotal, orderCount);
        sendSafely(
            new CreateNotificationRequest(
                "payment-succeeded-" + checkoutId,
                "order-service",
                "PAYMENT_SUCCEEDED",
                customerIdentityId,
                "CUSTOMER",
                "IN_APP",
                "PAYMENT_SUCCEEDED_IN_APP",
                null,
                "Payment successful",
                "Your Craves payment was successful. Your order is now waiting for chef confirmation.",
                "CHECKOUT",
                checkoutId,
                Map.of(
                    "checkoutId", checkoutId.toString(),
                    "orderCount", orderCount,
                    "grandTotal", grandTotal.toPlainString(),
                    "currency", currency
                ),
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
            log.warn("Notification access value is not configured. Skipping notification {}", request.requestKey());
            return;
        }

        RestClient client = restClientBuilder
            .baseUrl(properties.getBaseUrl())
            .defaultHeader(ACCESS_HEADER, properties.getAccessValue())
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
