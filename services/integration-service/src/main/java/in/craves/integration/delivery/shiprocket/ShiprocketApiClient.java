package in.craves.integration.delivery.shiprocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.craves.integration.config.ShiprocketProperties;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.CreateDeliveryRequest;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.CreateReconciliationResult;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.DeliveryStatus;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.ProviderCreateUncertainException;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.ProviderDelivery;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.ProviderQuote;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.QuoteRequest;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.ShipmentItem;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.Stop;
import in.craves.integration.delivery.provider.DeliveryProviderPickupLocationRepository;
import in.craves.integration.delivery.shiprocket.ShiprocketTransport.ShiprocketApiException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@ConditionalOnProperty(prefix = "craves.providers.shiprocket", name = "enabled", havingValue = "true")
public class ShiprocketApiClient implements DeliveryProviderAdapter {
    public static final String PROVIDER_ID = "shiprocket";
    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter ORDER_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final Pattern EXPLICIT_ETA = Pattern.compile(
        "(?i)(\\d+(?:\\.\\d+)?)\\s*(minutes?|mins?|hours?|hrs?)"
    );

    private final ShiprocketProperties properties;
    private final ShiprocketTransport transport;
    private final DeliveryProviderPickupLocationRepository pickupLocations;
    private final ObjectMapper objectMapper;

