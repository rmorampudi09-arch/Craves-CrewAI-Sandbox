package in.craves.integration.admin;

import in.craves.integration.security.CravesPrincipal;
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
@RequestMapping("/api/v1/admin/operations")
public class AdminIntegrationInvestigationController {
    private final JdbcTemplate jdbcTemplate;

    public AdminIntegrationInvestigationController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/payments/{paymentOrderId}")
    public ResponseEntity<PaymentInvestigationResponse> payment(Authentication authentication, @PathVariable UUID paymentOrderId,
        @RequestHeader("X-Admin-Reason") String reason,
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationHeader) {
        AuditContext audit = audit(authentication, "PAYMENT", paymentOrderId, reason, correlationHeader);
        PaymentSnapshot payment = jdbcTemplate.query(
            """
            SELECT id, checkout_id, customer_identity_id, craves_payment_order_ref,
                   cashfree_order_id, cashfree_cf_order_id, amount, currency,
                   status, provider_status, created_at, updated_at
              FROM payment_schema.payment_order
             WHERE id = ?
            """,
            (rs, rowNum) -> new PaymentSnapshot(
                rs.getObject("id", UUID.class), rs.getObject("checkout_id", UUID.class),
                rs.getObject("customer_identity_id", UUID.class), rs.getString("craves_payment_order_ref"),
                rs.getString("cashfree_order_id"), rs.getString("cashfree_cf_order_id"),
                rs.getBigDecimal("amount"), rs.getString("currency"), rs.getString("status"),
                rs.getString("provider_status"), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
            ), paymentOrderId
        ).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment order was not found"));

        List<PaymentAttemptSnapshot> attempts = jdbcTemplate.query(
            """
            SELECT cf_payment_id, payment_status, payment_amount, payment_currency, created_at
              FROM payment_schema.payment_attempt
             WHERE payment_order_id = ?
             ORDER BY created_at, id
            """,
            (rs, rowNum) -> new PaymentAttemptSnapshot(
                rs.getString("cf_payment_id"), rs.getString("payment_status"),
                rs.getBigDecimal("payment_amount"), rs.getString("payment_currency"),
                rs.getObject("created_at", OffsetDateTime.class)
            ), paymentOrderId
        );

        List<PaymentEventSnapshot> events = jdbcTemplate.query(
            """
            SELECT provider_event_id, event_type, payment_status, created_at
              FROM payment_schema.payment_event
             WHERE payment_order_id = ?
             ORDER BY created_at, id
            """,
            (rs, rowNum) -> new PaymentEventSnapshot(
                rs.getString("provider_event_id"), rs.getString("event_type"),
                rs.getString("payment_status"), rs.getObject("created_at", OffsetDateTime.class)
            ), paymentOrderId
        );
        persistAudit(audit);
        return response(audit.correlationId(), new PaymentInvestigationResponse(payment, attempts, events));
    }

