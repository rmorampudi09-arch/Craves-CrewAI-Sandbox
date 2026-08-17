package in.craves.order.service;

import in.craves.order.event.RefundRequestedEventFactory;
import in.craves.order.event.RefundRequestedEventSource;
import in.craves.order.event.SerializedDomainEvent;
import in.craves.order.exception.OrderApiException;
import in.craves.order.outbox.OrderDomainOutboxRepository;
import in.craves.order.security.CravesPrincipal;
import in.craves.order.web.ApiDtos.ChefRejectRequest;
import in.craves.order.web.ApiDtos.OrderResponse;
import in.craves.order.web.ApiDtos.OrderStatus;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ChefAcceptanceResolutionService {
    public static final String CHEF_DECLINED = "CHEF_DECLINED";
    public static final String CHEF_ACCEPTANCE_TIMEOUT = "CHEF_ACCEPTANCE_TIMEOUT";

    private final JdbcTemplate jdbcTemplate;
    private final OrderService orderService;
    private final RefundRequestedEventFactory refundEventFactory;
    private final OrderDomainOutboxRepository domainOutboxRepository;
    private final NotificationOutboxRepository notificationOutboxRepository;

    public ChefAcceptanceResolutionService(
        JdbcTemplate jdbcTemplate,
        OrderService orderService,
        RefundRequestedEventFactory refundEventFactory,
        OrderDomainOutboxRepository domainOutboxRepository,
        NotificationOutboxRepository notificationOutboxRepository
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.orderService = orderService;
        this.refundEventFactory = refundEventFactory;
        this.domainOutboxRepository = domainOutboxRepository;
        this.notificationOutboxRepository = notificationOutboxRepository;
    }

    @Transactional
    public OrderResponse reject(
        CravesPrincipal principal,
        UUID orderId,
        ChefRejectRequest request,
        UUID correlationId,
        String idempotencyKey
    ) {
        orderService.getOrderForChef(principal, orderId);
        LockedOrder lockedOrder = lockOrder(orderId);

        if (lockedOrder.status() == OrderStatus.CHEF_REJECTED
            && (CHEF_DECLINED.equals(lockedOrder.rejectionCode())
                || CHEF_ACCEPTANCE_TIMEOUT.equals(lockedOrder.rejectionCode()))) {
            return orderService.getOrderForChef(principal, orderId);
        }
        if (lockedOrder.status() != OrderStatus.CHEF_ACCEPTANCE_PENDING) {
            throw OrderApiException.conflict(
                "ORDER_NOT_WAITING_FOR_CHEF_ACCEPTANCE",
                "Only an order awaiting chef acceptance can be rejected."
            );
        }

        boolean expired = lockedOrder.acceptanceExpiresAt() != null
            && !lockedOrder.databaseNow().isBefore(lockedOrder.acceptanceExpiresAt());
        String rejectionCode = expired ? CHEF_ACCEPTANCE_TIMEOUT : CHEF_DECLINED;
        String note = expired
            ? "Kitchen did not accept the order within 30 minutes"
            : safeReason(request == null ? null : request.reason());

        rejectAndRequestRefund(
            lockedOrder,
            rejectionCode,
            note == null ? "Chef declined the order" : note,
            expired ? null : principal.identityId(),
            correlationId,
            expired ? "timeout:" + orderId : idempotencyKey
        );
        return orderService.getOrderForChef(principal, orderId);
    }

    @Transactional
    public boolean timeoutExpiredOrder(UUID orderId) {
        LockedOrder lockedOrder = lockOrder(orderId);
        if (lockedOrder.status() != OrderStatus.CHEF_ACCEPTANCE_PENDING) {
            return false;
        }
        if (lockedOrder.acceptanceExpiresAt() == null
            || lockedOrder.databaseNow().isBefore(lockedOrder.acceptanceExpiresAt())) {
            return false;
        }

        rejectAndRequestRefund(
            lockedOrder,
            CHEF_ACCEPTANCE_TIMEOUT,
            "Kitchen did not accept the order within 30 minutes",
            null,
            lockedOrder.checkoutId(),
            "timeout:" + orderId
        );
        return true;
    }

    @Transactional
    public boolean recordFirstReminder(UUID orderId, UUID chefIdentityId, int reminderMinutes) {
        return recordReminder(orderId, chefIdentityId, reminderMinutes, ReminderStage.FIRST);
    }

    @Transactional
    public boolean recordSecondReminder(UUID orderId, UUID chefIdentityId, int reminderMinutes) {
        return recordReminder(orderId, chefIdentityId, reminderMinutes, ReminderStage.SECOND);
    }

    private boolean recordReminder(
        UUID orderId,
        UUID chefIdentityId,
        int reminderMinutes,
        ReminderStage stage
    ) {
        if (chefIdentityId == null) {
            return false;
        }
        LockedOrder lockedOrder = lockOrder(orderId);
        if (lockedOrder.status() != OrderStatus.CHEF_ACCEPTANCE_PENDING
            || lockedOrder.acceptanceRequestedAt() == null
            || lockedOrder.acceptanceExpiresAt() == null
            || !lockedOrder.databaseNow().isBefore(lockedOrder.acceptanceExpiresAt())
            || lockedOrder.databaseNow().isBefore(lockedOrder.acceptanceRequestedAt().plusSeconds(reminderMinutes * 60L))) {
            return false;
        }
        if (stage == ReminderStage.FIRST && lockedOrder.firstReminderRecordedAt() != null) {
            return false;
        }
        if (stage == ReminderStage.SECOND
            && (lockedOrder.firstReminderRecordedAt() == null
                || lockedOrder.secondReminderRecordedAt() != null)) {
            return false;
        }

        String eventKey = stage.eventKeyPrefix + orderId;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", orderId.toString());
        payload.put("checkoutId", lockedOrder.checkoutId().toString());
        payload.put("kitchenId", lockedOrder.kitchenId().toString());
        payload.put("kitchenName", lockedOrder.kitchenName());
        payload.put("acceptanceExpiresAt", lockedOrder.acceptanceExpiresAt().toString());
        payload.put("reminderMinutes", reminderMinutes);

        notificationOutboxRepository.savePending(new NotificationOutboxEvent(
            eventKey,
            stage.eventType,
            "ORDER",
            orderId,
            chefIdentityId,
            "CHEF",
            "IN_APP",
            stage.templateCode,
            stage.title,
            stage.body,
            "ORDER",
            orderId,
            payload
        ));

        String column = stage == ReminderStage.FIRST
            ? "chef_acceptance_reminder_10_recorded_at"
            : "chef_acceptance_reminder_20_recorded_at";
        jdbcTemplate.update(
            "UPDATE order_schema.customer_order SET " + column + " = now(), updated_at = now() WHERE id = ?",
            orderId
        );
        return true;
    }

    private void rejectAndRequestRefund(
        LockedOrder order,
        String rejectionCode,
        String note,
        UUID actorIdentityId,
        UUID correlationId,
        String idempotencyKey
    ) {
        RefundDecision decision = jdbcTemplate.query(
            """
                UPDATE order_schema.customer_order
                SET status = 'CHEF_REJECTED',
                    chef_rejection_code = ?,
                    chef_response_note = ?,
                    refund_requested_at = now(),
                    refund_requested_amount = grand_total,
                    updated_at = now()
                WHERE id = ?
                  AND status = 'CHEF_ACCEPTANCE_PENDING'
                RETURNING refund_requested_at, refund_requested_amount
                """,
            (resultSet, rowNumber) -> new RefundDecision(
                instant(resultSet, "refund_requested_at"),
                resultSet.getBigDecimal("refund_requested_amount")
            ),
            rejectionCode,
            note,
            order.orderId()
        ).stream().findFirst().orElseThrow(() -> OrderApiException.conflict(
            "ORDER_DECISION_ALREADY_COMPLETED",
            "The chef acceptance decision was already completed."
        ));

        jdbcTemplate.update(
            """
                INSERT INTO order_schema.order_status_history (
                    id, order_id, old_status, new_status,
                    actor_identity_id, reason, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, now())
                """,
            UUID.randomUUID(),
            order.orderId(),
            OrderStatus.CHEF_ACCEPTANCE_PENDING.name(),
            OrderStatus.CHEF_REJECTED.name(),
            actorIdentityId,
            rejectionCode
        );

        RefundRequestedEventSource eventSource = new RefundRequestedEventSource(
            order.checkoutId(),
            order.orderId(),
            order.customerIdentityId(),
            decision.refundAmount(),
            order.currency(),
            rejectionCode,
            decision.requestedAt()
        );
        SerializedDomainEvent event = refundEventFactory.create(eventSource, correlationId, idempotencyKey);
        if (!domainOutboxRepository.insert(order.orderId(), event)) {
            throw OrderApiException.conflict(
                "REFUND_REQUESTED_EVENT_ALREADY_EXISTS",
                "The refund request event already exists for this order."
            );
        }
        recordCustomerRefundNotification(order, rejectionCode, decision);
    }

    private void recordCustomerRefundNotification(
        LockedOrder order,
        String rejectionCode,
        RefundDecision decision
    ) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.orderId().toString());
        payload.put("checkoutId", order.checkoutId().toString());
        payload.put("kitchenId", order.kitchenId().toString());
        payload.put("kitchenName", order.kitchenName());
        payload.put("reason", rejectionCode);
        payload.put("refundAmount", decision.refundAmount().toPlainString());
        payload.put("currency", order.currency());
        payload.put("requestedAt", decision.requestedAt().toString());

        String body = CHEF_ACCEPTANCE_TIMEOUT.equals(rejectionCode)
            ? "The kitchen did not confirm your order in time. Your full refund for this kitchen order has been requested."
            : "The kitchen could not confirm your order. Your full refund for this kitchen order has been requested.";

        notificationOutboxRepository.savePending(new NotificationOutboxEvent(
            "refund-requested-order-" + order.orderId(),
            "REFUND_REQUESTED",
            "ORDER",
            order.orderId(),
            order.customerIdentityId(),
            "CUSTOMER",
            "IN_APP",
            "REFUND_REQUESTED_IN_APP",
            "Refund requested",
            body,
            "ORDER",
            order.orderId(),
            payload
        ));
    }

    private LockedOrder lockOrder(UUID orderId) {
        return jdbcTemplate.query(
            """
                SELECT id, checkout_id, customer_identity_id, kitchen_id, kitchen_name_snapshot,
                       status, currency, grand_total, chef_rejection_code,
                       chef_acceptance_requested_at, chef_acceptance_expires_at,
                       chef_acceptance_reminder_10_recorded_at,
                       chef_acceptance_reminder_20_recorded_at,
                       now() AS database_now
                FROM order_schema.customer_order
                WHERE id = ?
                FOR UPDATE
                """,
            this::mapLockedOrder,
            orderId
        ).stream().findFirst().orElseThrow(() -> OrderApiException.notFound(
            "ORDER_NOT_FOUND",
            "The requested order was not found."
        ));
    }

    private LockedOrder mapLockedOrder(ResultSet resultSet, int rowNumber) throws SQLException {
        return new LockedOrder(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("checkout_id", UUID.class),
            resultSet.getObject("customer_identity_id", UUID.class),
            resultSet.getObject("kitchen_id", UUID.class),
            resultSet.getString("kitchen_name_snapshot"),
            OrderStatus.valueOf(resultSet.getString("status")),
            resultSet.getString("currency"),
            resultSet.getBigDecimal("grand_total"),
            resultSet.getString("chef_rejection_code"),
            instant(resultSet, "chef_acceptance_requested_at"),
            instant(resultSet, "chef_acceptance_expires_at"),
            instant(resultSet, "chef_acceptance_reminder_10_recorded_at"),
            instant(resultSet, "chef_acceptance_reminder_20_recorded_at"),
            instant(resultSet, "database_now")
        );
    }

    private static String safeReason(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private enum ReminderStage {
        FIRST(
            "chef-acceptance-reminder-10-",
            "CHEF_ACCEPTANCE_REMINDER",
            "CHEF_ACCEPTANCE_REMINDER_IN_APP",
            "Order awaiting confirmation",
            "Please accept or reject this order. The customer is waiting for your response."
        ),
        SECOND(
            "chef-acceptance-reminder-20-",
            "CHEF_ACCEPTANCE_URGENT_REMINDER",
            "CHEF_ACCEPTANCE_URGENT_REMINDER_IN_APP",
            "Urgent: order confirmation needed",
            "This order will expire soon. Please accept or reject it now."
        );

        private final String eventKeyPrefix;
        private final String eventType;
        private final String templateCode;
        private final String title;
        private final String body;

        ReminderStage(
            String eventKeyPrefix,
            String eventType,
            String templateCode,
            String title,
            String body
        ) {
            this.eventKeyPrefix = eventKeyPrefix;
            this.eventType = eventType;
            this.templateCode = templateCode;
            this.title = title;
            this.body = body;
        }
    }

    private record LockedOrder(
        UUID orderId,
        UUID checkoutId,
        UUID customerIdentityId,
        UUID kitchenId,
        String kitchenName,
        OrderStatus status,
        String currency,
        BigDecimal grandTotal,
        String rejectionCode,
        Instant acceptanceRequestedAt,
        Instant acceptanceExpiresAt,
        Instant firstReminderRecordedAt,
        Instant secondReminderRecordedAt,
        Instant databaseNow
    ) {
    }

    private record RefundDecision(Instant requestedAt, BigDecimal refundAmount) {
    }
}