    public ShiprocketApiClient(ShiprocketProperties properties,
                               ShiprocketTransport transport,
                               DeliveryProviderPickupLocationRepository pickupLocations,
                               ObjectMapper objectMapper) {
        this.properties = properties;
        this.transport = transport;
        this.pickupLocations = pickupLocations;
        this.objectMapper = objectMapper;
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    /**
     * Read-only hyperlocal serviceability probe used by readiness checks. It never creates or
     * updates Shiprocket account data.
     */
    public ProviderQuote readOnlyQuote(QuoteRequest request) {
        validateServiceabilityRequest(request);
        Map<String, String> query = new LinkedHashMap<>();
        query.put("pickup_postcode", request.pickup().postalCode());
        query.put("delivery_postcode", request.dropoff().postalCode());
        query.put("cod", "0");
        query.put("weight", kilograms(request.totalWeightGrams()).toPlainString());
        query.put("only_local", "1");
        if (request.declaredGoodsValue() != null && request.declaredGoodsValue().signum() > 0) {
            query.put("declared_value", request.declaredGoodsValue().setScale(2, RoundingMode.HALF_UP).toPlainString());
        }
        if (positive(properties.getPackageLengthCm())) {
            query.put("length", properties.getPackageLengthCm().stripTrailingZeros().toPlainString());
            query.put("breadth", properties.getPackageBreadthCm().stripTrailingZeros().toPlainString());
            query.put("height", properties.getPackageHeightCm().stripTrailingZeros().toPlainString());
        }

        JsonNode response = transport.get("/courier/serviceability/", query);
        JsonNode data = response.path("data");
        JsonNode couriers = data.path("available_courier_companies");
        List<String> warnings = new ArrayList<>();
        if (!couriers.isArray() || couriers.isEmpty()) {
            warnings.add("Shiprocket returned no hyperlocal courier for this route");
            return unavailableQuote(warnings, response);
        }

        JsonNode selected = deterministicCourier(data, couriers, warnings);
        if (selected == null) {
            return unavailableQuote(warnings, response);
        }

        Double etaMinutes = explicitEtaMinutes(selected);
        if (etaMinutes == null) {
            warnings.add("Shiprocket did not return an explicit minute/hour ETA that Craves can validate");
            return unavailableQuote(warnings, selected);
        }
        if (etaMinutes > properties.getMaximumAcceptedEtaMinutes()) {
            warnings.add(
                "Shiprocket hyperlocal ETA " + etaMinutes + " exceeds Craves maximum "
                    + properties.getMaximumAcceptedEtaMinutes() + " minutes"
            );
            return unavailableQuote(warnings, selected);
        }

        Integer courierId = integerField(selected, "courier_company_id", "courier_id");
        if (courierId == null || courierId <= 0) {
            warnings.add("Shiprocket selected courier did not contain a courier id");
            return unavailableQuote(warnings, selected);
        }
        BigDecimal rate = decimalField(selected, "rate", "freight_charge");
        if (rate == null || rate.signum() < 0) {
            warnings.add("Shiprocket selected courier did not contain a usable delivery rate");
            return unavailableQuote(warnings, selected);
        }

        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("quote_id", "shiprocket:" + courierId + ":" + rate.toPlainString());
        metadata.put("courier_company_id", courierId);
        metadata.put("courier_name", textField(selected, "courier_name", "courier_company_name"));
        metadata.put("delivery_eta_minutes", etaMinutes);
        metadata.put("hyperlocal", true);
        metadata.set("provider_quote", selected.deepCopy());

        return new ProviderQuote(
            PROVIDER_ID,
            true,
            request.declaredGoodsValue(),
            rate,
            "INR",
            List.copyOf(warnings),
            metadata,
            Instant.now()
        );
    }

    @Override
    public ProviderQuote quote(QuoteRequest request) {
        ProviderQuote readOnly = readOnlyQuote(request);
        if (!readOnly.available()) {
            return readOnly;
        }
        List<String> warnings = new ArrayList<>(readOnly.warnings());
        if (!properties.isCreateEnabled()) {
            warnings.add("Shiprocket create is disabled; quote retained for readiness only");
            return new ProviderQuote(
                PROVIDER_ID,
                false,
                readOnly.paymentAmount(),
                readOnly.deliveryFeeAmount(),
                readOnly.currency(),
                List.copyOf(warnings),
                readOnly.providerMetadata(),
                readOnly.quotedAt()
            );
        }
        if (request.pickupLocationReference() == null) {
            warnings.add("Craves kitchen pickup reference is missing");
            return unavailableQuote(warnings, readOnly.providerMetadata());
        }
        if (!pickupLocations.isVerified(PROVIDER_ID, request.pickupLocationReference())) {
            warnings.add("No verified Shiprocket pickup location is mapped to this kitchen");
            return unavailableQuote(warnings, readOnly.providerMetadata());
        }
        return readOnly;
    }

    @Override
    public ProviderDelivery create(CreateDeliveryRequest request) {
        Objects.requireNonNull(request, "request is required");
        if (!properties.productionCreateReady()) {
            throw new ShiprocketApiException(
                null,
                "Shiprocket create is blocked by production readiness gates",
                false
            );
        }
        QuoteRequest quoteRequest = Objects.requireNonNull(request.quoteRequest(), "quoteRequest is required");
        validateCreateRequest(quoteRequest);
        ProviderQuote selectedQuote = Objects.requireNonNull(
            request.selectedQuote(),
            "Shiprocket create requires the exact selected quote"
        );
        if (!selectedQuote.available()) {
            throw new IllegalArgumentException("Shiprocket selected quote is not available");
        }
        Integer courierId = metadataInteger(selectedQuote, "courier_company_id");
        if (courierId == null || courierId <= 0) {
            throw new IllegalArgumentException("Shiprocket selected quote has no courier_company_id");
        }
        UUID pickupReference = Objects.requireNonNull(
            quoteRequest.pickupLocationReference(),
            "Shiprocket pickupLocationReference is required"
        );
        String pickupLocation = pickupLocations.findVerifiedExternalLocation(PROVIDER_ID, pickupReference)
            .orElseThrow(() -> new IllegalStateException(
                "No verified Shiprocket pickup location is mapped to this kitchen"
            ));

        String clientReference = requireText(request.clientReference(), "clientReference");
        Instant attemptedAt = Instant.now();
        JsonNode response;
        try {
            response = transport.mutate(
                "/shipments/create/forward-shipment",
                buildForwardShipment(clientReference, quoteRequest, pickupLocation, courierId)
            );
        } catch (ShiprocketApiException ex) {
            if (ex.uncertainMutation()) {
                throw new ProviderCreateUncertainException(PROVIDER_ID, clientReference, attemptedAt, ex);
            }
            throw ex;
        }

        String awb = recursiveText(response, "awb_code", "awb").orElse(null);
        if (!StringUtils.hasText(awb)) {
            throw new ProviderCreateUncertainException(
                PROVIDER_ID,
                clientReference,
                attemptedAt,
                new IllegalStateException("Shiprocket create returned no AWB; reconciliation is required")
            );
        }
        return mapCreatedDelivery(clientReference, awb, selectedQuote, response);
    }

    @Override
    public CreateReconciliationResult reconcileCreate(String clientReference, Instant notBefore) {
        requireText(clientReference, "clientReference");
        Objects.requireNonNull(notBefore, "notBefore is required");
        try {
            JsonNode response = transport.get(
                "/orders",
                Map.of(
                    "filter_by", "channel_order_id",
                    "filter", clientReference,
                    "per_page", "20"
                )
            );
            JsonNode order = findOrderByClientReference(response, clientReference);
            if (order == null) {
                return CreateReconciliationResult.notFound(
                    "Shiprocket returned no order for the deterministic Craves source order id"
                );
            }
            String awb = recursiveText(order, "awb_code", "awb").orElse(null);
            if (!StringUtils.hasText(awb)) {
                return CreateReconciliationResult.inconclusive(
                    "Shiprocket order exists but has no AWB yet; fallback remains blocked"
                );
            }
            try {
                return CreateReconciliationResult.found(track(awb).delivery());
            } catch (RuntimeException trackingFailure) {
                ObjectNode metadata = objectMapper.createObjectNode();
                metadata.set("order", order.deepCopy());
                return CreateReconciliationResult.found(new ProviderDelivery(
                    PROVIDER_ID,
                    awb,
                    clientReference,
                    DeliveryStatus.COURIER_ASSIGNED,
                    "AWB_ASSIGNED",
                    null,
                    null,
                    null,
                    metadata,
                    Instant.now()
                ));
            }
        } catch (RuntimeException ex) {
            return CreateReconciliationResult.inconclusive(safeMessage(ex));
        }
    }

    @Override
    public ProviderDelivery cancel(String providerDeliveryId) {
        requireProductionMutationApproval("Shiprocket cancellation");
        String awb = requireText(providerDeliveryId, "providerDeliveryId");
        ObjectNode body = objectMapper.createObjectNode();
        ArrayNode awbs = body.putArray("awbs");
        awbs.add(awb);
        JsonNode response = transport.mutate("/orders/cancel/shipment/awbs", body);
        return new ProviderDelivery(
            PROVIDER_ID,
            awb,
            null,
            DeliveryStatus.CANCELLED,
            "CANCELLATION_REQUESTED",
            null,
            null,
            null,
            response.deepCopy(),
            Instant.now()
        );
    }

    @Override
    public DeliveryProviderAdapter.TrackingSnapshot track(String providerDeliveryId) {
        String awb = requireText(providerDeliveryId, "providerDeliveryId");
        JsonNode response = transport.get(
            "/courier/track/awb/" + URLEncoder.encode(awb, StandardCharsets.UTF_8),
            Map.of()
        );
        Integer statusCode = recursiveInteger(
            response,
            "shipment_status",
            "current_status_id",
            "status_code"
        ).orElse(null);
        String providerStatus = recursiveText(
            response,
            "current_status",
            "shipment_status_label",
            "status"
        ).orElse("UNKNOWN");
        DeliveryStatus status = ShiprocketStatusMapper.map(statusCode, providerStatus);
        String trackingUrl = recursiveText(response, "track_url", "tracking_url").orElse(null);

        ProviderDelivery delivery = new ProviderDelivery(
            PROVIDER_ID,
            awb,
            null,
            status,
            providerStatus,
            null,
            null,
            trackingUrl,
            response.deepCopy(),
            Instant.now()
        );
        return new DeliveryProviderAdapter.TrackingSnapshot(delivery, null, Instant.now());
    }

    private JsonNode deterministicCourier(JsonNode data, JsonNode couriers, List<String> warnings) {
        Integer recommended = integerField(
            data,
            "recommended_courier_company_id",
            "shiprocket_recommended_courier_id"
        );
        if (recommended != null && recommended > 0) {
            for (JsonNode courier : couriers) {
                Integer id = integerField(courier, "courier_company_id", "courier_id");
                if (recommended.equals(id)) {
                    return courier;
                }
            }
            warnings.add("Shiprocket recommended courier id was not present in the hyperlocal result set");
            return null;
        }
        if (couriers.size() == 1) {
            warnings.add("Shiprocket returned one hyperlocal courier; no Craves intra-provider preference was applied");
            return couriers.get(0);
        }
        warnings.add(
            "Shiprocket returned multiple hyperlocal couriers without a deterministic provider recommendation; Craves will not invent an intra-provider selection rule"
        );
        return null;
    }

    private ObjectNode buildForwardShipment(String clientReference,
                                            QuoteRequest request,
                                            String pickupLocation,
                                            int courierId) {
        Stop dropoff = request.dropoff();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("request_pickup", true);
        body.put("print_label", false);
        body.put("generate_manifest", false);
        body.put("courier_id", courierId);
        body.put("order_id", clientReference);
        body.put("order_date", ORDER_DATE.format(ZonedDateTime.now(INDIA)));
        body.put("pickup_location", pickupLocation);
        body.put("billing_customer_name", requireText(dropoff.contactName(), "dropoff contactName"));
        body.put("billing_address", requireText(primaryAddress(dropoff), "dropoff address"));
        body.put("billing_address_2", secondaryAddress(dropoff));
        body.put("billing_city", requireText(dropoff.city(), "dropoff city"));
        body.put("billing_pincode", requirePincode(dropoff.postalCode(), "dropoff postalCode"));
        body.put("billing_state", requireText(dropoff.state(), "dropoff state"));
        body.put("billing_country", StringUtils.hasText(dropoff.country()) ? dropoff.country().trim() : "India");
        body.put("billing_email", requireText(properties.getOrderEmail(), "SHIPROCKET_ORDER_EMAIL"));
        body.put("billing_phone", requirePhone(dropoff.contactPhone()));
        body.put("shipping_is_billing", true);
        if (dropoff.longitude() != null) {
            body.put("longitude", dropoff.longitude());
        }
        if (dropoff.latitude() != null) {
            body.put("latitude", dropoff.latitude());
        }

        ArrayNode items = body.putArray("order_items");
        for (ShipmentItem item : request.items()) {
            ObjectNode row = items.addObject();
            row.put("name", requireText(item.itemName(), "shipment item name"));
            row.put("sku", item.menuItemId() == null
                ? "craves-" + Math.abs(item.itemName().hashCode())
                : item.menuItemId().toString());
            row.put("units", item.quantity());
            row.put("selling_price", requirePositive(item.unitPrice(), "shipment item unitPrice"));
        }
        body.put("payment_method", "Prepaid");
        body.put("shipping_charges", 0);
        body.put("giftwrap_charges", 0);
        body.put("transaction_charges", 0);
        body.put("total_discount", 0);
        body.put("sub_total", requirePositive(request.declaredGoodsValue(), "declaredGoodsValue"));
        body.put("length", properties.getPackageLengthCm());
        body.put("breadth", properties.getPackageBreadthCm());
        body.put("height", properties.getPackageHeightCm());
        body.put("weight", kilograms(request.totalWeightGrams()));
        return body;
    }

    private ProviderDelivery mapCreatedDelivery(String clientReference,
                                                String awb,
                                                ProviderQuote quote,
                                                JsonNode response) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("client_reference", clientReference);
        recursiveText(response, "shipment_id").ifPresent(value -> metadata.put("shipment_id", value));
        recursiveText(response, "order_id").ifPresent(value -> metadata.put("shiprocket_order_id", value));
        metadata.set("create_response", response.deepCopy());
        return new ProviderDelivery(
            PROVIDER_ID,
            awb,
            clientReference,
            DeliveryStatus.COURIER_ASSIGNED,
            "AWB_ASSIGNED",
            quote.paymentAmount(),
            quote.deliveryFeeAmount(),
            recursiveText(response, "track_url", "tracking_url").orElse(null),
            metadata,
            Instant.now()
        );
    }

