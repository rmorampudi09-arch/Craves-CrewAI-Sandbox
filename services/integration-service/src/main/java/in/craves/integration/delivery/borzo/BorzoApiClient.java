package in.craves.integration.delivery.borzo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.craves.integration.config.BorzoProperties;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.CreateReconciliationResult;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.ProviderCreateUncertainException;
import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

@Component
public class BorzoApiClient implements DeliveryProviderAdapter {
    static final String PROVIDER_ID = "borzo";
    static final String AUTH_HEADER = "X-DV-Auth-Token";
    private static final int MOTORBIKE_VEHICLE_TYPE_ID = 8;
    private static final int MOTORBIKE_MAX_WEIGHT_GRAMS = 20_000;
    private static final DateTimeFormatter BORZO_TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssXXX");

    private final BorzoProperties properties;
    private final ObjectMapper objectMapper;
    private final BorzoStatusMapper statusMapper;
    private final RestClient restClient;

    @Autowired
    public BorzoApiClient(BorzoProperties properties,
                          ObjectMapper objectMapper,
                          BorzoStatusMapper statusMapper,
                          RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.statusMapper = statusMapper;

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Math.multiplyExact(properties.getConnectTimeoutSeconds(), 1000));
        requestFactory.setReadTimeout(Math.multiplyExact(properties.getReadTimeoutSeconds(), 1000));

