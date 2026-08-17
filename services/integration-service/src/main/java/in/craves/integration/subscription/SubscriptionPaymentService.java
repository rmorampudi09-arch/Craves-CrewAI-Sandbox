package in.craves.integration.subscription;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.craves.integration.config.PaymentProviderProperties;
import in.craves.integration.config.PaymentRoutingProperties;
import in.craves.integration.payment.CashfreeRequestSafety;
import in.craves.integration.payment.RazorpayPaymentClient;
import in.craves.integration.payment.RazorpayRequestSafety;
import in.craves.integration.subscription.SubscriptionPaymentModels.CreateSubscriptionPaymentOrderRequest;
import in.craves.integration.subscription.SubscriptionPaymentModels.EventEnvelope;
import in.craves.integration.subscription.SubscriptionPaymentModels.PaymentRequestedData;
import in.craves.integration.subscription.SubscriptionPaymentModels.StatusChangedData;
import in.craves.integration.subscription.SubscriptionPaymentModels.SubscriptionPaymentResponse;
import in.craves.integration.subscription.SubscriptionPaymentModels.VerifySubscriptionPaymentRequest;
import in.craves.integration.subscription.SubscriptionPaymentRepository.PaymentIntent;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SubscriptionPaymentService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionPaymentService.class);
    private static final Set<String> SUPPORTED_EVENT_VERSIONS = Set.of("v1");
    private static final long RECONCILIATION_MIN_AGE_SECONDS = 5L;

    private final SubscriptionPaymentRepository repository;
    private final SubscriptionPaymentProperties properties;
    private final PaymentProviderProperties provider;
    private final PaymentRoutingProperties routing;
    private final RazorpayPaymentClient razorpayClient;
    private final ObjectMapper objectMapper;
    private final RestClient providerClient;
    private final RestClient subscriptionClient;

    public SubscriptionPaymentService(
        SubscriptionPaymentRepository repository,
        SubscriptionPaymentProperties properties,
        PaymentProviderProperties provider,
        PaymentRoutingProperties routing,
        RazorpayPaymentClient razorpayClient,
        ObjectMapper objectMapper,
        RestClient.Builder builder
    ) {
        this.repository = repository;
        this.properties = properties;
        this.provider = provider;
        this.routing = routing;
        this.razorpayClient = razorpayClient;
        this.objectMapper = objectMapper;
        this.providerClient = builder.clone().baseUrl(provider.baseUrl()).build();
        this.subscriptionClient = StringUtils.hasText(properties.getSubscriptionServiceBaseUrl())
            ? builder.clone().baseUrl(properties.getSubscriptionServiceBaseUrl()).build()
            : null;
    }

    public boolean acceptRequested(String rawPayload) {
        try {
            JsonNode raw = objectMapper.readTree(rawPayload);
            JavaType type = objectMapper.getTypeFactory().constructParametricType(
                EventEnvelope.class,
                PaymentRequestedData.class
            );
            EventEnvelope<PaymentRequestedData> event = objectMapper.readerFor(type).readValue(raw);
            validate(event);
            return repository.acceptRequest(event, raw);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Subscription payment event is invalid", exception);
        }
    }

    public SubscriptionPaymentResponse getLatestOwned(String authorization, UUID subscriptionId) {
        requireAuthorization(authorization);
        validateSubscriptionOwnership(authorization, subscriptionId);
        PaymentIntent intent = repository.findLatestBySubscription(subscriptionId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription payment invoice is not ready"));
        return repository.response(reconcilePending(intent));
    }

    public SubscriptionPaymentResponse getOwned(String authorization, UUID invoiceId) {
        PaymentIntent intent = owned(authorization, invoiceId);
        return repository.response(reconcilePending(intent));
    }

    public SubscriptionPaymentResponse createProviderOrder(
        String authorization,
        UUID invoiceId,
        CreateSubscriptionPaymentOrderRequest request
    ) {
        PaymentIntent intent = owned(authorization, invoiceId);
        if ("PAID".equals(intent.status())) {
            return repository.response(intent);
        }
        if ("PAYMENT_PENDING".equals(intent.status())) {
            return repository.response(reconcilePending(intent));
        }
        if (
            "FAILED".equals(intent.status())
                && routing.provider().equalsIgnoreCase(intent.provider())
                && StringUtils.hasText(intent.providerOrderId())
        ) {
            return repository.response(intent);
        }
        if (routing.razorpay()) {
            return createRazorpayOrder(intent);
        }
        if (!provider.paymentExecutionAllowed()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Cashfree production payment execution is not enabled");
        }
        String orderId = "CRVSUB_" + invoiceId.toString().replace("-", "");
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("customer_id", intent.customerIdentityId().toString());
        customer.put("customer_name", request.customerName().trim());
        customer.put(
            "customer_phone",
            CashfreeRequestSafety.normalizeIndianPhone(request.customerPhone(), provider.sandbox())
        );
        if (StringUtils.hasText(request.customerEmail())) {
            customer.put("customer_email", request.customerEmail().trim());
        }
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("return_url", CashfreeRequestSafety.safeReturnUrl(provider, request.returnUrl()));
        meta.put("notify_url", provider.webhookUrl());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("order_id", orderId);
        body.put("order_amount", intent.amount());
        body.put("order_currency", intent.currency());
        body.put("customer_details", customer);
        body.put("order_meta", meta);
        body.put("order_note", "Craves subscription invoice " + invoiceId);

        JsonNode response = providerClient.post()
            .uri("/pg/orders")
            .header("x-client-id", provider.clientId())
            .header("x-client-" + "secret", provider.clientKey())
            .header("x-api-version", provider.apiVersion())
            .header("x-idempotency-key", invoiceId.toString())
            .body(body)
            .retrieve()
            .body(JsonNode.class);
        CashfreeRequestSafety.requireCreateOrderResponse(
            response, orderId, intent.amount(), intent.currency()
        );
        PaymentIntent stored = repository.storeProviderOrder(
            intent.id(),
            text(response, "order_id"),
            text(response, "cf_order_id"),
            text(response, "payment_session_id"),
            text(response, "order_status"),
            objectMapper.valueToTree(body),
            response
        );
        return repository.response(stored);
    }

    public SubscriptionPaymentResponse verifyRazorpay(
        String authorization,
        UUID invoiceId,
        VerifySubscriptionPaymentRequest request
    ) {
        PaymentIntent intent = owned(authorization, invoiceId);
        if (!"RAZORPAY".equalsIgnoreCase(intent.provider()) || request == null
            || !StringUtils.hasText(intent.providerOrderId())
            || !intent.providerOrderId().equals(request.providerOrderId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Razorpay verification details are invalid");
        }
        if ("PAID".equals(intent.status())) return repository.response(intent);
        RazorpayPaymentClient.VerifiedPayment verified = razorpayClient.verifyCheckout(
            intent.providerOrderId(), request.providerPaymentId(), request.providerSignature(),
            intent.amount(), intent.currency()
        );
        applyStatusEvent(intent, "PAID", verified.providerStatus(), verified.paymentId());
        return repository.response(repository.findByInvoice(invoiceId).orElse(intent));
    }

    public boolean handlesRazorpayOrder(String providerOrderId) {
        return StringUtils.hasText(providerOrderId) && providerOrderId.startsWith("order_")
            && repository.findByProviderOrder("RAZORPAY", providerOrderId).isPresent();
    }

    public void applyRazorpayWebhook(JsonNode payment, String eventType) {
        String orderId = text(payment, "order_id");
        String paymentId = text(payment, "id");
        String providerStatus = text(payment, "status");
        PaymentIntent intent = repository.findByProviderOrder("RAZORPAY", orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription payment intent was not found"));
        if ("captured".equalsIgnoreCase(providerStatus)
            || "payment.captured".equalsIgnoreCase(eventType)
            || "order.paid".equalsIgnoreCase(eventType)) {
            RazorpayRequestSafety.requireMoney(
                intent.amount(), intent.currency(), longValue(payment, "amount"), text(payment, "currency"),
                "Subscription Razorpay payment"
            );
            applyStatusEvent(intent, "PAID", providerStatus, paymentId);
        } else if ("failed".equalsIgnoreCase(providerStatus)) {
            applyStatusEvent(intent, "FAILED", providerStatus, paymentId);
        }
    }

    private SubscriptionPaymentResponse createRazorpayOrder(PaymentIntent intent) {
        String receipt = "CRVSUB_" + intent.invoiceId().toString().replace("-", "");
        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("craves_invoice_id", intent.invoiceId().toString());
        notes.put("craves_subscription_id", intent.subscriptionId().toString());
        RazorpayPaymentClient.CreatedOrder created = razorpayClient.createOrder(
            receipt, intent.amount(), intent.currency(), notes
        );
        return repository.response(repository.storeRazorpayOrder(
            intent.id(), created.orderId(), created.checkoutKeyId(), created.providerStatus()
        ));
    }

    public boolean handlesWebhook(JsonNode payload) {
        String orderId = firstText(payload, "/data/order/order_id", "/order/order_id", "/order_id");
        return StringUtils.hasText(orderId) && orderId.startsWith("CRVSUB_");
    }

    public void applyWebhook(JsonNode payload) {
        String orderId = firstText(payload, "/data/order/order_id", "/order/order_id", "/order_id");
        String paymentStatus = firstText(payload, "/data/payment/payment_status", "/payment/payment_status", "/payment_status");
        String providerPaymentId = firstText(payload, "/data/payment/cf_payment_id", "/payment/cf_payment_id", "/cf_payment_id");
        BigDecimal amount = decimal(firstText(payload, "/data/payment/payment_amount", "/payment/payment_amount", "/payment_amount"));
        String currency = firstText(payload, "/data/payment/payment_currency", "/payment/payment_currency", "/payment_currency");
        PaymentIntent intent = repository.findByCashfreeOrder(orderId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription payment intent was not found"));
        validateProviderAmount(intent, amount, currency);
        String normalized = switch (paymentStatus == null ? "" : paymentStatus.toUpperCase(Locale.ROOT)) {
            case "SUCCESS" -> "PAID";
            case "FAILED", "USER_DROPPED", "CANCELLED" -> "FAILED";
            default -> "PAYMENT_PENDING";
        };
        applyStatusEvent(intent, normalized, paymentStatus, providerPaymentId);
    }

    private PaymentIntent reconcilePending(PaymentIntent intent) {
        if (!"CASHFREE".equalsIgnoreCase(intent.provider())
            || !"PAYMENT_PENDING".equals(intent.status()) || !StringUtils.hasText(intent.cashfreeOrderId())) {
            return intent;
        }
        if (
            intent.updatedAt() != null
                && Duration.between(intent.updatedAt(), Instant.now()).abs().getSeconds() < RECONCILIATION_MIN_AGE_SECONDS
        ) {
            return intent;
        }

        try {
            JsonNode payments = providerClient.get()
                .uri("/pg/orders/{orderId}/payments", intent.cashfreeOrderId())
                .header("x-client-id", provider.clientId())
                .header("x-client-" + "secret", provider.clientKey())
                .header("x-api-version", provider.apiVersion())
                .retrieve()
                .body(JsonNode.class);

            JsonNode successful = successfulPayment(payments);
            if (successful != null) {
                BigDecimal amount = decimal(text(successful, "payment_amount"));
                String currency = text(successful, "payment_currency");
                validateProviderAmount(intent, amount, currency);
                String providerPaymentId = text(successful, "cf_payment_id");
                applyStatusEvent(intent, "PAID", "SUCCESS", providerPaymentId);
                LOGGER.info(
                    "Subscription Cashfree reconciliation marked invoice paid invoiceId={} orderId={}",
                    intent.invoiceId(),
                    intent.cashfreeOrderId()
                );
            } else {
                String providerStatus = firstPaymentStatus(payments);
                if (StringUtils.hasText(providerStatus)) {
                    repository.applyProviderStatus(
                        intent,
                        intent.status(),
                        providerStatus,
                        null,
                        objectMapper.createObjectNode()
                    );
                }
            }
            return repository.findByInvoice(intent.invoiceId()).orElse(intent);
        } catch (RestClientResponseException exception) {
            LOGGER.warn(
                "Subscription Cashfree reconciliation request failed invoiceId={} orderId={} status={}",
                intent.invoiceId(),
                intent.cashfreeOrderId(),
                exception.getStatusCode().value()
            );
            return intent;
        } catch (RuntimeException exception) {
            LOGGER.warn(
                "Subscription Cashfree reconciliation failed invoiceId={} orderId={} reason={}",
                intent.invoiceId(),
                intent.cashfreeOrderId(),
                safeLog(exception)
            );
            return intent;
        }
    }

    private JsonNode successfulPayment(JsonNode payments) {
        if (payments == null || !payments.isArray()) {
            return null;
        }
        for (JsonNode payment : payments) {
            if ("SUCCESS".equalsIgnoreCase(text(payment, "payment_status"))) {
                return payment;
            }
        }
        return null;
    }

    private String firstPaymentStatus(JsonNode payments) {
        if (payments == null || !payments.isArray() || payments.isEmpty()) {
            return null;
        }
        return text(payments.get(0), "payment_status");
    }

    private void validateProviderAmount(PaymentIntent intent, BigDecimal amount, String currency) {
        CashfreeRequestSafety.requireMoney(
            intent.amount(), intent.currency(), amount, currency, "Subscription Cashfree payment"
        );
    }

    private void applyStatusEvent(
        PaymentIntent intent,
        String normalized,
        String providerStatus,
        String providerPaymentId
    ) {
        StatusChangedData data = new StatusChangedData(
            intent.id(), intent.invoiceId(), intent.subscriptionId(), normalized, providerStatus,
            providerPaymentId, intent.amount(), intent.currency(), Instant.now()
        );
        ObjectNode event = objectMapper.createObjectNode();
        UUID eventId = UUID.randomUUID();
        event.put("eventId", eventId.toString());
        event.put("eventType", SubscriptionPaymentModels.PAYMENT_STATUS_CHANGED);
        event.put("eventVersion", "v1");
        event.put("occurredAt", data.changedAt().toString());
        event.put("correlationId", intent.invoiceId().toString());
        event.put(
            "causationId",
            providerPaymentId == null
                ? eventId.toString()
                : UUID.nameUUIDFromBytes(providerPaymentId.getBytes(StandardCharsets.UTF_8)).toString()
        );
        event.put("subject", intent.invoiceId().toString());
        event.set("data", objectMapper.valueToTree(data));
        repository.applyProviderStatus(intent, normalized, providerStatus, providerPaymentId, event);
    }

    private PaymentIntent owned(String authorization, UUID invoiceId) {
        requireAuthorization(authorization);
        PaymentIntent intent = repository.findByInvoice(invoiceId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription payment was not found"));
        validateSubscriptionOwnership(authorization, intent.subscriptionId());
        return intent;
    }

    private void validateSubscriptionOwnership(String authorization, UUID subscriptionId) {
        if (subscriptionClient == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Subscription ownership validation is unavailable");
        }
        try {
            subscriptionClient.get()
                .uri("/api/v1/subscriptions/{subscriptionId}", subscriptionId)
                .header(HttpHeaders.AUTHORIZATION, authorization)
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientResponseException exception) {
            if (exception.getStatusCode().value() == 401 || exception.getStatusCode().value() == 403) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Customer access token is invalid");
            }
            if (exception.getStatusCode().value() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Subscription payment was not found");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Subscription ownership validation failed");
        }
    }

    private static void validate(EventEnvelope<PaymentRequestedData> event) {
        if (event == null || event.eventId() == null || event.data() == null
            || !SubscriptionPaymentModels.PAYMENT_REQUESTED.equals(event.eventType())
            || !SUPPORTED_EVENT_VERSIONS.contains(event.eventVersion())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported subscription payment event");
        }
        PaymentRequestedData data = event.data();
        if (data.invoiceId() == null || data.subscriptionId() == null || data.planId() == null
            || data.customerIdentityId() == null || data.cycleStart() == null || data.cycleEnd() == null
            || !data.cycleEnd().isAfter(data.cycleStart()) || data.amount() == null || data.amount().signum() <= 0
            || !StringUtils.hasText(data.currency()) || data.currency().length() != 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Subscription payment event data is incomplete");
        }
    }

    private static void requireAuthorization(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Craves access token is required");
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String firstText(JsonNode node, String... pointers) {
        if (node == null) {
            return null;
        }
        for (String pointer : pointers) {
            JsonNode value = node.at(pointer);
            if (!value.isMissingNode() && !value.isNull() && StringUtils.hasText(value.asText())) {
                return value.asText();
            }
        }
        return null;
    }

    private static BigDecimal decimal(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cashfree payment amount is invalid");
        }
    }

    private static long longValue(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.canConvertToLong()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Razorpay payment amount is invalid");
        }
        return value.longValue();
    }

    private static String safeLog(Throwable error) {
        String value = error == null || error.getMessage() == null ? "unknown" : error.getMessage();
        value = value.replace('\n', ' ').replace('\r', ' ').trim();
        return value.length() > 300 ? value.substring(0, 300) : value;
    }
}