    private void validateServiceabilityRequest(QuoteRequest request) {
        Objects.requireNonNull(request, "quote request is required");
        if (request.totalWeightGrams() <= 0) {
            throw new IllegalArgumentException("Shiprocket totalWeightGrams must be positive");
        }
        Objects.requireNonNull(request.pickup(), "pickup is required");
        Objects.requireNonNull(request.dropoff(), "dropoff is required");
        requirePincode(request.pickup().postalCode(), "pickup postalCode");
        requirePincode(request.dropoff().postalCode(), "dropoff postalCode");
        if (request.paymentCollectionMode() != null
            && !"PREPAID".equalsIgnoreCase(request.paymentCollectionMode())) {
            throw new IllegalArgumentException("Shiprocket Craves integration currently accepts prepaid orders only");
        }
    }

    private void validateCreateRequest(QuoteRequest request) {
        validateServiceabilityRequest(request);
        if (request.pickupLocationReference() == null) {
            throw new IllegalArgumentException("Shiprocket pickupLocationReference is required");
        }
        if (request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("Shiprocket create requires immutable order item snapshots");
        }
        for (ShipmentItem item : request.items()) {
            if (item == null || item.quantity() <= 0 || item.unitPrice() == null || item.unitPrice().signum() < 0) {
                throw new IllegalArgumentException("Shiprocket create contains an invalid shipment item");
            }
        }
        requirePositive(request.declaredGoodsValue(), "declaredGoodsValue");
        Stop dropoff = request.dropoff();
        requireText(dropoff.contactName(), "dropoff contactName");
        requirePhone(dropoff.contactPhone());
        requireText(primaryAddress(dropoff), "dropoff address");
        requireText(dropoff.city(), "dropoff city");
        requireText(dropoff.state(), "dropoff state");
        requirePincode(dropoff.postalCode(), "dropoff postalCode");
    }