    @GetMapping("/refunds/{refundId}")
    public ResponseEntity<RefundInvestigationResponse> refund(Authentication authentication, @PathVariable UUID refundId,
        @RequestHeader("X-Admin-Reason") String reason,
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationHeader) {
        AuditContext audit = audit(authentication, "REFUND", refundId, reason, correlationHeader);
        RefundSnapshot refund = jdbcTemplate.query(
            """
            SELECT id, payment_order_id, checkout_id, chef_sub_order_id, customer_identity_id,
                   request_event_id, cashfree_order_id, refund_ref, idempotency_key,
                   amount, currency, reason, status, provider_status, cf_refund_id,
                   attempt_count, next_attempt_at, processed_at, last_error, created_at, updated_at
              FROM payment_schema.refund
             WHERE id = ?
            """,
            (rs, rowNum) -> new RefundSnapshot(
                rs.getObject("id", UUID.class), rs.getObject("payment_order_id", UUID.class),
                rs.getObject("checkout_id", UUID.class), rs.getObject("chef_sub_order_id", UUID.class),
                rs.getObject("customer_identity_id", UUID.class), rs.getObject("request_event_id", UUID.class),
                rs.getString("cashfree_order_id"), rs.getString("refund_ref"),
                rs.getObject("idempotency_key", UUID.class), rs.getBigDecimal("amount"),
                rs.getString("currency"), rs.getString("reason"), rs.getString("status"),
                rs.getString("provider_status"), rs.getString("cf_refund_id"), rs.getInt("attempt_count"),
                rs.getObject("next_attempt_at", OffsetDateTime.class), rs.getObject("processed_at", OffsetDateTime.class),
                safeError(rs.getString("last_error")), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
            ), refundId
        ).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Refund was not found"));

        List<OutboxSnapshot> statusEvents = jdbcTemplate.query(
            """
            SELECT id, event_type, event_version, status, attempt_count,
                   next_attempt_at, published_at, broker_message_id, last_error, created_at, updated_at
              FROM payment_schema.refund_status_outbox
             WHERE aggregate_id = ?
             ORDER BY created_at, id
            """,
            (rs, rowNum) -> new OutboxSnapshot(
                rs.getObject("id", UUID.class), rs.getString("event_type"), rs.getString("event_version"),
                rs.getString("status"), rs.getInt("attempt_count"), rs.getObject("next_attempt_at", OffsetDateTime.class),
                rs.getObject("published_at", OffsetDateTime.class), rs.getString("broker_message_id"),
                safeError(rs.getString("last_error")), rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class)
            ), refundId
        );
        persistAudit(audit);
        return response(audit.correlationId(), new RefundInvestigationResponse(refund, statusEvents));
    }

    @GetMapping("/delivery-commands/{commandId}")
    public ResponseEntity<DeliveryInvestigationResponse> delivery(Authentication authentication, @PathVariable UUID commandId,
        @RequestHeader("X-Admin-Reason") String reason,
        @RequestHeader(value = "X-Correlation-ID", required = false) String correlationHeader) {
        AuditContext audit = audit(authentication, "DELIVERY_COMMAND", commandId, reason, correlationHeader);
        DeliveryCommandSnapshot command = jdbcTemplate.query(
            """
            SELECT id, chef_sub_order_id, order_id, status, ready_at, dispatch_at,
                   idempotency_key, source_event_id, attempt_count, scheduled_sequence_number,
                   service_bus_message_id, reconciliation_provider_id,
                   reconciliation_client_reference, reconciliation_started_at,
                   reconciliation_attempt_count, next_reconciliation_at, last_error,
                   created_at, updated_at
              FROM delivery_schema.delivery_command
             WHERE id = ?
            """,
            (rs, rowNum) -> new DeliveryCommandSnapshot(
                rs.getObject("id", UUID.class), rs.getObject("chef_sub_order_id", UUID.class),
                rs.getObject("order_id", UUID.class), rs.getString("status"),
                rs.getObject("ready_at", OffsetDateTime.class), rs.getObject("dispatch_at", OffsetDateTime.class),
                rs.getObject("idempotency_key", UUID.class), rs.getObject("source_event_id", UUID.class),
                rs.getInt("attempt_count"), rs.getObject("scheduled_sequence_number", Long.class),
                rs.getString("service_bus_message_id"), rs.getString("reconciliation_provider_id"),
                rs.getString("reconciliation_client_reference"),
                rs.getObject("reconciliation_started_at", OffsetDateTime.class),
                rs.getInt("reconciliation_attempt_count"),
                rs.getObject("next_reconciliation_at", OffsetDateTime.class), safeError(rs.getString("last_error")),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class)
            ), commandId
        ).stream().findFirst().orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery command was not found"));

        DeliveryJobSnapshot job = jdbcTemplate.query(
            """
            SELECT id, assignment_id, provider_id, provider_delivery_id, status,
                   provider_status, booked_at, last_status_observed_at, last_status_source,
                   next_tracking_at, created_at, updated_at
              FROM delivery_schema.delivery_job
             WHERE chef_sub_order_id = ?
            """,
            (rs, rowNum) -> new DeliveryJobSnapshot(
                rs.getObject("id", UUID.class), rs.getObject("assignment_id", UUID.class),
                rs.getString("provider_id"), rs.getString("provider_delivery_id"), rs.getString("status"),
                rs.getString("provider_status"), rs.getObject("booked_at", OffsetDateTime.class),
                rs.getObject("last_status_observed_at", OffsetDateTime.class), rs.getString("last_status_source"),
                rs.getObject("next_tracking_at", OffsetDateTime.class),
                rs.getObject("created_at", OffsetDateTime.class), rs.getObject("updated_at", OffsetDateTime.class)
            ), command.chefSubOrderId()
        ).stream().findFirst().orElse(null);
        persistAudit(audit);
        return response(audit.correlationId(), new DeliveryInvestigationResponse(command, job));
    }

    private AuditContext audit(Authentication authentication, String resourceType, UUID resourceId,
        String reason, String correlationHeader) {
        CravesPrincipal principal = requireInvestigationAccess(authentication, resourceType);
        return new AuditContext(principal.identityId(), resourceType, resourceId,
            validateReason(reason), correlationId(correlationHeader));
    }

    private void persistAudit(AuditContext audit) {
        jdbcTemplate.update(
            "INSERT INTO payment_schema.admin_investigation_audit " +
                "(id, actor_identity_id, resource_type, resource_id, action, reason, correlation_id, created_at) " +
                "VALUES (?, ?, ?, ?, 'INVESTIGATE', ?, ?, now())",
            UUID.randomUUID(), audit.actorIdentityId(), audit.resourceType(), audit.resourceId(),
            audit.reason(), audit.correlationId()
        );
    }

    private static <T> ResponseEntity<T> response(UUID correlationId, T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .header("X-Correlation-ID", correlationId.toString()).body(body);
    }

    private static CravesPrincipal requireInvestigationAccess(Authentication authentication, String resourceType) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CravesPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Craves access token is required");
        }
        boolean allowed = switch (resourceType) {
            case "PAYMENT", "REFUND" -> principal.hasAnyRole(
                "PLATFORM_ADMIN", "SUPPORT_ADMIN", "PAYMENTS_ADMIN", "AUDIT_ADMIN");
            case "DELIVERY_COMMAND" -> principal.hasAnyRole(
                "PLATFORM_ADMIN", "SUPPORT_ADMIN", "OPERATIONS_ADMIN", "AUDIT_ADMIN");
            default -> false;
        };
        if (!allowed) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Investigation role is required");
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
        if (value == null || value.isBlank()) return UUID.randomUUID();
        try { return UUID.fromString(value.trim()); }
        catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-Correlation-ID must be a UUID");
        }
    }

    private static String safeError(String value) {
        if (value == null || value.isBlank()) return null;
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() > 500 ? normalized.substring(0, 500) : normalized;
    }

    private record AuditContext(UUID actorIdentityId, String resourceType, UUID resourceId, String reason, UUID correlationId) {}
    public record PaymentInvestigationResponse(PaymentSnapshot payment, List<PaymentAttemptSnapshot> attempts, List<PaymentEventSnapshot> events) {}
    public record PaymentSnapshot(UUID paymentOrderId, UUID checkoutId, UUID customerIdentityId, String cravesReference,
        String cashfreeOrderId, String cashfreeCfOrderId, BigDecimal amount, String currency,
        String status, String providerStatus, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
    public record PaymentAttemptSnapshot(String cashfreePaymentId, String paymentStatus, BigDecimal amount, String currency, OffsetDateTime createdAt) {}
    public record PaymentEventSnapshot(String providerEventId, String eventType, String paymentStatus, OffsetDateTime createdAt) {}
    public record RefundInvestigationResponse(RefundSnapshot refund, List<OutboxSnapshot> statusEvents) {}
    public record RefundSnapshot(UUID refundId, UUID paymentOrderId, UUID checkoutId, UUID chefSubOrderId, UUID customerIdentityId,
        UUID requestEventId, String cashfreeOrderId, String refundReference, UUID idempotencyKey,
        BigDecimal amount, String currency, String reason, String status, String providerStatus,
        String cashfreeRefundId, int attemptCount, OffsetDateTime nextAttemptAt, OffsetDateTime processedAt,
        String lastError, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
    public record OutboxSnapshot(UUID eventId, String eventType, String eventVersion, String status, int attemptCount,
        OffsetDateTime nextAttemptAt, OffsetDateTime publishedAt, String brokerMessageId,
        String lastError, OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
    public record DeliveryInvestigationResponse(DeliveryCommandSnapshot command, DeliveryJobSnapshot job) {}
    public record DeliveryCommandSnapshot(UUID commandId, UUID chefSubOrderId, UUID orderId, String status,
        OffsetDateTime readyAt, OffsetDateTime dispatchAt, UUID idempotencyKey, UUID sourceEventId,
        int attemptCount, Long scheduledSequenceNumber, String serviceBusMessageId,
        String reconciliationProviderId, String reconciliationClientReference,
        OffsetDateTime reconciliationStartedAt, int reconciliationAttemptCount,
        OffsetDateTime nextReconciliationAt, String lastError,
        OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
    public record DeliveryJobSnapshot(UUID deliveryJobId, UUID assignmentId, String providerId, String providerDeliveryId,
        String status, String providerStatus, OffsetDateTime bookedAt,
        OffsetDateTime lastStatusObservedAt, String lastStatusSource, OffsetDateTime nextTrackingAt,
        OffsetDateTime createdAt, OffsetDateTime updatedAt) {}
}
