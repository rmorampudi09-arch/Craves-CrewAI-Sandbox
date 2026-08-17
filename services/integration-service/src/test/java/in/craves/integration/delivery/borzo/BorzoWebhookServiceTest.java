package in.craves.integration.delivery.borzo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.integration.config.BorzoProperties;
import in.craves.integration.delivery.provider.DeliveryProviderAdapter.DeliveryStatus;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class BorzoWebhookServiceTest {

    @Test
    void verifiesAndStoresDeliveryCallbacksIdempotently() {
        String body = """
            {
              "event_datetime":"2026-07-14T11:15:46+05:30",
              "event_type":"delivery_changed",
              "delivery":{"delivery_id":9001,"order_id":1250032,"status":"courier_at_pickup"}
            }
            """;
        BorzoProperties properties = new BorzoProperties();
        properties.setCallbackSecret("callback-secret");
        BorzoSignatureVerifier verifier = new BorzoSignatureVerifier(properties);
        String signature = BorzoSignatureVerifier.hmacSha256Hex(body, "callback-secret");
        BorzoWebhookInboxRepository repository = mock(BorzoWebhookInboxRepository.class);
        when(repository.store(anyString(), anyString(), any())).thenReturn(true);

        BorzoWebhookService service = new BorzoWebhookService(
            new ObjectMapper(), verifier, repository, new BorzoStatusMapper());

        var receipt = service.accept(body, signature);

        assertThat(receipt.eventType()).isEqualTo("delivery_changed");
        assertThat(receipt.normalizedStatus()).isEqualTo(DeliveryStatus.AT_PICKUP);
        assertThat(receipt.duplicate()).isFalse();
        assertThat(receipt.providerEventId()).hasSize(64);
        verify(repository).store(anyString(), anyString(), any());
    }

    @Test
    void rejectsInvalidSignaturesBeforeDatabaseAccess() {
        BorzoProperties properties = new BorzoProperties();
        properties.setCallbackSecret("callback-secret");
        BorzoWebhookInboxRepository repository = mock(BorzoWebhookInboxRepository.class);
        BorzoWebhookService service = new BorzoWebhookService(
            new ObjectMapper(),
            new BorzoSignatureVerifier(properties),
            repository,
            new BorzoStatusMapper()
        );

        assertThatThrownBy(() -> service.accept("{}", "invalid"))
            .isInstanceOf(ResponseStatusException.class)
            .hasMessageContaining("401 UNAUTHORIZED");
    }
}
