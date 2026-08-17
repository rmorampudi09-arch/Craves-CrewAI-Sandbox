package in.craves.integration.delivery.shiprocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.integration.config.ShiprocketProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class ShiprocketWebhookServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private ShiprocketWebhookInboxRepository inboxRepository;
    private ShiprocketWebhookService service;

    @BeforeEach
    void setUp() {
        ShiprocketProperties properties = new ShiprocketProperties();
        properties.setWebhookToken("expected-webhook-token");
        inboxRepository = Mockito.mock(ShiprocketWebhookInboxRepository.class);
        service = new ShiprocketWebhookService(objectMapper, properties, inboxRepository);
    }

    @Test
    void acknowledgesNonTrackingValidationProbesWithoutPersistence() {
        ShiprocketWebhookService.WebhookReceipt emptyBody = service.accept(null, null);
        ShiprocketWebhookService.WebhookReceipt emptyObject = service.accept("{}", null);
        ShiprocketWebhookService.WebhookReceipt malformedBody = service.accept("not-json", "wrong-token");
        ShiprocketWebhookService.WebhookReceipt partialBody = service.accept(
            "{\"awb\":\"VALIDATION-PROBE\"}",
            null
        );

        assertThat(emptyBody.validationProbe()).isTrue();
        assertThat(emptyObject.validationProbe()).isTrue();
        assertThat(malformedBody.validationProbe()).isTrue();
        assertThat(partialBody.validationProbe()).isTrue();
        Mockito.verifyNoInteractions(inboxRepository);
    }

    @Test
    void rejectsMissingOrIncorrectApiKeyForRealTrackingEventBeforePersistence() {
        String payload = validPayload();

        assertThatThrownBy(() -> service.accept(payload, null))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));

        assertThatThrownBy(() -> service.accept(payload, "wrong-token"))
            .isInstanceOfSatisfying(ResponseStatusException.class, ex ->
                assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED));

        Mockito.verifyNoInteractions(inboxRepository);
    }

    @Test
    void storesAuthenticatedCallbackWithCredentialFingerprintNotPlaintextToken() {
        when(inboxRepository.store(anyString(), anyString(), any(JsonNode.class))).thenReturn(true);

        ShiprocketWebhookService.WebhookReceipt receipt = service.accept(
            validPayload(),
            "expected-webhook-token"
        );

        assertThat(receipt.validationProbe()).isFalse();
        assertThat(receipt.awb()).isEqualTo("14326480716236");
        assertThat(receipt.duplicate()).isFalse();
        assertThat(receipt.providerEventId()).hasSize(64);

        ArgumentCaptor<String> fingerprint = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<JsonNode> storedPayload = ArgumentCaptor.forClass(JsonNode.class);
        verify(inboxRepository).store(anyString(), fingerprint.capture(), storedPayload.capture());

        assertThat(fingerprint.getValue())
            .hasSize(64)
            .isNotEqualTo("expected-webhook-token");
        assertThat(storedPayload.getValue().path("_craves_received_at").asText()).isNotBlank();
        assertThat(storedPayload.getValue().path("awb").asText()).isEqualTo("14326480716236");
    }

    @Test
    void reportsDuplicateWhenInboxAlreadyContainsDerivedProviderEvent() {
        when(inboxRepository.store(anyString(), anyString(), any(JsonNode.class))).thenReturn(false);

        ShiprocketWebhookService.WebhookReceipt receipt = service.accept(
            validPayload(),
            "expected-webhook-token"
        );

        assertThat(receipt.validationProbe()).isFalse();
        assertThat(receipt.duplicate()).isTrue();
    }

    private static String validPayload() {
        return """
            {
              "awb": "14326480716236",
              "current_status": "IN TRANSIT",
              "current_status_id": 20,
              "shipment_status": "IN TRANSIT",
              "shipment_status_id": 18,
              "current_timestamp": "2026-08-16 04:00:00",
              "order_id": "CRAVES-ORDER-1"
            }
            """;
    }
}
