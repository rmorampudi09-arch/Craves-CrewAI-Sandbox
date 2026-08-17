package in.craves.order.subscription;

import com.fasterxml.jackson.databind.JsonNode;
import in.craves.order.subscription.SubscriptionOrderModels.CallbackRecord;
import in.craves.order.subscription.SubscriptionOrderModels.EventEnvelope;
import in.craves.order.subscription.SubscriptionOrderModels.RequestedData;
import in.craves.order.web.ApiDtos.CustomerAddressSnapshotResponse;
import in.craves.order.web.ApiDtos.KitchenPickupSnapshotResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class SubscriptionOrderRepository {
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    private final JdbcTemplate jdbcTemplate;

    public SubscriptionOrderRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UUID> findOrderByOccurrence(UUID occurrenceId) {
        return jdbcTemplate.query(
            "SELECT id FROM order_schema.customer_order WHERE subscription_occurrence_id = ?",
            (rs, rowNum) -> rs.getObject("id", UUID.class),
            occurrenceId
        ).stream().findFirst();
    }

    @Transactional
    public CreatedOrder create(
        EventEnvelope<RequestedData> event,
        JsonNode rawPayload,
        UUID kitchenId,
        String kitchenName,
        CustomerAddressSnapshotResponse dropoff,
        KitchenPickupSnapshotResponse pickup,
        List<ValidatedItem> items,
        int totalPackageWeightGrams,
        boolean thermoboxRequired
    ) {
        jdbcTemplate.update(
            "INSERT INTO order_schema.subscription_order_request_inbox " +
                "(event_id, event_type, event_version, correlation_id, causation_id, subject, occurrence_id, subscription_id, payload, processing_status, received_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), 'RECEIVED', now()) ON CONFLICT (event_id) DO NOTHING",
            event.eventId(), event.eventType(), event.eventVersion(), event.correlationId(), event.causationId(),
            event.subject(), event.data().occurrenceId(), event.data().subscriptionId(), rawPayload.toString()
        );
        Optional<UUID> existingOrder = findOrderByOccurrence(event.data().occurrenceId());
        if (existingOrder.isPresent()) {
            UUID orderId = existingOrder.get();
            jdbcTemplate.update(
                "UPDATE order_schema.subscription_order_request_inbox SET processing_status = 'DUPLICATE', order_id = ?, processed_at = now() WHERE event_id = ?",
                orderId, event.eventId()
            );
            ensureCallback(event.data().occurrenceId(), orderId);
            return new CreatedOrder(orderId, false);
        }

        UUID orderId = UUID.randomUUID();
        jdbcTemplate.update(
            """
            INSERT INTO order_schema.customer_order (
                id, checkout_id, customer_identity_id, kitchen_id, kitchen_name_snapshot,
                status, currency, food_subtotal, platform_fee, tax_amount, delivery_fee, grand_total,
                total_package_weight_grams, thermobox_required,
                delivery_address_id, dropoff_recipient_name, dropoff_contact_phone,
                dropoff_address_line1, dropoff_address_line2, dropoff_landmark, dropoff_area_name,
                dropoff_city, dropoff_state, dropoff_postal_code, dropoff_latitude, dropoff_longitude,
                pickup_phone_number, pickup_email, pickup_address_line1, pickup_address_line2,
                pickup_landmark, pickup_area_name, pickup_city, pickup_state, pickup_postal_code,
                pickup_latitude, pickup_longitude,
                order_source, subscription_occurrence_id, subscription_id, scheduled_service_at,
                financial_allocation_status, created_at, updated_at
            ) VALUES (
                ?, NULL, ?, ?, ?, 'CHEF_ACCEPTANCE_PENDING', 'INR', ?, ?, ?, ?, ?, ?, ?,
                ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?,
                'SUBSCRIPTION', ?, ?, ?, 'PENDING_POLICY', now(), now()
            )
            """,
            orderId,
            event.data().customerIdentityId(),
            kitchenId,
            kitchenName,
            ZERO, ZERO, ZERO, ZERO, ZERO,
            totalPackageWeightGrams,
            thermoboxRequired,
            dropoff.sourceAddressId(),
            dropoff.recipientName(),
            dropoff.contactPhoneNumber(),
            dropoff.addressLine1(),
            dropoff.addressLine2(),
            dropoff.landmark(),
            dropoff.areaName(),
            dropoff.city(),
            dropoff.state(),
            dropoff.postalCode(),
            dropoff.latitude(),
            dropoff.longitude(),
            pickup.contactPhoneNumber(),
            pickup.email(),
            pickup.addressLine1(),
            pickup.addressLine2(),
            pickup.landmark(),
            pickup.areaName(),
            pickup.city(),
            pickup.state(),
            pickup.postalCode(),
            pickup.latitude(),
            pickup.longitude(),
            event.data().occurrenceId(),
            event.data().subscriptionId(),
            event.data().scheduledServiceAt()
        );

        for (ValidatedItem item : items) {
            jdbcTemplate.update(
                "INSERT INTO order_schema.order_item " +
                    "(id, order_id, menu_item_id, item_name_snapshot, category_snapshot, food_type_snapshot, " +
                    "unit_price_snapshot, unit_package_weight_grams_snapshot, thermobox_required_snapshot, quantity, line_total, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now())",
                UUID.randomUUID(), orderId, item.menuItemId(), item.itemName(), item.category(), item.foodType(),
                ZERO, item.unitPackageWeightGrams(), item.thermoboxRequired(), item.quantity(), ZERO
            );
        }

        jdbcTemplate.update(
            "INSERT INTO order_schema.order_status_history " +
                "(id, order_id, old_status, new_status, actor_identity_id, reason, created_at) " +
                "VALUES (?, ?, NULL, 'CHEF_ACCEPTANCE_PENDING', NULL, 'Paid subscription occurrence created', now())",
            UUID.randomUUID(), orderId
        );
        jdbcTemplate.update(
            "UPDATE order_schema.subscription_order_request_inbox SET processing_status = 'PROCESSED', order_id = ?, processed_at = now() WHERE event_id = ?",
            orderId, event.eventId()
        );
        ensureCallback(event.data().occurrenceId(), orderId);
        return new CreatedOrder(orderId, true);
    }

    @Transactional
    public List<CallbackRecord> claimCallbacks(int batchSize, int maxAttempts, int staleMinutes) {
        UUID lockToken = UUID.randomUUID();
        String sql = """
            WITH candidates AS (
                SELECT id
                  FROM order_schema.subscription_order_callback_outbox
                 WHERE (status IN ('PENDING', 'FAILED') AND next_attempt_at <= now() AND attempt_count < ?)
                    OR (status = 'PROCESSING' AND locked_at < now() - (? * INTERVAL '1 minute'))
                 ORDER BY created_at
                 FOR UPDATE SKIP LOCKED
                 LIMIT ?
            )
            UPDATE order_schema.subscription_order_callback_outbox callback
               SET status = 'PROCESSING', lock_token = ?, locked_at = now(),
                   attempt_count = attempt_count + 1, last_error = NULL, updated_at = now()
              FROM candidates candidate
             WHERE callback.id = candidate.id
            RETURNING callback.id, callback.occurrence_id, callback.order_id, callback.attempt_count
            """;
        return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new CallbackRecord(
                rs.getObject("id", UUID.class),
                rs.getObject("occurrence_id", UUID.class),
                rs.getObject("order_id", UUID.class),
                rs.getInt("attempt_count"),
                lockToken
            ),
            maxAttempts, staleMinutes, batchSize, lockToken
        );
    }

    public void markCallbackDelivered(CallbackRecord record) {
        jdbcTemplate.update(
            "UPDATE order_schema.subscription_order_callback_outbox SET status = 'DELIVERED', delivered_at = now(), " +
                "lock_token = NULL, locked_at = NULL, updated_at = now() WHERE id = ? AND lock_token = ?",
            record.id(), record.lockToken()
        );
    }

    public void markCallbackFailure(CallbackRecord record, int maxAttempts, Throwable error) {
        boolean dead = record.attemptCount() >= maxAttempts;
        long delay = Math.min(3600L, 5L * (1L << Math.min(10, Math.max(0, record.attemptCount() - 1))));
        jdbcTemplate.update(
            "UPDATE order_schema.subscription_order_callback_outbox SET status = ?, " +
                "next_attempt_at = now() + (? * INTERVAL '1 second'), last_error = ?, " +
                "lock_token = NULL, locked_at = NULL, updated_at = now() WHERE id = ? AND lock_token = ?",
            dead ? "DEAD_LETTER" : "FAILED",
            dead ? 0L : delay,
            safe(error),
            record.id(), record.lockToken()
        );
    }

    private void ensureCallback(UUID occurrenceId, UUID orderId) {
        jdbcTemplate.update(
            "INSERT INTO order_schema.subscription_order_callback_outbox " +
                "(id, occurrence_id, order_id, status, created_at, updated_at) VALUES (?, ?, ?, 'PENDING', now(), now()) " +
                "ON CONFLICT (occurrence_id) DO NOTHING",
            UUID.randomUUID(), occurrenceId, orderId
        );
    }

    private static String safe(Throwable error) {
        String value = error == null || error.getMessage() == null
            ? (error == null ? "Unknown callback failure" : error.getClass().getSimpleName())
            : error.getMessage();
        value = value.replace('\n', ' ').replace('\r', ' ').trim();
        return value.length() > 1000 ? value.substring(0, 1000) : value;
    }

    public record ValidatedItem(
        UUID menuItemId,
        String itemName,
        String category,
        String foodType,
        int unitPackageWeightGrams,
        boolean thermoboxRequired,
        int quantity
    ) {
    }

    public record CreatedOrder(UUID orderId, boolean created) {
    }
}
