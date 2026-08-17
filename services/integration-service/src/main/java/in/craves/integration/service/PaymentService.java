package in.craves.integration.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.integration.config.OrderClientProperties;
import in.craves.integration.config.PaymentProviderProperties;
import in.craves.integration.config.PaymentRoutingProperties;
import in.craves.integration.payment.CashfreeRequestSafety;
import in.craves.integration.payment.RazorpayPaymentClient;
import in.craves.integration.payment.RazorpayRequestSafety;
import in.craves.integration.subscription.SubscriptionPaymentService;
import in.craves.integration.web.PaymentDtos.CreatePaymentOrderRequest;
import in.craves.integration.web.PaymentDtos.CreatePaymentOrderResponse;
import in.craves.integration.web.PaymentDtos.PaymentOrderResponse;
import in.craves.integration.web.PaymentDtos.PaymentOrderStatus;
import in.craves.integration.web.PaymentDtos.VerifyPaymentResponse;
import in.craves.integration.web.PaymentDtos.VerifyPaymentRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PaymentService {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final PaymentProviderProperties provider;
    private final PaymentRoutingProperties routing;
    private final RazorpayPaymentClient razorpayClient;
    private final SubscriptionPaymentService subscriptionPayments;
    private final OrderClientProperties orderClientProperties;
    private final RestClient providerClient;
    private final RestClient orderClient;
    private final RestClient internalOrderClient;

    public PaymentService(
        JdbcTemplate jdbcTemplate,
        ObjectMapper objectMapper,
        PaymentProviderProperties provider,
        PaymentRoutingProperties routing,
        RazorpayPaymentClient razorpayClient,
        SubscriptionPaymentService subscriptionPayments,
        OrderClientProperties orderClientProperties,
        RestClient.Builder builder
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
        this.provider = provider;
        this.routing = routing;
        this.razorpayClient = razorpayClient;
        this.subscriptionPayments = subscriptionPayments;
        this.orderClientProperties = orderClientProperties;
        this.providerClient = builder.clone().baseUrl(provider.baseUrl()).build();
        this.orderClient = builder.clone().baseUrl(orderClientProperties.baseUrl()).build();
        this.internalOrderClient = builder.clone().baseUrl(orderClientProperties.internalBaseUrl()).build();
    }

    @Transactional
    public CreatePaymentOrderResponse createPaymentOrder(String authorizationHeader, CreatePaymentOrderRequest request) {
        requireAuthorization(authorizationHeader);
        CheckoutResponse checkout = fetchOwnedCheckout(authorizationHeader, request.checkoutId());
        if (checkout.id() == null || checkout.grandTotal() == null || checkout.customerIdentityId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Checkout was not valid for payment");
        }
        Optional<PaymentOrderResponse> existing = findByCheckout(checkout.id());
        if (existing.isPresent() && (
            existing.get().status() == PaymentOrderStatus.PAID
                || routing.provider().equalsIgnoreCase(existing.get().provider())
        )) {
            requireMatchingCustomer(existing.get().customerIdentityId(), checkout);
            return toCreateResponse(existing.get());
        }
        return createProviderOrder(checkout, request);
    }

    public PaymentOrderResponse getPaymentOrder(String authorizationHeader, UUID paymentOrderId) {
        requireAuthorization(authorizationHeader);
        PaymentOrderResponse paymentOrder = loadPaymentOrder(paymentOrderId);
        CheckoutResponse checkout = fetchOwnedCheckout(authorizationHeader, paymentOrder.checkoutId());
        requireMatchingCustomer(paymentOrder.customerIdentityId(), checkout);
        return paymentOrder;
    }

    @Transactional
    public VerifyPaymentResponse verifyPayment(
        String authorizationHeader,
        UUID paymentOrderId,
        VerifyPaymentRequest request
    ) {
        PaymentOrderResponse existing = getPaymentOrder(authorizationHeader, paymentOrderId);
        if ("RAZORPAY".equalsIgnoreCase(existing.provider())) {
            return verifyRazorpayPayment(existing, request);
        }
        JsonNode response = providerClient.get()
            .uri("/pg/orders/{orderId}", existing.providerOrderId())
            .header("x-client-id", provider.clientId())
            .header("x-client-" + "secret", provider.clientKey())
            .header("x-api-version", provider.apiVersion())
            .retrieve()
            .body(JsonNode.class);
        if (response == null || !existing.providerOrderId().equals(text(response, "order_id"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cashfree order verification identity does not match Craves");
        }
        CashfreeRequestSafety.requireMoney(
            existing.amount(),
            existing.currency(),
            CashfreeRequestSafety.decimal(text(response, "order_amount")),
            text(response, "order_currency"),
            "Cashfree order verification"
        );
        String providerStatus = text(response, "order_status");
        boolean paidTransition = "PAID".equalsIgnoreCase(providerStatus)
            && existing.status() != PaymentOrderStatus.PAID;
        PaymentOrderStatus status = "PAID".equalsIgnoreCase(providerStatus)
            ? PaymentOrderStatus.PAID
            : existing.status();
        jdbcTemplate.update(
            "UPDATE payment_schema.payment_order SET status = ?, provider_status = ?, response_payload = ?::jsonb, updated_at = now() WHERE id = ?",
            status.name(), providerStatus, json(response), paymentOrderId
        );
        if (paidTransition) {
            notifyOrderPaid(existing.checkoutId(), existing, null);
        }
        return new VerifyPaymentResponse(paymentOrderId, status, providerStatus, existing.providerPaymentId());
    }

    private VerifyPaymentResponse verifyRazorpayPayment(
        PaymentOrderResponse existing,
        VerifyPaymentRequest request
    ) {
        if (request == null
            || !existing.providerOrderId().equals(request.providerOrderId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Razorpay verification details are required");
        }
        RazorpayPaymentClient.VerifiedPayment verified = razorpayClient.verifyCheckout(
            existing.providerOrderId(),
            request.providerPaymentId(),
            request.providerSignature(),
            existing.amount(),
            existing.currency()
        );
        int transitioned = jdbcTemplate.update(
            "UPDATE payment_schema.payment_order SET status = ?, provider_status = ?, provider_payment_id = ?, response_payload = ?::jsonb, updated_at = now() WHERE id = ? AND status <> ?",
            PaymentOrderStatus.PAID.name(), verified.providerStatus(), verified.paymentId(),
            json(verified.response()), existing.paymentOrderId(), PaymentOrderStatus.PAID.name()
        );
        if (transitioned > 0) {
            notifyOrderPaid(existing.checkoutId(), existing, verified.paymentId());
        }
        return new VerifyPaymentResponse(
            existing.paymentOrderId(), PaymentOrderStatus.PAID, verified.providerStatus(), verified.paymentId()
        );
    }

    @Transactional
    public void handleRazorpayWebhook(String signature, String eventId, String rawBody) {
        UUID inboxId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO payment_schema.webhook_inbox (id, provider, signature_hash, event_identity, processing_status, raw_payload, received_at) VALUES (?, 'RAZORPAY', ?, ?, 'RECEIVED', ?, now())",
            inboxId, hash(signature), eventId, rawBody
        );
        if (!razorpayClient.verifyWebhook(rawBody, signature)) {
            jdbcTemplate.update(
                "UPDATE payment_schema.webhook_inbox SET processing_status = 'REJECTED', error_message = 'Invalid signature', processed_at = now() WHERE id = ?",
                inboxId
            );
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid Razorpay webhook signature");
        }
        try {
            JsonNode payload = objectMapper.readTree(rawBody);
            String eventType = text(payload, "event");
            JsonNode payment = payload.at("/payload/payment/entity");
            String paymentId = text(payment, "id");
            String orderId = text(payment, "order_id");
            String status = text(payment, "status");
            if (!StringUtils.hasText(eventType) || !StringUtils.hasText(paymentId) || !StringUtils.hasText(orderId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Razorpay webhook payload is incomplete");
            }
            if (subscriptionPayments.handlesRazorpayOrder(orderId)) {
                subscriptionPayments.applyRazorpayWebhook(payment, eventType);
                String identity = StringUtils.hasText(eventId) ? eventId : paymentId + ":" + eventType;
                jdbcTemplate.update(
                    "UPDATE payment_schema.webhook_inbox SET event_identity = ?, processing_status = 'PROCESSED', processed_at = now() WHERE id = ?",
                    identity, inboxId
                );
                return;
            }
            PaymentOrderResponse order = findByProviderOrderId("RAZORPAY", orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment order was not found"));
            if ("payment.captured".equalsIgnoreCase(eventType) || "order.paid".equalsIgnoreCase(eventType)) {
                RazorpayRequestSafety.requireMoney(
                    order.amount(), order.currency(), longValue(payment, "amount"), text(payment, "currency"),
                    "Razorpay success webhook"
                );
            }
            String identity = StringUtils.hasText(eventId) ? eventId : paymentId + ":" + eventType;
            try {
                jdbcTemplate.update(
                    "INSERT INTO payment_schema.payment_event (id, payment_order_id, provider_event_id, event_type, payment_status, raw_payload, created_at) VALUES (?, ?, ?, ?, ?, ?::jsonb, now())",
                    UUID.randomUUID(), order.paymentOrderId(), identity, eventType, status, rawBody
                );
            } catch (DuplicateKeyException duplicate) {
                jdbcTemplate.update(
                    "UPDATE payment_schema.webhook_inbox SET processing_status = 'DUPLICATE', processed_at = now() WHERE id = ?",
                    inboxId
                );
                return;
            }
            jdbcTemplate.update(
                "INSERT INTO payment_schema.payment_attempt (id, payment_order_id, provider, provider_payment_id, cf_payment_id, payment_status, payment_amount, payment_currency, raw_payload, created_at) VALUES (?, ?, 'RAZORPAY', ?, ?, ?, ?, ?, ?::jsonb, now())",
                UUID.randomUUID(), order.paymentOrderId(), paymentId, paymentId, status,
                RazorpayRequestSafety.fromSubunits(longValue(payment, "amount")), text(payment, "currency"), rawBody
            );
            if ("captured".equalsIgnoreCase(status)
                || "payment.captured".equalsIgnoreCase(eventType)
                || "order.paid".equalsIgnoreCase(eventType)) {
                int transitioned = jdbcTemplate.update(
                    "UPDATE payment_schema.payment_order SET status = 'PAID', provider_status = ?, provider_payment_id = ?, updated_at = now() WHERE id = ? AND status <> 'PAID'",
                    status, paymentId, order.paymentOrderId()
                );
                if (transitioned > 0) notifyOrderPaid(order.checkoutId(), order, paymentId);
            } else {
                jdbcTemplate.update(
                    "UPDATE payment_schema.payment_order SET provider_status = ?, provider_payment_id = ?, updated_at = now() WHERE id = ?",
                    status, paymentId, order.paymentOrderId()
                );
            }
            jdbcTemplate.update(
                "UPDATE payment_schema.webhook_inbox SET event_identity = ?, processing_status = 'PROCESSED', processed_at = now() WHERE id = ?",
                identity, inboxId
            );
        } catch (ResponseStatusException exception) {
            jdbcTemplate.update(
                "UPDATE payment_schema.webhook_inbox SET processing_status = 'FAILED', error_message = ?, processed_at = now() WHERE id = ?",
                safe(exception.getReason()), inboxId
            );
            throw exception;
        } catch (Exception exception) {
            jdbcTemplate.update(
                "UPDATE payment_schema.webhook_inbox SET processing_status = 'FAILED', error_message = ?, processed_at = now() WHERE id = ?",
                safe(exception.getMessage()), inboxId
            );
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Razorpay webhook processing failed");
        }
    }

    @Transactional
    public void handleWebhook(String timestamp, String signature, String rawBody) {
        UUID inboxId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO payment_schema.webhook_inbox (id, provider, signature_hash, processing_status, raw_payload, received_at) VALUES (?, ?, ?, ?, ?, now())",
            inboxId, "CASHFREE", hash(signature), "RECEIVED", rawBody
        );
        if (!verifySignature(timestamp, signature, rawBody)) {
            jdbcTemplate.update(
                "UPDATE payment_schema.webhook_inbox SET processing_status = ?, error_message = ?, processed_at = now() WHERE id = ?",
                "REJECTED", "Invalid signature", inboxId
            );
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid webhook signature");
        }
        try {
            JsonNode payload = objectMapper.readTree(rawBody);
            String orderId = firstText(payload, "/data/order/order_id", "/order/order_id", "/order_id");
            String cfPaymentId = firstText(payload, "/data/payment/cf_payment_id", "/payment/cf_payment_id", "/cf_payment_id");
            String paymentStatus = firstText(payload, "/data/payment/payment_status", "/payment/payment_status", "/payment_status");
            BigDecimal amount = decimal(firstText(payload, "/data/payment/payment_amount", "/payment/payment_amount", "/payment_amount"));
            String currency = firstText(payload, "/data/payment/payment_currency", "/payment/payment_currency", "/payment_currency");
            PaymentOrderResponse order = findByProviderOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment order was not found"));
            if ("SUCCESS".equalsIgnoreCase(paymentStatus)) {
                CashfreeRequestSafety.requireMoney(
                    order.amount(), order.currency(), amount, currency, "Cashfree success webhook"
                );
            }
            String eventIdentity = StringUtils.hasText(cfPaymentId)
                ? cfPaymentId
                : orderId + ":" + paymentStatus + ":" + hash(rawBody);
            try {
                jdbcTemplate.update(
                    "INSERT INTO payment_schema.payment_event (id, payment_order_id, provider_event_id, event_type, payment_status, raw_payload, created_at) VALUES (?, ?, ?, ?, ?, ?::jsonb, now())",
                    UUID.randomUUID(), order.paymentOrderId(), eventIdentity,
                    firstText(payload, "/type", "/event_type"), paymentStatus, rawBody
                );
            } catch (DuplicateKeyException duplicate) {
                jdbcTemplate.update(
                    "UPDATE payment_schema.webhook_inbox SET event_identity = ?, processing_status = ?, processed_at = now() WHERE id = ?",
                    eventIdentity, "DUPLICATE", inboxId
                );
                return;
            }
            String attemptCurrency = StringUtils.hasText(currency) ? currency : order.currency();
            jdbcTemplate.update(
                "INSERT INTO payment_schema.payment_attempt (id, payment_order_id, cf_payment_id, payment_status, payment_amount, payment_currency, raw_payload, created_at) VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, now())",
                UUID.randomUUID(), order.paymentOrderId(), cfPaymentId, paymentStatus, amount, attemptCurrency, rawBody
            );
            if ("SUCCESS".equalsIgnoreCase(paymentStatus)) {
                boolean paidTransition = order.status() != PaymentOrderStatus.PAID;
                jdbcTemplate.update(
                    "UPDATE payment_schema.payment_order SET status = ?, provider_status = ?, updated_at = now() WHERE id = ?",
                    PaymentOrderStatus.PAID.name(), paymentStatus, order.paymentOrderId()
                );
                if (paidTransition) {
                    notifyOrderPaid(order.checkoutId(), order, cfPaymentId);
                }
            } else {
                jdbcTemplate.update(
                    "UPDATE payment_schema.payment_order SET provider_status = ?, updated_at = now() WHERE id = ?",
                    paymentStatus, order.paymentOrderId()
                );
            }
            jdbcTemplate.update(
                "UPDATE payment_schema.webhook_inbox SET event_identity = ?, processing_status = ?, processed_at = now() WHERE id = ?",
                eventIdentity, "PROCESSED", inboxId
            );
        } catch (ResponseStatusException ex) {
            jdbcTemplate.update(
                "UPDATE payment_schema.webhook_inbox SET processing_status = ?, error_message = ?, processed_at = now() WHERE id = ?",
                "FAILED", ex.getReason(), inboxId
            );
            throw ex;
        } catch (Exception ex) {
            jdbcTemplate.update(
                "UPDATE payment_schema.webhook_inbox SET processing_status = ?, error_message = ?, processed_at = now() WHERE id = ?",
                "FAILED", safe(ex.getMessage()), inboxId
            );
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Webhook processing failed");
        }
    }

    private CheckoutResponse fetchOwnedCheckout(String authorizationHeader, UUID checkoutId) {
        requireAuthorization(authorizationHeader);
        try {
            CheckoutResponse checkout = orderClient.get()
                .uri("/checkout/{checkoutId}", checkoutId)
                .header(HttpHeaders.AUTHORIZATION, authorizationHeader)
                .retrieve()
                .body(CheckoutResponse.class);
            if (checkout == null || checkout.id() == null || checkout.customerIdentityId() == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Checkout was not found");
            }
            return checkout;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Customer access token is invalid");
            }
            if (ex.getStatusCode().value() == 404) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment order was not found");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Order ownership validation failed");
        }
    }

    static void requireAuthorization(String authorizationHeader) {
        if (!StringUtils.hasText(authorizationHeader)
            || !authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing or invalid access token");
        }
    }

    static void requireMatchingCustomer(UUID paymentCustomerIdentityId, CheckoutResponse checkout) {
        if (paymentCustomerIdentityId == null
            || checkout == null
            || checkout.customerIdentityId() == null
            || !paymentCustomerIdentityId.equals(checkout.customerIdentityId())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment order was not found");
        }
    }

    private CreatePaymentOrderResponse createProviderOrder(CheckoutResponse checkout, CreatePaymentOrderRequest request) {
        if (routing.razorpay()) {
            return createRazorpayOrder(checkout, request);
        }
        return createCashfreeOrder(checkout, request);
    }

    private CreatePaymentOrderResponse createRazorpayOrder(CheckoutResponse checkout, CreatePaymentOrderRequest request) {
        UUID paymentOrderId = UUID.randomUUID();
        String orderRef = "CRV_" + checkout.id().toString().replace("-", "") + "_RZP";
        Map<String, String> notes = new LinkedHashMap<>();
        notes.put("craves_checkout_id", checkout.id().toString());
        notes.put("craves_customer_identity_id", checkout.customerIdentityId().toString());
        if (StringUtils.hasText(request.customerName())) notes.put("customer_name", request.customerName().trim());
        RazorpayPaymentClient.CreatedOrder created = razorpayClient.createOrder(
            orderRef, checkout.grandTotal(), checkout.currency(), notes
        );
        jdbcTemplate.update(
            "INSERT INTO payment_schema.payment_order (id, checkout_id, customer_identity_id, craves_payment_order_ref, provider, provider_order_id, checkout_key_id, amount, currency, status, provider_status, request_payload, response_payload, created_at, updated_at) VALUES (?, ?, ?, ?, 'RAZORPAY', ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, now(), now())",
            paymentOrderId, checkout.id(), checkout.customerIdentityId(), orderRef, created.orderId(),
            created.checkoutKeyId(), checkout.grandTotal(), checkout.currency(),
            PaymentOrderStatus.PAYMENT_PENDING.name(), created.providerStatus(),
            json(created.request()), json(created.response())
        );
        return new CreatePaymentOrderResponse(
            paymentOrderId, checkout.id(), orderRef, "RAZORPAY", created.orderId(), null,
            created.checkoutKeyId(), null, checkout.grandTotal(), checkout.currency(),
            PaymentOrderStatus.PAYMENT_PENDING, Instant.now()
        );
    }

    private CreatePaymentOrderResponse createCashfreeOrder(CheckoutResponse checkout, CreatePaymentOrderRequest request) {
        UUID paymentOrderId = UUID.randomUUID();
        String orderRef = "CRV_" + checkout.id().toString().replace("-", "");
        Map<String, Object> customer = new LinkedHashMap<>();
        customer.put("customer_id", checkout.customerIdentityId().toString());
        customer.put(
            "customer_phone",
            CashfreeRequestSafety.normalizeIndianPhone(request.customerPhone(), provider.sandbox())
        );
        if (StringUtils.hasText(request.customerEmail())) {
            customer.put("customer_email", request.customerEmail().trim());
        }
        customer.put(
            "customer_name",
            StringUtils.hasText(request.customerName()) ? request.customerName().trim() : "Craves Customer"
        );
        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("return_url", CashfreeRequestSafety.safeReturnUrl(provider, request.returnUrl()));
        meta.put("notify_url", provider.webhookUrl());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("order_id", orderRef);
        body.put("order_amount", checkout.grandTotal());
        body.put("order_currency", checkout.currency());
        body.put("customer_details", customer);
        body.put("order_meta", meta);
        JsonNode response = providerClient.post()
            .uri("/pg/orders")
            .header("x-client-id", provider.clientId())
            .header("x-client-" + "secret", provider.clientKey())
            .header("x-api-version", provider.apiVersion())
            .header("x-idempotency-key", checkout.id().toString())
            .body(body)
            .retrieve()
            .body(JsonNode.class);
        CashfreeRequestSafety.requireCreateOrderResponse(
            response, orderRef, checkout.grandTotal(), checkout.currency()
        );
        String paymentSessionId = text(response, "payment_session_id");
        String cfOrderId = text(response, "cf_order_id");
        String cashfreeOrderId = text(response, "order_id");
        String providerStatus = text(response, "order_status");
        jdbcTemplate.update(
            "INSERT INTO payment_schema.payment_order (id, checkout_id, customer_identity_id, craves_payment_order_ref, provider, provider_order_id, provider_payment_id, cashfree_order_id, cashfree_cf_order_id, payment_session_id, amount, currency, status, provider_status, request_payload, response_payload, created_at, updated_at) VALUES (?, ?, ?, ?, 'CASHFREE', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, now(), now())",
            paymentOrderId, checkout.id(), checkout.customerIdentityId(), orderRef, cashfreeOrderId, cfOrderId,
            cashfreeOrderId, cfOrderId,
            paymentSessionId, checkout.grandTotal(), checkout.currency(), PaymentOrderStatus.PAYMENT_PENDING.name(),
            providerStatus, json(body), json(response)
        );
        return new CreatePaymentOrderResponse(
            paymentOrderId, checkout.id(), orderRef, "CASHFREE", cashfreeOrderId, cfOrderId, null, paymentSessionId,
            checkout.grandTotal(), checkout.currency(), PaymentOrderStatus.PAYMENT_PENDING, Instant.now()
        );
    }

    private PaymentOrderResponse loadPaymentOrder(UUID paymentOrderId) {
        return jdbcTemplate.query(
            "SELECT * FROM payment_schema.payment_order WHERE id = ?",
            (rs, rowNum) -> new PaymentOrderResponse(
                rs.getObject("id", UUID.class),
                rs.getObject("checkout_id", UUID.class),
                rs.getObject("customer_identity_id", UUID.class),
                rs.getString("craves_payment_order_ref"),
                rs.getString("provider"),
                rs.getString("provider_order_id"),
                rs.getString("provider_payment_id"),
                rs.getBigDecimal("amount"),
                rs.getString("currency"),
                PaymentOrderStatus.valueOf(rs.getString("status")),
                rs.getString("provider_status"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant()
            ),
            paymentOrderId
        ).stream().findFirst().orElseThrow(() ->
            new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment order was not found")
        );
    }

    private Optional<PaymentOrderResponse> findByCheckout(UUID checkoutId) {
        return jdbcTemplate.query(
            "SELECT id FROM payment_schema.payment_order WHERE checkout_id = ? ORDER BY created_at DESC LIMIT 1",
            (rs, rowNum) -> rs.getObject("id", UUID.class), checkoutId
        ).stream().findFirst().map(this::loadPaymentOrder);
    }

    private Optional<PaymentOrderResponse> findByProviderOrderId(String providerName, String orderId) {
        return jdbcTemplate.query(
            "SELECT id FROM payment_schema.payment_order WHERE (provider = ? AND provider_order_id = ?) OR craves_payment_order_ref = ?",
            (rs, rowNum) -> rs.getObject("id", UUID.class), providerName, orderId, orderId
        ).stream().findFirst().map(this::loadPaymentOrder);
    }

    private Optional<PaymentOrderResponse> findByProviderOrderId(String orderId) {
        return findByProviderOrderId("CASHFREE", orderId);
    }

    private CreatePaymentOrderResponse toCreateResponse(PaymentOrderResponse existing) {
        String paymentSessionId = jdbcTemplate.query(
            "SELECT payment_session_id FROM payment_schema.payment_order WHERE id = ?",
            (rs, rowNum) -> rs.getString("payment_session_id"), existing.paymentOrderId()
        ).stream().findFirst().orElse(null);
        String checkoutKeyId = jdbcTemplate.query(
            "SELECT checkout_key_id FROM payment_schema.payment_order WHERE id = ?",
            (rs, rowNum) -> rs.getString("checkout_key_id"), existing.paymentOrderId()
        ).stream().findFirst().orElse(null);
        return new CreatePaymentOrderResponse(
            existing.paymentOrderId(), existing.checkoutId(), existing.cravesPaymentOrderRef(),
            existing.provider(), existing.providerOrderId(), existing.providerPaymentId(), checkoutKeyId,
            paymentSessionId, existing.amount(),
            existing.currency(), existing.status(), existing.createdAt()
        );
    }

    private void notifyOrderPaid(UUID checkoutId, PaymentOrderResponse order, String cfPaymentId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("paymentOrderId", order.paymentOrderId());
        body.put("provider", order.provider());
        body.put("providerOrderId", order.providerOrderId());
        body.put("providerPaymentId", cfPaymentId);
        internalOrderClient.post()
            .uri("/payments/checkout/{checkoutId}/paid", checkoutId)
            .header("X-Craves-Internal-" + "Secret", orderClientProperties.internalKey())
            .body(body)
            .retrieve()
            .toBodilessEntity();
    }

    private boolean verifySignature(String timestamp, String signature, String rawBody) {
        try {
            if (!StringUtils.hasText(timestamp)
                || !StringUtils.hasText(signature)
                || !StringUtils.hasText(provider.clientKey())) {
                return false;
            }
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(provider.clientKey().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            String generated = Base64.getEncoder().encodeToString(
                mac.doFinal((timestamp + rawBody).getBytes(StandardCharsets.UTF_8))
            );
            return MessageDigest.isEqual(
                generated.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception ex) {
            return false;
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    private static String firstText(JsonNode node, String... pointers) {
        for (String pointer : pointers) {
            JsonNode value = node.at(pointer);
            if (!value.isMissingNode() && !value.isNull()) {
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
        } catch (Exception ex) {
            return null;
        }
    }

    private static long longValue(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        if (value == null || !value.canConvertToLong()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Razorpay webhook is missing " + field);
        }
        return value.longValue();
    }

    private static String hash(String value) {
        try {
            return Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(
                    String.valueOf(value).getBytes(StandardCharsets.UTF_8)
                )
            );
        } catch (Exception ex) {
            return null;
        }
    }

    private static String safe(String value) {
        if (value == null) {
            return null;
        }
        return value.length() > 500 ? value.substring(0, 500) : value;
    }

    public record CheckoutResponse(
        UUID id,
        UUID customerIdentityId,
        String status,
        String currency,
        BigDecimal grandTotal
    ) {
    }
}
