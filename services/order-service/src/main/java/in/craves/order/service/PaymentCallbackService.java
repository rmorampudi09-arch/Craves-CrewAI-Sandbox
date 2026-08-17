package in.craves.order.service;

import in.craves.order.config.ChefAcceptanceWindowProperties;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class PaymentCallbackService {
    private final JdbcTemplate jdbcTemplate;
    private final NotificationInternalClient notificationInternalClient;
    private final ChefAcceptanceWindowProperties chefAcceptanceProperties;

    public PaymentCallbackService(
        JdbcTemplate jdbcTemplate,
        NotificationInternalClient notificationInternalClient,
        ChefAcceptanceWindowProperties chefAcceptanceProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.notificationInternalClient = notificationInternalClient;
        this.chefAcceptanceProperties = chefAcceptanceProperties;
    }

    @Transactional
    public void markCheckoutPaid(UUID checkoutId, UUID actorId, String reason) {
        int checkoutUpdated = jdbcTemplate.update(
            "UPDATE order_schema.checkout SET status = ?, updated_at = now() WHERE id = ? AND status IN (?, ?)",
            "PAID", checkoutId, "PAYMENT_PENDING", "CREATED"
        );
        int timeoutMinutes = chefAcceptanceProperties.validatedTimeoutMinutes();
        jdbcTemplate.query(
            "SELECT id, status FROM order_schema.customer_order WHERE checkout_id = ?",
            resultSet -> {
                UUID orderId = resultSet.getObject("id", UUID.class);
                String oldStatus = resultSet.getString("status");
                if ("PAYMENT_PENDING".equals(oldStatus) || "CREATED".equals(oldStatus) || "PAID".equals(oldStatus)) {
                    int updated = jdbcTemplate.update(
                        """
                            UPDATE order_schema.customer_order
                            SET status = 'CHEF_ACCEPTANCE_PENDING',
                                chef_acceptance_requested_at = now(),
                                chef_acceptance_expires_at = now() + (? * INTERVAL '1 minute'),
                                chef_acceptance_initial_recorded_at = NULL,
                                chef_acceptance_reminder_10_recorded_at = NULL,
                                chef_acceptance_reminder_20_recorded_at = NULL,
                                chef_rejection_code = NULL,
                                refund_requested_at = NULL,
                                refund_requested_amount = NULL,
                                updated_at = now()
                            WHERE id = ?
                              AND status = ?
                            """,
                        timeoutMinutes,
                        orderId,
                        oldStatus
                    );
                    if (updated == 1) {
                        jdbcTemplate.update(
                            "INSERT INTO order_schema.order_status_history (id, order_id, old_status, new_status, actor_identity_id, reason, created_at) VALUES (?, ?, ?, ?, ?, ?, now())",
                            UUID.randomUUID(), orderId, oldStatus, "CHEF_ACCEPTANCE_PENDING", actorId, reason
                        );
                    }
                }
            },
            checkoutId
        );
        if (checkoutUpdated > 0) {
            loadPaymentNotification(checkoutId).ifPresent(this::notifyPaymentSucceededAfterCommit);
        }
    }

    private Optional<PaymentNotification> loadPaymentNotification(UUID checkoutId) {
        return jdbcTemplate.query(
            "SELECT c.id, c.customer_identity_id, c.currency, c.grand_total, COUNT(o.id) AS order_count " +
                "FROM order_schema.checkout c " +
                "LEFT JOIN order_schema.customer_order o ON o.checkout_id = c.id " +
                "WHERE c.id = ? " +
                "GROUP BY c.id, c.customer_identity_id, c.currency, c.grand_total",
            (resultSet, rowNumber) -> mapPaymentNotification(resultSet),
            checkoutId
        ).stream().findFirst();
    }

    private PaymentNotification mapPaymentNotification(ResultSet resultSet) throws SQLException {
        return new PaymentNotification(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("customer_identity_id", UUID.class),
            resultSet.getString("currency"),
            resultSet.getBigDecimal("grand_total"),
            resultSet.getInt("order_count")
        );
    }

    private void notifyPaymentSucceededAfterCommit(PaymentNotification notification) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatchPaymentSucceeded(notification);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                dispatchPaymentSucceeded(notification);
            }
        });
    }

    private void dispatchPaymentSucceeded(PaymentNotification notification) {
        notificationInternalClient.paymentSucceeded(
            notification.checkoutId(),
            notification.customerIdentityId(),
            notification.currency(),
            notification.grandTotal(),
            notification.orderCount()
        );
    }

    private record PaymentNotification(
        UUID checkoutId,
        UUID customerIdentityId,
        String currency,
        BigDecimal grandTotal,
        int orderCount
    ) {
    }
}
