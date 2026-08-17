package in.craves.order.admin;

import in.craves.order.security.CravesPrincipal;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/operations/orders")
public class AdminOrderInvestigationController {
    private final JdbcTemplate jdbcTemplate;

    public AdminOrderInvestigationController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/{orderId}")
    public ResponseEntity<OrderInvestigationResponse> investigate(
        Authentication authentication,
        @PathVariable UUID orderId,
        @RequestHeader("X-Admin-Reason") String reason,
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationHeader
    ) {
        CravesPrincipal principal = requireAdmin(authentication);
        String normalizedReason = validateReason(reason);
        UUID correlationId = correlationId(correlationHeader);

        OrderSnapshot order = jdbcTemplate.query(
            """
            SELECT id, checkout_id, customer_identity_id, kitchen_id, kitchen_name_snapshot,
                   status, currency, food_subtotal, platform_fee, tax_amount, delivery_fee, grand_total,
                   order_source, subscription_occurrence_id, subscription_id, scheduled_service_at,
                   financial_allocation_status, delivery_address_id, dropoff_recipient_name,
                   dropoff_contact_phone, dropoff_area_name, dropoff_city, dropoff_state,
                   dropoff_postal_code, delivery_job_id, delivery_provider_id,
                   delivery_provider_delivery_id, delivery_status, delivery_status_observed_at,
                   refund_id, refund_reference, refund_provider_status, cf_refund_id,
                   refund_status_updated_at, created_at, updated_at
              FROM order_schema.customer_order
             WHERE id = ?
            """,
            (rs, rowNum) -> new OrderSnapshot(
                rs.getObject("id", UUID.class), rs.getObject("checkout_id", UUID.class),
                rs.getObject("customer_identity_id", UUID.class), rs.getObject("kitchen_id", UUID.class),
                rs.getString("kitchen_name_snapshot"), rs.getString("status"), rs.getString("currency"),
                rs.getBigDecimal("food_subtotal"), rs.getBigDecimal("platform_fee"),
                rs.getBigDecimal("tax_amount"), rs.getBigDecimal("delivery_fee"),
                rs.getBigDecimal("grand_total"), rs.getString("order_source"),
                rs.getObject("subscription_occurrence_id", UUID.class), rs.getObject("subscription_id", UUID.class),
                rs.getObject("scheduled_service_at", OffsetDateTime.class), rs.getString("financial_allocation_status"),
                rs.getObject("delivery_address_id", UUID.class), rs.getString("dropoff_recipient_name"),
                maskPhone(rs.getString("dropoff_contact_phone")), rs.getString("dropoff_area_name"),
                rs.getString("dropoff_city"), rs.getString("dropoff_state"), rs.getString("dropoff_postal_code"),
                rs.getObject("delivery_job_id", UUID.class), rs.getString("delivery_provider_id"),
                rs.getString("delivery_provider_delivery_id"), rs.getString("delivery_status"),
                rs.getObject("delivery_status_observed_at", OffsetDateTime.class),
                rs.getObject("refund_id", UUID.class), rs.getString("refund_reference"),
                rs.getString("refund_provider_status"), rs.getString("cf_refund_id"),
                rs.getObject("refund_status_updated_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class)
            ), orderId
        ).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Order was not found"));

        List<OrderItemSnapshot> items = jdbcTemplate.query(
            """
            SELECT menu_item_id, item_name_snapshot, category_snapshot, food_type_snapshot,
                   unit_price_snapshot, quantity, line_total
              FROM order_schema.order_item
             WHERE order_id = ?
             ORDER BY created_at, id
            """,
            (rs, rowNum) -> new OrderItemSnapshot(
                rs.getObject("menu_item_id", UUID.class), rs.getString("item_name_snapshot"),
                rs.getString("category_snapshot"), rs.getString("food_type_snapshot"),
                rs.getBigDecimal("unit_price_snapshot"), rs.getInt("quantity"), rs.getBigDecimal("line_total")
            ), orderId
        );

        List<StatusHistoryEntry> statusHistory = jdbcTemplate.query(
            """
            SELECT old_status, new_status, actor_identity_id, reason, created_at
              FROM order_schema.order_status_history
             WHERE order_id = ?
             ORDER BY created_at, id
            """,
            (rs, rowNum) -> new StatusHistoryEntry(
                rs.getString("old_status"), rs.getString("new_status"),
                rs.getObject("actor_identity_id", UUID.class), rs.getString("reason"),
                rs.getObject("created_at", OffsetDateTime.class)
            ), orderId
        );

        List<DeliveryHistoryEntry> deliveryHistory = jdbcTemplate.query(
            """
            SELECT event_id, delivery_job_id, old_status, new_status, provider_id,
                   provider_delivery_id, observed_at, source, created_at
              FROM order_schema.order_delivery_status_history
             WHERE order_id = ?
             ORDER BY observed_at, created_at
            """,
            (rs, rowNum) -> new DeliveryHistoryEntry(
                rs.getObject("event_id", UUID.class), rs.getObject("delivery_job_id", UUID.class),
                rs.getString("old_status"), rs.getString("new_status"), rs.getString("provider_id"),
                rs.getString("provider_delivery_id"), rs.getObject("observed_at", OffsetDateTime.class),
                rs.getString("source"), rs.getObject("created_at", OffsetDateTime.class)
            ), orderId
        );

        List<RefundInboxEntry> refundEvents = jdbcTemplate.query(
            """
            SELECT event_id, refund_id, normalized_status, provider_status,
                   processing_status, received_at, processed_at
              FROM order_schema.refund_status_inbox
             WHERE subject = ?
             ORDER BY received_at, event_id
            """,
            (rs, rowNum) -> new RefundInboxEntry(
                rs.getObject("event_id", UUID.class), rs.getObject("refund_id", UUID.class),
                rs.getString("normalized_status"), rs.getString("provider_status"),
                rs.getString("processing_status"), rs.getObject("received_at", OffsetDateTime.class),
                rs.getObject("processed_at", OffsetDateTime.class)
            ), orderId
        );

        jdbcTemplate.update(
            "INSERT INTO order_schema.admin_investigation_audit " +
                "(id, actor_identity_id, resource_type, resource_id, action, reason, correlation_id, created_at) " +
                "VALUES (?, ?, 'ORDER', ?, 'INVESTIGATE', ?, ?, now())",
            UUID.randomUUID(), principal.identityId(), orderId, normalizedReason, correlationId
        );

        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .header("X-Correlation-ID", correlationId.toString())
            .body(new OrderInvestigationResponse(order, items, statusHistory, deliveryHistory, refundEvents));
    }

    private static CravesPrincipal requireAdmin(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CravesPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Craves access token is required");
        }
        if (!principal.hasAnyRole(
            "PLATFORM_ADMIN", "SUPPORT_ADMIN", "PAYMENTS_ADMIN", "OPERATIONS_ADMIN", "AUDIT_ADMIN"
        )) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Order investigation access is required");
        }
        return principal;
    }

    private static String validateReason(String value) {
        String normalized = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
        if (normalized.length() < 10 || normalized.length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Admin-Reason must contain 10 to 500 characters");
        }
        return normalized;
    }

    private static UUID correlationId(String value) {
        if (value == null || value.isBlank()) {
            return UUID.randomUUID();
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Correlation-ID must be a UUID");
        }
    }

    private static String maskPhone(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.replaceAll("\\s", "");
        int visible = Math.min(4, normalized.length());
        return "*".repeat(Math.max(0, normalized.length() - visible)) + normalized.substring(normalized.length() - visible);
    }

    public record OrderInvestigationResponse(
        OrderSnapshot order, List<OrderItemSnapshot> items, List<StatusHistoryEntry> statusHistory,
        List<DeliveryHistoryEntry> deliveryHistory, List<RefundInboxEntry> refundEvents
    ) {}
    public record OrderSnapshot(
        UUID orderId, UUID checkoutId, UUID customerIdentityId, UUID kitchenId, String kitchenName,
        String status, String currency, BigDecimal foodSubtotal, BigDecimal platformFee,
        BigDecimal taxAmount, BigDecimal deliveryFee, BigDecimal grandTotal, String orderSource,
        UUID subscriptionOccurrenceId, UUID subscriptionId, OffsetDateTime scheduledServiceAt,
        String financialAllocationStatus, UUID deliveryAddressId, String recipientName,
        String maskedRecipientPhone, String areaName, String city, String state, String postalCode,
        UUID deliveryJobId, String deliveryProviderId, String providerDeliveryId, String deliveryStatus,
        OffsetDateTime deliveryStatusObservedAt, UUID refundId, String refundReference,
        String refundProviderStatus, String cashfreeRefundId, OffsetDateTime refundStatusUpdatedAt,
        OffsetDateTime createdAt, OffsetDateTime updatedAt
    ) {}
    public record OrderItemSnapshot(
        UUID menuItemId, String itemName, String category, String foodType,
        BigDecimal unitPrice, int quantity, BigDecimal lineTotal
    ) {}
    public record StatusHistoryEntry(
        String oldStatus, String newStatus, UUID actorIdentityId, String reason, OffsetDateTime createdAt
    ) {}
    public record DeliveryHistoryEntry(
        UUID eventId, UUID deliveryJobId, String oldStatus, String newStatus, String providerId,
        String providerDeliveryId, OffsetDateTime observedAt, String source, OffsetDateTime createdAt
    ) {}
    public record RefundInboxEntry(
        UUID eventId, UUID refundId, String normalizedStatus, String providerStatus,
        String processingStatus, OffsetDateTime receivedAt, OffsetDateTime processedAt
    ) {}
}