        RestClient.Builder configured = restClientBuilder.requestFactory(requestFactory);
        if (StringUtils.hasText(properties.getAuthToken())) {
            configured.defaultHeader(AUTH_HEADER, properties.getAuthToken());
        }
        this.restClient = configured.build();
    }

    BorzoApiClient(BorzoProperties properties,
                   ObjectMapper objectMapper,
                   BorzoStatusMapper statusMapper,
                   RestClient restClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.statusMapper = statusMapper;
        this.restClient = restClient;
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public ProviderQuote quote(QuoteRequest request) {
        requireApiReady();
        validateQuoteRequest(request);
        JsonNode response = post("/calculate-order", buildOrderRequest(request, null));
        requireSuccessful(response, "Borzo quote");

        JsonNode order = response.path("order");
        List<String> warnings = readWarnings(response);
        boolean hasParameterWarnings = response.path("parameter_warnings").isObject()
            && !response.path("parameter_warnings").isEmpty();
        if (hasParameterWarnings) {
            warnings.add("parameter_warnings=" + response.path("parameter_warnings"));
        }

        return new ProviderQuote(
            PROVIDER_ID,
            warnings.isEmpty(),
            money(order, "payment_amount"),
            money(order, "delivery_fee_amount"),
            "INR",
            List.copyOf(warnings),
            order.deepCopy(),
            Instant.now()
        );
    }

    @Override
    public ProviderDelivery create(CreateDeliveryRequest request) {
        requireApiReady();
        Objects.requireNonNull(request, "request is required");
        validateClientReference(request.clientReference());
        validateQuoteRequest(request.quoteRequest());

        Instant attemptedAt = Instant.now();
        JsonNode response;
        try {
            response = post(
                "/create-order",
                buildOrderRequest(request.quoteRequest(), request.clientReference())
            );
        } catch (BorzoApiException ex) {
            if (ex.getCause() instanceof ResourceAccessException) {
                throw new ProviderCreateUncertainException(
                    PROVIDER_ID,
                    request.clientReference(),
                    attemptedAt,
                    ex
                );
            }
            throw ex;
        }
        requireSuccessful(response, "Borzo create-order");
        return mapOrder(response.path("order"));
    }

    @Override
    public CreateReconciliationResult reconcileCreate(String clientReference, Instant notBefore) {
        requireApiReady();
        validateClientReference(clientReference);
        Objects.requireNonNull(notBefore, "notBefore is required");

        int pageSize = properties.getReconciliationPageSize();
        int offset = 0;
        Instant lowerBound = notBefore.minusSeconds(properties.getReconciliationLookbackSeconds());

        try {
            for (int page = 0; page < properties.getReconciliationMaxPages(); page++) {
                JsonNode response = getOrdersPage(offset, pageSize);
                requireSuccessful(response, "Borzo create reconciliation");

                JsonNode orders = response.path("orders");
                if (!orders.isArray()) {
                    return CreateReconciliationResult.inconclusive(
                        "Borzo orders response did not contain an orders array"
                    );
                }

                Instant oldestCreatedAt = null;
                for (JsonNode order : orders) {
                    if (hasClientReference(order, clientReference)) {
                        return CreateReconciliationResult.found(mapOrder(order));
                    }
                    Instant createdAt = orderCreatedAt(order);
                    if (createdAt != null && (oldestCreatedAt == null || createdAt.isBefore(oldestCreatedAt))) {
                        oldestCreatedAt = createdAt;
                    }
                }

                int returned = orders.size();
                int ordersCount = response.path("orders_count").asInt(-1);
                offset += returned;

                if (returned == 0 || (ordersCount >= 0 && offset >= ordersCount)) {
                    return CreateReconciliationResult.notFound(
                        "All Borzo orders in the bounded result set were checked"
                    );
                }
                if (oldestCreatedAt != null && oldestCreatedAt.isBefore(lowerBound)) {
                    return CreateReconciliationResult.notFound(
                        "Borzo results crossed the uncertain create time window"
                    );
                }
            }
            return CreateReconciliationResult.inconclusive(
                "Borzo reconciliation reached the configured page limit"
            );
        } catch (BorzoApiException ex) {
            return CreateReconciliationResult.inconclusive(safeMessage(ex));
        }
    }

    @Override
    public ProviderDelivery cancel(String providerDeliveryId) {
        requireApiReady();
        long orderId = parseOrderId(providerDeliveryId);
        ObjectNode body = objectMapper.createObjectNode().put("order_id", orderId);
        JsonNode response = post("/cancel-order", body);
        requireSuccessful(response, "Borzo cancel-order");
        return mapOrder(response.path("order"));
    }

    @Override
    public TrackingSnapshot track(String providerDeliveryId) {
        requireApiReady();
        long orderId = parseOrderId(providerDeliveryId);
        JsonNode ordersResponse = get("/orders", orderId);
        requireSuccessful(ordersResponse, "Borzo orders");
        JsonNode orders = ordersResponse.path("orders");
        if (!orders.isArray() || orders.isEmpty()) {
            throw new BorzoApiException(null, "Borzo order was not found", null);
        }

        ProviderDelivery delivery = mapOrder(orders.get(0));
        JsonNode courierResponse = get("/courier", orderId);
        requireSuccessful(courierResponse, "Borzo courier");
        Courier courier = mapCourier(courierResponse.path("courier"));
        return new TrackingSnapshot(delivery, courier, Instant.now());
    }

    private ObjectNode buildOrderRequest(QuoteRequest request, String clientReference) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "standard");
        root.put("matter", request.matter());
        root.put("vehicle_type_id", MOTORBIKE_VEHICLE_TYPE_ID);
        root.put("total_weight_kg", toBorzoWeightKg(request.totalWeightGrams()));
        root.put("is_thermobox_required", request.thermoboxRequired());
        root.put("is_client_notification_enabled", false);
        root.put("is_contact_person_notification_enabled", false);

        ArrayNode points = root.putArray("points");
        points.add(buildPoint(request.pickup(), null));
        points.add(buildPoint(request.dropoff(), clientReference));
        return root;
    }

    private ObjectNode buildPoint(Stop stop, String clientReference) {
        ObjectNode point = objectMapper.createObjectNode();
        point.put("address", stop.address());

        ObjectNode contact = point.putObject("contact_person");
        contact.put("phone", stop.contactPhone());
        putIfText(contact, "name", stop.contactName());

        putIfText(point, "client_order_id", clientReference);
        if (stop.latitude() != null) {
            point.put("latitude", stop.latitude().toPlainString());
        }
        if (stop.longitude() != null) {
            point.put("longitude", stop.longitude().toPlainString());
        }
        if (stop.requiredStart() != null) {
            point.put("required_start_datetime", BORZO_TIMESTAMP.format(stop.requiredStart()));
        }
        if (stop.requiredFinish() != null) {
            point.put("required_finish_datetime", BORZO_TIMESTAMP.format(stop.requiredFinish()));
        }
        putIfText(point, "note", stop.note());
        return point;
    }

    private ProviderDelivery mapOrder(JsonNode order) {
        if (!order.isObject()) {
            throw new BorzoApiException(null, "Borzo response did not contain order data", null);
        }
        String orderId = order.path("order_id").asText(null);
        if (!StringUtils.hasText(orderId)) {
            throw new BorzoApiException(null, "Borzo response did not contain order_id", order.toString());
        }
        return new ProviderDelivery(
            PROVIDER_ID,
            orderId,
            textOrNull(order, "order_name"),
            statusMapper.fromOrder(order),
            providerStatus(order),
            money(order, "payment_amount"),
            money(order, "delivery_fee_amount"),
            trackingUrl(order),
            order.deepCopy(),
            Instant.now()
        );
    }

    private Courier mapCourier(JsonNode courier) {
        if (courier == null || courier.isMissingNode() || courier.isNull() || !courier.isObject()) {
            return null;
        }
        String fullName = Stream.of(
                textOrNull(courier, "name"),
                textOrNull(courier, "middlename"),
                textOrNull(courier, "surname")
            )
            .filter(StringUtils::hasText)
            .reduce((left, right) -> left + " " + right)
            .orElse(null);

        return new Courier(
            textOrNull(courier, "courier_id"),
            fullName,
            textOrNull(courier, "phone"),
            textOrNull(courier, "photo_url"),
            decimalOrNull(courier.path("latitude").asText(null)),
            decimalOrNull(courier.path("longitude").asText(null))
        );
    }

    private JsonNode post(String path, JsonNode body) {
        try {
            JsonNode response = restClient.post()
                .uri(endpoint(path))
                .body(body)
                .retrieve()
                .body(JsonNode.class);
            return requireBody(response);
        } catch (RestClientResponseException ex) {
            throw new BorzoApiException(ex.getStatusCode(),
                "Borzo returned HTTP " + ex.getStatusCode().value(), ex.getResponseBodyAsString(), ex);
        } catch (ResourceAccessException ex) {
            throw new BorzoApiException(null, "Borzo API could not be reached", null, ex);
        }
    }

    private JsonNode get(String path, long orderId) {
        UriComponentsBuilder uriBuilder = UriComponentsBuilder.fromUriString(properties.normalizedBaseUrl())
            .path(path)
            .queryParam("order_id", orderId);
        if ("/orders".equals(path)) {
            uriBuilder.queryParam("count", 1);
        }
        return get(uriBuilder.build(true).toUri());
    }

    private JsonNode getOrdersPage(int offset, int count) {
        URI uri = UriComponentsBuilder.fromUriString(properties.normalizedBaseUrl())
            .path("/orders")
            .queryParam("offset", offset)
            .queryParam("count", count)
            .build(true)
            .toUri();
        return get(uri);
    }

    private JsonNode get(URI uri) {
        try {
            JsonNode response = restClient.get()
                .uri(uri)
                .retrieve()
                .body(JsonNode.class);
            return requireBody(response);
        } catch (RestClientResponseException ex) {
            throw new BorzoApiException(ex.getStatusCode(),
                "Borzo returned HTTP " + ex.getStatusCode().value(), ex.getResponseBodyAsString(), ex);
        } catch (ResourceAccessException ex) {
            throw new BorzoApiException(null, "Borzo API could not be reached", null, ex);
        }
    }

    private URI endpoint(String path) {
        return URI.create(properties.normalizedBaseUrl() + path);
    }

    private void requireApiReady() {
        if (!properties.isEnabled()) {
            throw new BorzoApiException(null, "Borzo API integration is disabled", null);
        }
        if (!StringUtils.hasText(properties.getAuthToken())) {
            throw new BorzoApiException(null, "Borzo API auth token is not configured", null);
        }
    }

    private static void validateQuoteRequest(QuoteRequest request) {
        Objects.requireNonNull(request, "quote request is required");
        if (!StringUtils.hasText(request.matter())) {
            throw new IllegalArgumentException("Delivery matter is required");
        }
        if (request.totalWeightGrams() <= 0 || request.totalWeightGrams() > MOTORBIKE_MAX_WEIGHT_GRAMS) {
            throw new IllegalArgumentException(
                "Borzo motorbike delivery weight must be between 1 and 20000 grams"
            );
        }
        validateStop(request.pickup(), "pickup");
        validateStop(request.dropoff(), "dropoff");
    }

    private static int toBorzoWeightKg(int totalWeightGrams) {
        return Math.max(1, (totalWeightGrams + 999) / 1000);
    }

    private static void validateStop(Stop stop, String name) {
        Objects.requireNonNull(stop, name + " stop is required");
        if (!StringUtils.hasText(stop.address())) {
            throw new IllegalArgumentException(name + " address is required");
        }
        if (!StringUtils.hasText(stop.contactPhone())) {
            throw new IllegalArgumentException(name + " contact phone is required");
        }
        if ((stop.latitude() == null) != (stop.longitude() == null)) {
            throw new IllegalArgumentException(name + " latitude and longitude must be supplied together");
        }
        if (stop.requiredStart() != null && stop.requiredFinish() != null
            && stop.requiredFinish().isBefore(stop.requiredStart())) {
            throw new IllegalArgumentException(name + " requiredFinish cannot be before requiredStart");
        }
    }

    private static void validateClientReference(String clientReference) {
        if (!StringUtils.hasText(clientReference)) {
            throw new IllegalArgumentException("clientReference is required");
        }
        if (clientReference.length() > 32) {
            throw new IllegalArgumentException("Borzo clientReference cannot exceed 32 characters");
        }
    }

    private static void requireSuccessful(JsonNode response, String operation) {
        if (!response.path("is_successful").asBoolean(false)) {
            throw new BorzoApiException(null, operation + " was rejected", response.toString());
        }
    }

    private static JsonNode requireBody(JsonNode response) {
        if (response == null) {
            throw new BorzoApiException(null, "Borzo returned an empty response", null);
        }
        return response;
    }

    private static List<String> readWarnings(JsonNode response) {
        List<String> warnings = new ArrayList<>();
        JsonNode values = response.path("warnings");
        if (values.isArray()) {
            values.forEach(value -> warnings.add(value.asText()));
        }
        return warnings;
    }

    private static boolean hasClientReference(JsonNode order, String clientReference) {
        JsonNode points = order.path("points");
        if (!points.isArray()) {
            return false;
        }
        for (JsonNode point : points) {
            if (clientReference.equals(point.path("client_order_id").asText(null))) {
                return true;
            }
        }
        return false;
    }

    private static Instant orderCreatedAt(JsonNode order) {
        String value = order.path("created_datetime").asText(null);
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(value).toInstant();
        } catch (DateTimeParseException ignored) {
            return null;
        }
    }

    private static String providerStatus(JsonNode order) {
        JsonNode points = order.path("points");
        if (points.isArray()) {
            for (int index = points.size() - 1; index >= 0; index--) {
                String status = points.path(index).path("delivery").path("status").asText(null);
                if (StringUtils.hasText(status)) {
                    return status;
                }
            }
        }
        return textOrNull(order, "status");
    }

    private static String trackingUrl(JsonNode order) {
        JsonNode points = order.path("points");
        if (points.isArray()) {
            for (int index = points.size() - 1; index >= 0; index--) {
                String trackingUrl = points.path(index).path("tracking_url").asText(null);
                if (StringUtils.hasText(trackingUrl)) {
                    return trackingUrl;
                }
            }
        }
        return null;
    }

    private static BigDecimal money(JsonNode object, String field) {
        return decimalOrNull(object.path(field).asText(null));
    }

    private static BigDecimal decimalOrNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static String textOrNull(JsonNode object, String field) {
        JsonNode value = object.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText(null);
        return StringUtils.hasText(text) ? text : null;
    }

    private static void putIfText(ObjectNode node, String field, String value) {
        if (StringUtils.hasText(value)) {
            node.put(field, value);
        }
    }

    private static long parseOrderId(String providerDeliveryId) {
        if (!StringUtils.hasText(providerDeliveryId)
            || !providerDeliveryId.trim().matches("[0-9]+")) {
            throw new IllegalArgumentException("Borzo providerDeliveryId must be numeric");
        }
        try {
            return Long.parseLong(providerDeliveryId.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Borzo providerDeliveryId is outside the supported range", ex);
        }
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (!StringUtils.hasText(message)) {
            return error.getClass().getSimpleName();
        }
        String normalized = message.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() <= 500 ? normalized : normalized.substring(0, 500);
    }

    public static class BorzoApiException extends RuntimeException {
        private final HttpStatusCode providerStatus;
        private final String safeProviderResponse;

        BorzoApiException(HttpStatusCode providerStatus, String message, String safeProviderResponse) {
            this(providerStatus, message, safeProviderResponse, null);
        }

        BorzoApiException(HttpStatusCode providerStatus,
                          String message,
                          String safeProviderResponse,
                          Throwable cause) {
            super(message, cause);
            this.providerStatus = providerStatus;
            this.safeProviderResponse = safeProviderResponse;
        }

        public HttpStatusCode getProviderStatus() {
            return providerStatus;
        }

        public String getSafeProviderResponse() {
            return safeProviderResponse;
        }
    }
}
