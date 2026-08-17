package in.craves.order.service;

import in.craves.order.web.ApiDtos.CheckoutResponse;
import in.craves.order.web.ApiDtos.OrderResponse;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class NotificationOutboxService {
    private static final Logger log = LoggerFactory.getLogger(NotificationOutboxService.class);
    private static final String CUSTOMER = "CUSTOMER";
    private static final String IN_APP = "IN_APP";
    private static final String CHECKOUT = "CHECKOUT";
    private static final String ORDER = "ORDER";

    private final NotificationOutboxRepository repository;

    public NotificationOutboxService(NotificationOutboxRepository repository) {
        this.repository = repository;
    }

    public void recordOrderCreated(CheckoutResponse checkout) {
        saveBestEffort(new NotificationOutboxEvent(
            "order-created-" + checkout.id(),
            "ORDER_CREATED",
            CHECKOUT,
            checkout.id(),
            checkout.customerIdentityId(),
            CUSTOMER,
            IN_APP,
            "ORDER_CREATED_IN_APP",
            "Order created",
            "Your Craves order has been created. Complete payment to continue.",
            CHECKOUT,
            checkout.id(),
            Map.of(
                "checkoutId", checkout.id().toString(),
                "orderCount", checkout.orders().size(),
                "grandTotal", checkout.grandTotal().toPlainString(),
                "currency", checkout.currency()
            )
        ));
    }

    public void recordPaymentSucceeded(UUID checkoutId, UUID customerIdentityId, String currency, BigDecimal grandTotal, int orderCount) {
        saveBestEffort(new NotificationOutboxEvent(
            "payment-succeeded-" + checkoutId,
            "PAYMENT_SUCCEEDED",
            CHECKOUT,
            checkoutId,
            customerIdentityId,
            CUSTOMER,
            IN_APP,
            "PAYMENT_SUCCEEDED_IN_APP",
            "Payment successful",
            "Your Craves payment was successful. Your order is now waiting for chef confirmation.",
            CHECKOUT,
            checkoutId,
            Map.of(
                "checkoutId", checkoutId.toString(),
                "orderCount", orderCount,
                "grandTotal", grandTotal.toPlainString(),
                "currency", currency
            )
        ));
    }

    public void recordChefAccepted(OrderResponse order) {
        saveBestEffort(new NotificationOutboxEvent(
            "chef-accepted-order-" + order.id(),
            "CHEF_ACCEPTED_ORDER",
            ORDER,
            order.id(),
            order.customerIdentityId(),
            CUSTOMER,
            IN_APP,
            "CHEF_ACCEPTED_ORDER_IN_APP",
            "Chef accepted your order",
            "Chef accepted your Craves order. We will keep you updated as it is prepared.",
            ORDER,
            order.id(),
            orderPayload(order)
        ));
    }

    public void recordChefRejected(OrderResponse order) {
        Map<String, Object> payload = orderPayload(order);
        payload.put("reason", order.chefResponseNote());
        saveBestEffort(new NotificationOutboxEvent(
            "chef-rejected-order-" + order.id(),
            "CHEF_REJECTED_ORDER",
            ORDER,
            order.id(),
            order.customerIdentityId(),
            CUSTOMER,
            IN_APP,
            "CHEF_REJECTED_ORDER_IN_APP",
            "Order update",
            "Your Craves order could not be confirmed by the kitchen. We will update you about the next steps.",
            ORDER,
            order.id(),
            payload
        ));
    }

    public void recordReadyForPickup(OrderResponse order) {
        Map<String, Object> payload = orderPayload(order);
        payload.put("status", order.status().name());
        saveBestEffort(new NotificationOutboxEvent(
            "kitchen-ready-order-" + order.id(),
            "DELIVERY_STATUS_CHANGED",
            ORDER,
            order.id(),
            order.customerIdentityId(),
            CUSTOMER,
            IN_APP,
            "DELIVERY_STATUS_CHANGED_IN_APP",
            "Order is ready",
            "Your Craves order is ready. We will update you as the next step begins.",
            ORDER,
            order.id(),
            payload
        ));
    }

    private void saveBestEffort(NotificationOutboxEvent event) {
        try {
            repository.savePending(event);
            log.info("Notification outbox recorded eventKey={} eventType={} aggregateType={} aggregateId={}", event.eventKey(), event.eventType(), event.aggregateType(), event.aggregateId());
        } catch (RuntimeException ex) {
            log.warn("Notification outbox record failed eventKey={}: {}", event.eventKey(), ex.getMessage());
        }
    }

    private Map<String, Object> orderPayload(OrderResponse order) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.id().toString());
        payload.put("checkoutId", order.checkoutId().toString());
        payload.put("kitchenId", order.kitchenId().toString());
        payload.put("kitchenName", order.kitchenName());
        payload.put("prepTimeMinutes", order.prepTimeMinutes());
        payload.put("grandTotal", order.grandTotal().toPlainString());
        payload.put("currency", order.currency());
        return payload;
    }
}
