package in.craves.subscription.payment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.subscription.capacity.CapacityService;
import in.craves.subscription.repository.SubscriptionRepository;
import in.craves.subscription.web.ApiDtos.SubscriptionResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SubscriptionPaymentStatusService {
    private static final String EVENT_TYPE = "SUBSCRIPTION_PAYMENT_STATUS_CHANGED";
    private static final Set<String> STATUSES = Set.of("PAYMENT_PENDING", "PAID", "FAILED", "CANCELLED");

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final CapacityService capacityService;
    private final SubscriptionRepository subscriptionRepository;

    public SubscriptionPaymentStatusService(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper,
        CapacityService capacityService,
        SubscriptionRepository subscriptionRepository
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.capacityService = capacityService;
        this.subscriptionRepository = subscriptionRepository;
    }

    @Transactional
    public boolean accept(String rawPayload) {
        JsonNode event = parse(rawPayload);
        UUID eventId = uuid(event, "/eventId");
        String eventType = text(event, "/eventType");
        String eventVersion = text(event, "/eventVersion");
        UUID correlationId = uuid(event, "/correlationId");
        UUID causationId = optionalUuid(event, "/causationId");
        UUID subject = uuid(event, "/subject");
        JsonNode data = event.path("data");
        UUID invoiceId = uuid(data, "/invoiceId");
        UUID subscriptionId = uuid(data, "/subscriptionId");
        UUID paymentIntentId = uuid(data, "/paymentIntentId");
        String status = normalize(text(data, "/status"));
        String providerStatus = text(data, "/providerStatus");
        String providerPaymentId = text(data, "/providerPaymentId");
        BigDecimal amount = decimal(data, "/amount");
        String currency = text(data, "/currency");

        if (!EVENT_TYPE.equals(eventType) || !"v1".equals(eventVersion) || !STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported subscription payment status event");
        }

        int inserted = jdbcTemplate.update(
            "INSERT INTO subscription_schema.subscription_payment_status_inbox " +
                "(event_id, event_type, event_version, correlation_id, causation_id, subject, invoice_id, subscription_id, payload, processing_status, received_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), 'RECEIVED', now()) ON CONFLICT (event_id) DO NOTHING",
            eventId, eventType, eventVersion, correlationId, causationId, subject,
            invoiceId, subscriptionId, event.toString()
        );
        if (inserted == 0) {
            return false;
        }

        Invoice invoice = findInvoice(invoiceId, subscriptionId);
        if (amount == null || amount.compareTo(invoice.amount()) != 0
            || !invoice.currency().equalsIgnoreCase(currency)) {
            reject(eventId, "Payment amount or currency does not match invoice");
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Subscription payment does not match invoice");
        }
        if ("PAID".equals(invoice.status()) && !"PAID".equals(status)) {
            complete(eventId, "PROCESSED", null);
            return false;
        }

        String oldStatus = invoice.status();
        if (!oldStatus.equals(status)) {
            jdbcTemplate.update(
                "UPDATE subscription_schema.subscription_invoice SET status = ?, provider_payment_intent_id = ?, " +
                    "provider_status = ?, provider_payment_id = ?, paid_at = CASE WHEN ? = 'PAID' THEN now() ELSE paid_at END, " +
                    "updated_at = now() WHERE id = ?",
                status, paymentIntentId, providerStatus, providerPaymentId, status, invoiceId
            );
            jdbcTemplate.update(
                "INSERT INTO subscription_schema.subscription_invoice_history " +
                    "(id, invoice_id, old_status, new_status, reason, created_at) VALUES (?, ?, ?, ?, ?, now())",
                UUID.randomUUID(), invoiceId, oldStatus, status,
                "Provider subscription payment status " + safe(providerStatus)
            );
        }

        SubscriptionResponse subscription = subscriptionRepository.findSubscriptionById(subscriptionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription was not found"));

        if ("PAID".equals(status)) {
            // Capacity is committed before the subscription can become ACTIVE. Any capacity failure rolls back
            // this entire inbox transaction so Service Bus can retry instead of creating an overbooked subscription.
            capacityService.commitForActivation(subscription);
            activateSubscription(subscriptionId);
            moveOccurrences(subscriptionId, invoice.cycleStart(), invoice.cycleEnd(), "READY_FOR_ORDER", "Billing cycle paid");
        } else if ("FAILED".equals(status) || "CANCELLED".equals(status)) {
            markPaymentFailed(subscriptionId, providerStatus);
            capacityService.releaseForPauseOrTerminal(
                subscription,
                LocalDate.now(),
                "Subscription payment was not completed: " + safe(providerStatus)
            );
            moveOccurrences(subscriptionId, invoice.cycleStart(), invoice.cycleEnd(), "PAYMENT_PENDING", "Billing payment not completed");
        }

        complete(eventId, "PROCESSED", null);
        return !oldStatus.equals(status);
    }

    private void activateSubscription(UUID subscriptionId) {
        List<String> states = jdbcTemplate.query(
            "SELECT status FROM subscription_schema.customer_subscription WHERE id = ? FOR UPDATE",
            (rs, rowNum) -> rs.getString(1), subscriptionId
        );
        if (states.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription was not found");
        }
        String old = states.getFirst();
        if (Set.of("PENDING_PAYMENT", "PAYMENT_FAILED").contains(old)) {
            jdbcTemplate.update(
                "UPDATE subscription_schema.customer_subscription SET status = 'ACTIVE', updated_at = now() WHERE id = ?",
                subscriptionId
            );
            history(subscriptionId, old, "ACTIVE", "Subscription billing cycle paid");
        }
    }

    private void markPaymentFailed(UUID subscriptionId, String providerStatus) {
        List<String> states = jdbcTemplate.query(
            "SELECT status FROM subscription_schema.customer_subscription WHERE id = ? FOR UPDATE",
            (rs, rowNum) -> rs.getString(1), subscriptionId
        );
        if (states.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription was not found");
        }
        String old = states.getFirst();
        if (Set.of("PENDING_PAYMENT", "ACTIVE", "PAYMENT_FAILED").contains(old) && !"PAYMENT_FAILED".equals(old)) {
            jdbcTemplate.update(
                "UPDATE subscription_schema.customer_subscription SET status = 'PAYMENT_FAILED', updated_at = now() WHERE id = ?",
                subscriptionId
            );
            history(subscriptionId, old, "PAYMENT_FAILED", "Provider status " + safe(providerStatus));
        }
    }

    private void moveOccurrences(
        UUID subscriptionId,
        LocalDate cycleStart,
        LocalDate cycleEnd,
        String target,
        String reason
    ) {
        List<OccurrenceState> occurrences = jdbcTemplate.query(
            "SELECT id, status FROM subscription_schema.subscription_occurrence " +
                "WHERE subscription_id = ? AND service_date >= ? AND service_date < ? " +
                "AND status IN ('BILLING_PENDING', 'PAYMENT_PENDING') FOR UPDATE",
            (rs, rowNum) -> new OccurrenceState(rs.getObject("id", UUID.class), rs.getString("status")),
            subscriptionId, cycleStart, cycleEnd
        );
        for (OccurrenceState occurrence : occurrences) {
            if (occurrence.status().equals(target)) {
                continue;
            }
            jdbcTemplate.update(
                "UPDATE subscription_schema.subscription_occurrence SET status = ?, updated_at = now() WHERE id = ?",
                target, occurrence.id()
            );
            jdbcTemplate.update(
                "INSERT INTO subscription_schema.subscription_occurrence_history " +
                    "(id, occurrence_id, old_status, new_status, reason, created_at) VALUES (?, ?, ?, ?, ?, now())",
                UUID.randomUUID(), occurrence.id(), occurrence.status(), target, reason
            );
        }
    }

    private Invoice findInvoice(UUID invoiceId, UUID subscriptionId) {
        return jdbcTemplate.query(
            "SELECT id, subscription_id, cycle_start, cycle_end, amount, currency, status " +
                "FROM subscription_schema.subscription_invoice WHERE id = ? AND subscription_id = ? FOR UPDATE",
            (rs, rowNum) -> new Invoice(
                rs.getObject("id", UUID.class), rs.getObject("subscription_id", UUID.class),
                rs.getObject("cycle_start", LocalDate.class), rs.getObject("cycle_end", LocalDate.class),
                rs.getBigDecimal("amount"), rs.getString("currency"), rs.getString("status")
            ),
            invoiceId, subscriptionId
        ).stream().findFirst().orElseThrow(
            () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription invoice was not found")
        );
    }

    private void history(UUID subscriptionId, String oldStatus, String newStatus, String reason) {
        jdbcTemplate.update(
            "INSERT INTO subscription_schema.subscription_status_history " +
                "(id, subscription_id, old_status, new_status, reason, actor_identity_id, created_at) " +
                "VALUES (?, ?, ?, ?, ?, NULL, now())",
            UUID.randomUUID(), subscriptionId, oldStatus, newStatus, reason
        );
    }

    private void reject(UUID eventId, String error) {
        complete(eventId, "REJECTED", error);
    }

    private void complete(UUID eventId, String state, String error) {
        jdbcTemplate.update(
            "UPDATE subscription_schema.subscription_payment_status_inbox SET processing_status = ?, error_message = ?, processed_at = now() WHERE event_id = ?",
            state, error, eventId
        );
    }

    private JsonNode parse(String rawPayload) {
        try {
            JsonNode value = objectMapper.readTree(rawPayload);
            if (value == null || !value.isObject() || !value.path("data").isObject()) {
                throw new IllegalArgumentException("Event must be an object with data");
            }
            return value;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Subscription payment status event is invalid", exception);
        }
    }

    private static UUID uuid(JsonNode node, String pointer) {
        String value = text(node, pointer);
        try {
            return UUID.fromString(value);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Required event UUID is invalid");
        }
    }

    private static UUID optionalUuid(JsonNode node, String pointer) {
        String value = text(node, pointer);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event causation UUID is invalid");
        }
    }

    private static String text(JsonNode node, String pointer) {
        JsonNode value = node.at(pointer);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private static BigDecimal decimal(JsonNode node, String pointer) {
        String value = text(node, pointer);
        try {
            return StringUtils.hasText(value) ? new BigDecimal(value) : null;
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Event amount is invalid");
        }
    }

    private static String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim().toUpperCase(Locale.ROOT) : "";
    }

    private static String safe(String value) {
        if (!StringUtils.hasText(value)) {
            return "UNKNOWN";
        }
        String normalized = value.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() > 200 ? normalized.substring(0, 200) : normalized;
    }

    private record Invoice(
        UUID id,
        UUID subscriptionId,
        LocalDate cycleStart,
        LocalDate cycleEnd,
        BigDecimal amount,
        String currency,
        String status
    ) {
    }

    private record OccurrenceState(UUID id, String status) {
    }
}