    private void requireProductionMutationApproval(String operation) {
        if (!"PRODUCTION".equals(properties.executionMode())
            || !properties.isProductionActivationApproved()) {
            throw new IllegalStateException(operation + " requires explicit production activation approval");
        }
    }

    private ProviderQuote unavailableQuote(List<String> warnings, JsonNode metadata) {
        return new ProviderQuote(
            PROVIDER_ID,
            false,
            null,
            null,
            "INR",
            List.copyOf(warnings),
            metadata == null ? objectMapper.createObjectNode() : metadata.deepCopy(),
            Instant.now()
        );
    }

    private static JsonNode findOrderByClientReference(JsonNode root, String clientReference) {
        if (root == null || root.isNull()) {
            return null;
        }
        if (root.isObject()) {
            JsonNode channelOrder = root.get("channel_order_id");
            if (channelOrder != null && clientReference.equals(channelOrder.asText())) {
                return root;
            }
            Iterator<JsonNode> children = root.elements();
            while (children.hasNext()) {
                JsonNode match = findOrderByClientReference(children.next(), clientReference);
                if (match != null) {
                    return match;
                }
            }
        } else if (root.isArray()) {
            for (JsonNode child : root) {
                JsonNode match = findOrderByClientReference(child, clientReference);
                if (match != null) {
                    return match;
                }
            }
        }
        return null;
    }

