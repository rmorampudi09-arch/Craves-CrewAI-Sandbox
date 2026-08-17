package in.craves.integration.delivery.shiprocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.craves.integration.config.ShiprocketProperties;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.CreateDeliveryRequest;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.CreateReconciliationStatus;
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
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ShiprocketApiClientTest {
    private static final UUID KITCHEN_ID = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID MENU_ITEM_ID = UUID.fromString("22222222-2222-4222-8222-222222222222");

    private ShiprocketProperties properties;
    private ShiprocketTransport transport;
    private DeliveryProviderPickupLocationRepository pickupLocations;
    private ObjectMapper objectMapper;
    private ShiprocketApiClient client;

    @BeforeEach
    void setUp() {
        properties = new ShiprocketProperties();
        properties.setMaximumAcceptedEtaMinutes(60);
        transport = mock(ShiprocketTransport.class);
        pickupLocations = mock(DeliveryProviderPickupLocationRepository.class);
        objectMapper = new ObjectMapper();
        client = new ShiprocketApiClient(properties, transport, pickupLocations, objectMapper);
    }

    @Test
    void readOnlyQuoteUsesHyperlocalServiceabilityAndProviderRate() throws Exception {
        when(transport.get(eq("/courier/serviceability/"), anyMap()))
            .thenReturn(serviceabilityResponse(77, "rate", "42.50", 35));

        ProviderQuote quote = client.readOnlyQuote(request());

        assertThat(quote.available()).isTrue();
        assertThat(quote.deliveryFeeAmount()).isEqualByComparingTo("42.50");
        assertThat(quote.providerMetadata().path("courier_company_id").asInt()).isEqualTo(77);
        assertThat(quote.providerMetadata().path("delivery_eta_minutes").asDouble()).isEqualTo(35.0d);
        verify(transport).get(
            eq("/courier/serviceability/"),
            argThat(query ->
                "1".equals(query.get("only_local"))
                    && "0".equals(query.get("cod"))
                    && "500081".equals(query.get("pickup_postcode"))
                    && "500084".equals(query.get("delivery_postcode"))
            )
        );
    }

    @Test
    void chargeWeightIsNeverMisclassifiedAsDeliveryPrice() throws Exception {
        when(transport.get(eq("/courier/serviceability/"), anyMap()))
            .thenReturn(serviceabilityResponse(77, "charge_weight", "0.75", 25));

        ProviderQuote quote = client.readOnlyQuote(request());

        assertThat(quote.available()).isFalse();
        assertThat(quote.deliveryFeeAmount()).isNull();
        assertThat(quote.warnings()).contains("Shiprocket selected courier did not contain a usable delivery rate");
    }

    @Test
    void quoteRejectsEtaBeyondCravesOneHourPromise() throws Exception {
        when(transport.get(eq("/courier/serviceability/"), anyMap()))
            .thenReturn(serviceabilityResponse(77, "rate", "39.00", 75));

        ProviderQuote quote = client.readOnlyQuote(request());

        assertThat(quote.available()).isFalse();
        assertThat(quote.warnings()).anyMatch(value -> value.contains("exceeds Craves maximum 60 minutes"));
    }

    @Test
    void quoteRefusesToInventCourierSelectionWhenProviderReturnsMultipleWithoutRecommendation() throws Exception {
        JsonNode response = objectMapper.readTree("""
            {
              "data": {
                "available_courier_companies": [
                  {"courier_company_id": 10, "rate": 35, "eta_minutes": 20},
                  {"courier_company_id": 11, "rate": 30, "eta_minutes": 25}
                ]
              }
            }
            """);
        when(transport.get(eq("/courier/serviceability/"), anyMap())).thenReturn(response);

        ProviderQuote quote = client.readOnlyQuote(request());

        assertThat(quote.available()).isFalse();
        assertThat(quote.warnings()).anyMatch(value -> value.contains("will not invent an intra-provider selection rule"));
    }

    @Test
    void routableQuoteRequiresCreateEnabledAndVerifiedKitchenPickup() throws Exception {
        properties.setCreateEnabled(true);
        when(transport.get(eq("/courier/serviceability/"), anyMap()))
            .thenReturn(serviceabilityResponse(77, "freight_charge", "41.00", 30));
        when(pickupLocations.isVerified("shiprocket", KITCHEN_ID)).thenReturn(false, true);

        ProviderQuote blocked = client.quote(request());
        ProviderQuote allowed = client.quote(request());

        assertThat(blocked.available()).isFalse();
        assertThat(blocked.warnings()).contains("No verified Shiprocket pickup location is mapped to this kitchen");
        assertThat(allowed.available()).isTrue();
        assertThat(allowed.deliveryFeeAmount()).isEqualByComparingTo("41.00");
    }

    @Test
    void createUsesExactSelectedCourierVerifiedPickupAndImmutableOrderSnapshot() throws Exception {
        configureProductionCreate();
        QuoteRequest request = request();
        ProviderQuote selectedQuote = selectedQuote(77, "41.00");
        when(pickupLocations.findVerifiedExternalLocation("shiprocket", KITCHEN_ID))
            .thenReturn(Optional.of("craves-kitchen-hitech-city"));
        when(transport.mutate(eq("/shipments/create/forward-shipment"), any(JsonNode.class)))
            .thenReturn(objectMapper.readTree("""
                {"awb_code":"AWB123","shipment_id":"9001","order_id":"SR-1001","tracking_url":"https://tracking.example/AWB123"}
                """));

        ProviderDelivery delivery = client.create(
            new CreateDeliveryRequest("CRAVES-ORDER-123", request, selectedQuote)
        );

        assertThat(delivery.providerId()).isEqualTo("shiprocket");
        assertThat(delivery.providerDeliveryId()).isEqualTo("AWB123");
        assertThat(delivery.status()).isEqualTo(DeliveryStatus.COURIER_ASSIGNED);
        assertThat(delivery.deliveryFeeAmount()).isEqualByComparingTo("41.00");

        ArgumentCaptor<JsonNode> bodyCaptor = ArgumentCaptor.forClass(JsonNode.class);
        verify(transport).mutate(eq("/shipments/create/forward-shipment"), bodyCaptor.capture());
        JsonNode body = bodyCaptor.getValue();
        assertThat(body.path("order_id").asText()).isEqualTo("CRAVES-ORDER-123");
        assertThat(body.path("courier_id").asInt()).isEqualTo(77);
        assertThat(body.path("pickup_location").asText()).isEqualTo("craves-kitchen-hitech-city");
        assertThat(body.path("payment_method").asText()).isEqualTo("Prepaid");
        assertThat(body.path("billing_pincode").asText()).isEqualTo("500084");
        assertThat(body.path("billing_phone").asText()).isEqualTo("9988776655");
        assertThat(body.path("order_items").size()).isEqualTo(1);
        assertThat(body.path("order_items").get(0).path("sku").asText()).isEqualTo(MENU_ITEM_ID.toString());
        assertThat(body.path("order_items").get(0).path("units").asInt()).isEqualTo(2);
        assertThat(body.path("length").decimalValue()).isEqualByComparingTo("20");
        assertThat(body.path("breadth").decimalValue()).isEqualByComparingTo("15");
        assertThat(body.path("height").decimalValue()).isEqualByComparingTo("10");
    }

    @Test
    void createWithoutAwbIsTreatedAsUncertainAndRequiresReconciliation() throws Exception {
        configureProductionCreate();
        when(pickupLocations.findVerifiedExternalLocation("shiprocket", KITCHEN_ID))
            .thenReturn(Optional.of("craves-kitchen-hitech-city"));
        when(transport.mutate(eq("/shipments/create/forward-shipment"), any(JsonNode.class)))
            .thenReturn(objectMapper.readTree("{\"shipment_id\":\"9001\"}"));

        assertThatThrownBy(() -> client.create(
            new CreateDeliveryRequest("CRAVES-ORDER-123", request(), selectedQuote(77, "41.00"))
        ))
            .isInstanceOf(ProviderCreateUncertainException.class)
            .satisfies(error -> assertThat(((ProviderCreateUncertainException) error).clientReference())
                .isEqualTo("CRAVES-ORDER-123"));
    }

    @Test
    void uncertainProviderMutationIsWrappedForReconciliationBeforeFallback() {
        configureProductionCreate();
        when(pickupLocations.findVerifiedExternalLocation("shiprocket", KITCHEN_ID))
            .thenReturn(Optional.of("craves-kitchen-hitech-city"));
        when(transport.mutate(eq("/shipments/create/forward-shipment"), any(JsonNode.class)))
            .thenThrow(new ShiprocketApiException(503, "provider timeout", true));

        assertThatThrownBy(() -> client.create(
            new CreateDeliveryRequest("CRAVES-ORDER-123", request(), selectedQuote(77, "41.00"))
        ))
            .isInstanceOf(ProviderCreateUncertainException.class);
    }

    @Test
    void createReconciliationDistinguishesNotFoundFromExistingOrderWithoutAwb() throws Exception {
        JsonNode noOrder = objectMapper.readTree("{\"data\":[]}");
        JsonNode awaitingAwb = objectMapper.readTree("""
            {"data":[{"channel_order_id":"CRAVES-ORDER-123","id":9001}]}
            """);
        when(transport.get(eq("/orders"), anyMap())).thenReturn(noOrder, awaitingAwb);

        var absent = client.reconcileCreate("CRAVES-ORDER-123", Instant.now().minusSeconds(30));
        var pending = client.reconcileCreate("CRAVES-ORDER-123", Instant.now().minusSeconds(30));

        assertThat(absent.status()).isEqualTo(CreateReconciliationStatus.NOT_FOUND);
        assertThat(pending.status()).isEqualTo(CreateReconciliationStatus.INCONCLUSIVE);
        assertThat(pending.detail()).contains("no AWB yet");
    }

    @Test
    void cancelRequiresProductionApprovalAndSendsOnlyTheTargetAwb() throws Exception {
        properties.setEnvironment("PRODUCTION");
        properties.setProductionActivationApproved(true);
        when(transport.mutate(eq("/orders/cancel/shipment/awbs"), any(JsonNode.class)))
            .thenReturn(objectMapper.readTree("{\"status\":\"ok\"}"));

        ProviderDelivery cancelled = client.cancel("AWB123");

        assertThat(cancelled.status()).isEqualTo(DeliveryStatus.CANCELLED);
        ArgumentCaptor<JsonNode> bodyCaptor = ArgumentCaptor.forClass(JsonNode.class);
        verify(transport).mutate(eq("/orders/cancel/shipment/awbs"), bodyCaptor.capture());
        assertThat(bodyCaptor.getValue().path("awbs").size()).isEqualTo(1);
        assertThat(bodyCaptor.getValue().path("awbs").get(0).asText()).isEqualTo("AWB123");
    }

    @Test
    void trackingMapsProviderStatusIntoCanonicalDeliveryState() throws Exception {
        when(transport.get(eq("/courier/track/awb/AWB123"), eq(Map.of())))
            .thenReturn(objectMapper.readTree("""
                {
                  "tracking_data": {
                    "shipment_track": [
                      {"current_status":"IN TRANSIT", "current_status_id":18, "tracking_url":"https://tracking.example/AWB123"}
                    ]
                  }
                }
                """));

        var tracking = client.track("AWB123");

        assertThat(tracking.delivery().status()).isEqualTo(DeliveryStatus.IN_TRANSIT);
        assertThat(tracking.delivery().providerStatus()).isEqualTo("IN TRANSIT");
        assertThat(tracking.delivery().trackingUrl()).isEqualTo("https://tracking.example/AWB123");
    }

    private QuoteRequest request() {
        Stop pickup = new Stop(
            "Craves Kitchen, Hitech City",
            "Chef One",
            "+919999999999",
            new BigDecimal("17.4435"),
            new BigDecimal("78.3772"),
            null,
            null,
            null,
            "12 Kitchen Street",
            null,
            null,
            "Hitech City",
            "Hyderabad",
            "Telangana",
            "500081",
            "India"
        );
        Stop dropoff = new Stop(
            "Customer Home, Kondapur",
            "Customer One",
            "+919988776655",
            new BigDecimal("17.4698"),
            new BigDecimal("78.3634"),
            null,
            null,
            "Call on arrival",
            "44 Customer Road",
            "Flat 301",
            "Near Botanical Garden",
            "Kondapur",
            "Hyderabad",
            "Telangana",
            "500084",
            "India"
        );
        ShipmentItem item = new ShipmentItem(
            MENU_ITEM_ID,
            "Paneer Bowl",
            new BigDecimal("120.00"),
            2,
            new BigDecimal("240.00")
        );
        return new QuoteRequest(
            "Fresh food",
            750,
            false,
            pickup,
            dropoff,
            List.of(item),
            new BigDecimal("240.00"),
            "PREPAID",
            KITCHEN_ID
        );
    }

    private JsonNode serviceabilityResponse(int courierId,
                                            String priceField,
                                            String price,
                                            int etaMinutes) throws Exception {
        ObjectNode courier = objectMapper.createObjectNode();
        courier.put("courier_company_id", courierId);
        courier.put("courier_name", "Shiprocket Hyperlocal");
        courier.put(priceField, new BigDecimal(price));
        courier.put("eta_minutes", etaMinutes);
        ObjectNode data = objectMapper.createObjectNode();
        data.put("recommended_courier_company_id", courierId);
        data.putArray("available_courier_companies").add(courier);
        return objectMapper.createObjectNode().set("data", data);
    }

    private ProviderQuote selectedQuote(int courierId, String fee) {
        ObjectNode metadata = objectMapper.createObjectNode();
        metadata.put("courier_company_id", courierId);
        metadata.put("quote_id", "shiprocket:" + courierId + ":" + fee);
        return new ProviderQuote(
            "shiprocket",
            true,
            new BigDecimal("240.00"),
            new BigDecimal(fee),
            "INR",
            List.of(),
            metadata,
            Instant.now()
        );
    }

    private void configureProductionCreate() {
        properties.setEnabled(true);
        properties.setCreateEnabled(true);
        properties.setEnvironment("PRODUCTION");
        properties.setProductionActivationApproved(true);
        properties.setAttributionApproved(true);
        properties.setEmail("api-user@craves.in");
        properties.setPassword("test-only-secret");
        properties.setWebhookToken("test-only-webhook-token");
        properties.setOrderEmail("orders@craves.in");
        properties.setPackageLengthCm(new BigDecimal("20"));
        properties.setPackageBreadthCm(new BigDecimal("15"));
        properties.setPackageHeightCm(new BigDecimal("10"));
    }
}
