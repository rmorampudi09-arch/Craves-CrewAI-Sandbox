package in.craves.order.service;

import in.craves.order.event.ChefAcceptedOrderEventFactory;
import in.craves.order.event.ChefAcceptedOrderEventSource;
import in.craves.order.event.ChefAcceptedOrderEventSource.DeliveryItemSource;
import in.craves.order.event.SerializedDomainEvent;
import in.craves.order.exception.OrderApiException;
import in.craves.order.outbox.OrderDomainOutboxRepository;
import in.craves.order.security.CravesPrincipal;
import in.craves.order.service.ChefAcceptancePolicy.Decision;
import in.craves.order.web.ApiDtos.ChefAcceptRequest;
import in.craves.order.web.ApiDtos.OrderResponse;
import in.craves.order.web.ApiDtos.OrderStatus;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ChefAcceptanceService {
    private static final String PAYMENT_COLLECTION_MODE_PREPAID = "PREPAID";

    private final JdbcTemplate jdbcTemplate;
    private final OrderService orderService;
    private final ChefAcceptedOrderEventFactory eventFactory;
    private final OrderDomainOutboxRepository outboxRepository;

    public ChefAcceptanceService(
        JdbcTemplate jdbcTemplate,
        OrderService orderService,
        ChefAcceptedOrderEventFactory eventFactory,
        OrderDomainOutboxRepository outboxRepository
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.orderService = orderService;
        this.eventFactory = eventFactory;
        this.outboxRepository = outboxRepository;
    }

    @Transactional
    public OrderResponse accept(
        CravesPrincipal principal,
        UUID orderId,
        ChefAcceptRequest request,
        UUID correlationId,
        String idempotencyKey
    ) {
        if (request == null || request.prepTimeMinutes() == null) {
            throw OrderApiException.badRequest(
                "PREPARATION_TIME_REQUIRED",
                "A positive preparation time is required when accepting an order."
            );
        }

        orderService.getOrderForChef(principal, orderId);

        LockedAcceptanceState lockedState = lockAcceptanceState(orderId);
        Decision decision = ChefAcceptancePolicy.decide(
            lockedState.status(),
            lockedState.prepTimeMinutes(),
            request.prepTimeMinutes()
        );
        if (decision == Decision.IDEMPOTENT_SUCCESS) {
            return orderService.getOrderForChef(principal, orderId);
        }
        validateAcceptanceWindow(lockedState);

        AcceptanceTimes acceptanceTimes = jdbcTemplate.queryForObject(
            """
                UPDATE order_schema.customer_order
                SET status = ?,
                    chef_response_note = ?,
                    prep_time_minutes = ?,
                    accepted_at = now(),
                    ready_at = now() + (? * INTERVAL '1 minute'),
                    updated_at = now()
                WHERE id = ?
                  AND status = ?
                RETURNING accepted_at, ready_at
                """,
            (resultSet, rowNumber) -> new AcceptanceTimes(
                instant(resultSet, "accepted_at"),
                instant(resultSet, "ready_at")
            ),
            OrderStatus.CHEF_ACCEPTED.name(),
            safeReason(request.note()),
            request.prepTimeMinutes(),
            request.prepTimeMinutes(),
            orderId,
            OrderStatus.CHEF_ACCEPTANCE_PENDING.name()
        );

        if (acceptanceTimes == null || acceptanceTimes.acceptedAt() == null || acceptanceTimes.readyAt() == null) {
            throw new IllegalStateException("Chef acceptance timestamps were not persisted");
        }

        jdbcTemplate.update(
            """
                INSERT INTO order_schema.order_status_history (
                    id, order_id, old_status, new_status,
                    actor_identity_id, reason, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, now())
                """,
            UUID.randomUUID(),
            orderId,
            OrderStatus.CHEF_ACCEPTANCE_PENDING.name(),
            OrderStatus.CHEF_ACCEPTED.name(),
            principal.identityId(),
            safeReason(request.note())
        );

        ChefAcceptedOrderEventSource eventSource = loadEventSource(orderId);
        SerializedDomainEvent event = eventFactory.create(eventSource, correlationId, idempotencyKey);
        if (!outboxRepository.insert(orderId, event)) {
            throw OrderApiException.conflict(
                "CHEF_ACCEPTED_EVENT_ALREADY_EXISTS",
                "The chef acceptance event already exists for this order."
            );
        }

        return orderService.getOrderForChef(principal, orderId);
    }

    private void validateAcceptanceWindow(LockedAcceptanceState lockedState) {
        if (lockedState.status() != OrderStatus.CHEF_ACCEPTANCE_PENDING) {
            return;
        }
        if (lockedState.acceptanceExpiresAt() == null) {
            throw OrderApiException.conflict(
                "CHEF_ACCEPTANCE_WINDOW_MISSING",
                "The order does not have a valid chef acceptance deadline."
            );
        }
        if (!lockedState.databaseNow().isBefore(lockedState.acceptanceExpiresAt())) {
            throw OrderApiException.conflict(
                "CHEF_ACCEPTANCE_EXPIRED",
                "The 30-minute chef acceptance window has expired."
            );
        }
    }

    private LockedAcceptanceState lockAcceptanceState(UUID orderId) {
        return jdbcTemplate.query(
            """
                SELECT status, prep_time_minutes, chef_acceptance_expires_at,
                       now() AS database_now
                FROM order_schema.customer_order
                WHERE id = ?
                FOR UPDATE
                """,
            (resultSet, rowNumber) -> new LockedAcceptanceState(
                OrderStatus.valueOf(resultSet.getString("status")),
                integerOrNull(resultSet, "prep_time_minutes"),
                instant(resultSet, "chef_acceptance_expires_at"),
                instant(resultSet, "database_now")
            ),
            orderId
        ).stream().findFirst().orElseThrow(() -> OrderApiException.notFound(
            "ORDER_NOT_FOUND",
            "The requested order was not found."
        ));
    }

    private ChefAcceptedOrderEventSource loadEventSource(UUID orderId) {
        List<DeliveryItemSource> deliveryItems = jdbcTemplate.query(
            """
                SELECT menu_item_id, item_name_snapshot, unit_price_snapshot,
                       quantity, line_total
                FROM order_schema.order_item
                WHERE order_id = ?
                ORDER BY created_at, id
                """,
            (resultSet, rowNumber) -> new DeliveryItemSource(
                resultSet.getObject("menu_item_id", UUID.class),
                resultSet.getString("item_name_snapshot"),
                resultSet.getBigDecimal("unit_price_snapshot"),
                resultSet.getInt("quantity"),
                resultSet.getBigDecimal("line_total")
            ),
            orderId
        );

        return jdbcTemplate.query(
            "SELECT * FROM order_schema.customer_order WHERE id = ?",
            (resultSet, rowNumber) -> mapEventSource(resultSet, deliveryItems),
            orderId
        ).stream().findFirst().orElseThrow(() -> OrderApiException.notFound(
            "ORDER_NOT_FOUND",
            "The requested order was not found."
        ));
    }

    private ChefAcceptedOrderEventSource mapEventSource(
        ResultSet resultSet,
        List<DeliveryItemSource> deliveryItems
    ) throws SQLException {
        Integer totalPackageWeightGrams = integerOrNull(resultSet, "total_package_weight_grams");
        Boolean thermoboxRequired = booleanOrNull(resultSet, "thermobox_required");
        if (totalPackageWeightGrams == null || thermoboxRequired == null) {
            throw OrderApiException.conflict(
                "ORDER_DELIVERY_METADATA_INCOMPLETE",
                "The order does not contain complete delivery package metadata."
            );
        }

        return new ChefAcceptedOrderEventSource(
            resultSet.getObject("id", UUID.class),
            resultSet.getObject("checkout_id", UUID.class),
            instant(resultSet, "accepted_at"),
            instant(resultSet, "ready_at"),
            totalPackageWeightGrams,
            thermoboxRequired,
            resultSet.getObject("kitchen_id", UUID.class),
            resultSet.getString("kitchen_name_snapshot"),
            resultSet.getString("pickup_phone_number"),
            resultSet.getString("pickup_address_line1"),
            resultSet.getString("pickup_address_line2"),
            resultSet.getString("pickup_landmark"),
            resultSet.getString("pickup_area_name"),
            resultSet.getString("pickup_city"),
            resultSet.getString("pickup_state"),
            resultSet.getString("pickup_postal_code"),
            resultSet.getBigDecimal("pickup_latitude"),
            resultSet.getBigDecimal("pickup_longitude"),
            resultSet.getString("dropoff_recipient_name"),
            resultSet.getString("dropoff_contact_phone"),
            resultSet.getString("dropoff_address_line1"),
            resultSet.getString("dropoff_address_line2"),
            resultSet.getString("dropoff_landmark"),
            resultSet.getString("dropoff_area_name"),
            resultSet.getString("dropoff_city"),
            resultSet.getString("dropoff_state"),
            resultSet.getString("dropoff_postal_code"),
            resultSet.getBigDecimal("dropoff_latitude"),
            resultSet.getBigDecimal("dropoff_longitude"),
            List.copyOf(deliveryItems),
            resultSet.getBigDecimal("food_subtotal"),
            PAYMENT_COLLECTION_MODE_PREPAID
        );
    }

    private static String safeReason(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() > 255 ? trimmed.substring(0, 255) : trimmed;
    }

    private static Integer integerOrNull(ResultSet resultSet, String column) throws SQLException {
        int value = resultSet.getInt(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Boolean booleanOrNull(ResultSet resultSet, String column) throws SQLException {
        boolean value = resultSet.getBoolean(column);
        return resultSet.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet resultSet, String column) throws SQLException {
        Timestamp timestamp = resultSet.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record LockedAcceptanceState(
        OrderStatus status,
        Integer prepTimeMinutes,
        Instant acceptanceExpiresAt,
        Instant databaseNow
    ) {
    }

    private record AcceptanceTimes(
        Instant acceptedAt,
        Instant readyAt
    ) {
    }
}