    private static Optional<String> recursiveText(JsonNode root, String... names) {
        if (root == null || root.isNull()) {
            return Optional.empty();
        }
        if (root.isObject()) {
            for (String name : names) {
                JsonNode value = root.get(name);
                if (value != null && !value.isNull() && !value.asText("").isBlank()) {
                    return Optional.of(value.asText());
                }
            }
            Iterator<JsonNode> children = root.elements();
            while (children.hasNext()) {
                Optional<String> value = recursiveText(children.next(), names);
                if (value.isPresent()) {
                    return value;
                }
            }
        } else if (root.isArray()) {
            for (JsonNode child : root) {
                Optional<String> value = recursiveText(child, names);
                if (value.isPresent()) {
                    return value;
                }
            }
        }
        return Optional.empty();
    }

    private static Optional<Integer> recursiveInteger(JsonNode root, String... names) {
        Optional<String> text = recursiveText(root, names);
        if (text.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.valueOf(text.get()));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private static Integer metadataInteger(ProviderQuote quote, String field) {
        JsonNode metadata = quote.providerMetadata();
        if (metadata == null || metadata.get(field) == null || metadata.get(field).isNull()) {
            return null;
        }
        return metadata.get(field).isNumber()
            ? metadata.get(field).asInt()
            : parseInteger(metadata.get(field).asText());
    }

    private static Integer integerField(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                if (value.isNumber()) {
                    return value.asInt();
                }
                Integer parsed = parseInteger(value.asText());
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return null;
    }

    private static Integer parseInteger(String value) {
        try {
            return Integer.valueOf(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static BigDecimal decimalField(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull()) {
                try {
                    return new BigDecimal(value.asText());
                } catch (NumberFormatException ignored) {
                    // try next field
                }
            }
        }
        return null;
    }

    private static String textField(JsonNode node, String... fields) {
        for (String field : fields) {
            JsonNode value = node.get(field);
            if (value != null && !value.isNull() && !value.asText("").isBlank()) {
                return value.asText();
            }
        }
        return "";
    }

    private static Double explicitEtaMinutes(JsonNode courier) {
        for (String numericField : new String[]{"pickup_eta_minutes", "estimated_delivery_minutes", "eta_minutes"}) {
            JsonNode value = courier.get(numericField);
            if (value != null && value.isNumber()) {
                return value.asDouble();
            }
        }
        JsonNode hours = courier.get("etd_hours");
        if (hours != null && hours.isNumber()) {
            return hours.asDouble() * 60.0d;
        }
        for (String field : new String[]{"etd", "estimated_delivery_time", "eta"}) {
            JsonNode value = courier.get(field);
            if (value == null || value.isNull()) {
                continue;
            }
            Matcher matcher = EXPLICIT_ETA.matcher(value.asText(""));
            if (matcher.find()) {
                double amount = Double.parseDouble(matcher.group(1));
                String unit = matcher.group(2).toLowerCase(Locale.ROOT);
                return unit.startsWith("hour") || unit.startsWith("hr") ? amount * 60.0d : amount;
            }
        }
        return null;
    }

    private static BigDecimal kilograms(int grams) {
        return BigDecimal.valueOf(grams)
            .divide(BigDecimal.valueOf(1000), 3, RoundingMode.UP)
            .stripTrailingZeros();
    }

    private static String primaryAddress(Stop stop) {
        return StringUtils.hasText(stop.addressLine1()) ? stop.addressLine1().trim() : stop.address();
    }

    private static String secondaryAddress(Stop stop) {
        List<String> parts = new ArrayList<>();
        for (String value : new String[]{stop.addressLine2(), stop.landmark(), stop.area()}) {
            if (StringUtils.hasText(value)) {
                parts.add(value.trim());
            }
        }
        return String.join(", ", parts);
    }

    private static String requirePincode(String value, String label) {
        String text = requireText(value, label);
        if (!text.matches("\\d{6}")) {
            throw new IllegalArgumentException(label + " must be a 6-digit Indian pincode");
        }
        return text;
    }

    private static String requirePhone(String value) {
        String digits = requireText(value, "dropoff contactPhone").replaceAll("\\D", "");
        if (digits.length() == 12 && digits.startsWith("91")) {
            digits = digits.substring(2);
        }
        if (digits.length() != 10) {
            throw new IllegalArgumentException("dropoff contactPhone must resolve to a 10-digit Indian number");
        }
        return digits;
    }

    private static String requireText(String value, String label) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalArgumentException(label + " is required");
        }
        return value.trim();
    }

    private static BigDecimal requirePositive(BigDecimal value, String label) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(label + " must be greater than zero");
        }
        return value;
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.signum() > 0;
    }

    private static String safeMessage(Throwable error) {
        String message = error.getMessage();
        if (message == null || message.isBlank()) {
            return error.getClass().getSimpleName();
        }
        return message.length() <= 500 ? message : message.substring(0, 500);
    }
}
