package in.craves.order.service;

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

@Service
public class ChefAcceptanceInitialNotificationService {
    private final JdbcTemplate jdbcTemplate;
    private final NotificationOutboxRepository notificationOutboxRepository;

    public ChefAcceptanceInitialNotificationService(
        JdbcTemplate jdbcTemplate,
        NotificationOutboxRepository notificationOutboxRepository
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.notificationOutboxRepository = notificationOutboxRepository;
    }

    @Transactional
    public boolean record(UUID orderId, UUID chefIdentityId) {
        if (chefIdentityId == null) {
            return false;
        }
        InitialNotificationOrder order = lockOrder(orderId);
        if (!"CHEF_ACCEPTANCE_PENDING".equals(order.status())
            || order.recordedAt() != null
            || order.expiresAt() == null
            || !order.databaseNow().isBefore(order.expiresAt())) {
            return false;
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("orderId", order.orderId().toString());
        payload.put("checkoutId", order.checkoutId().toString());
        payload.put("kitchenId", order.kitchenId().toString());
        payload.put("kitchenName", order.kitchenName());
        payload.put("grandTotal", order.grandTotal());
        payload.put("currency", order.currency());
        payload.put("acceptanceExpiresAt", order.expiresAt().toString());

        notificationOutboxRepository.savePending(new NotificationOutboxEvent(
            "chef-new-order-" + order.orderId(),
            "CHEF_ORDER_AWAITING_ACCEPTANCE",
            "ORDER",
            order.orderId(),
            chefIdentityId,
            "CHEF",
            "IN_APP",
            "CHEF_ORDER_AWAITING_ACCEPTANCE_IN_APP",
            "New order received",
            "A paid Craves order is waiting for your confirmation.",
            "ORDER",
            order.orderId(),
            payload
        ));

        jdbcTemplate.update(
            "UPDATE order_schema.customer_order SET chef_acceptance_initial_recorded_at = now(), updated_at = now() WHERE id = ?",
            orderId
        );
        return true;
    }

    private InitialNotificationOrder lockOrder(UUID orderId) {
        return jdbcTemplate.query(
            """
                SELECT id, checkout_id, kitchen_id, kitchen_name_snapshot, status,
                       currency, grand_total, chef_acceptance_expires_at,
                       chef_acceptance_initial_recorded_at, now() AS database_now
                FROM order_schema.customer_order
                WHERE id = ?
                FOR UPDATE
                """,
            this::mapOrder,
            orderId
        ).stream().findFirst().orElseThrow(() -> new IllegalArgumentException(
            "Order was not found for initial chef notification"
        ));
    }

    private InitialNotificationOrder mapOrder(ResultSet resultSet, int rowNumber) throws SQLException {
        return new InitialNotificationOrder(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("checkout_id", UUID.class),
            resultSet.getObject("kitchen_id", UUID.class),
            resultSet.getString("kitchen_name_snapshot"),
            resultSet.getString("status"),
            resultSet.getString("currency"),
            resultSet.getBigDecimal("grand_total").toPlainString(),
            instant(resultSet, "chef_acceptance_expires_at"),
            instant(resultSet, "chef_acceptance_initial_recorded_at"),
            instant(resultSet, "database_now")
        );
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record InitialNotificationOrder(
        UUID orderId,
        UUID checkoutId,
        UUID kitchenId,
        String kitchenName,
        String status,
        String currency,
        String grandTotal,
        Instant expiresAt,
        Instant recordedAt,
        Instant databaseNow
    ) {
    }
}
