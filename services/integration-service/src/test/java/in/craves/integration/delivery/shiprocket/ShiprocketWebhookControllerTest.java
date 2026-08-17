package in.craves.integration.delivery.shiprocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestBody;

class ShiprocketWebhookControllerTest {

    @Test
    void validationProbeReturnsHttp200WithoutEventIdentity() {
        ShiprocketWebhookService webhookService = Mockito.mock(ShiprocketWebhookService.class);
        when(webhookService.accept(null, null))
            .thenReturn(ShiprocketWebhookService.WebhookReceipt.forValidationProbe());

        ShiprocketWebhookController controller = new ShiprocketWebhookController(webhookService);
        ResponseEntity<Map<String, Object>> response = controller.accept(null, null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody())
            .containsEntry("accepted", true)
            .containsEntry("validationProbe", true)
            .doesNotContainKey("eventId");
    }

    @Test
    void requestBodyIsOptionalSoProviderCanProbeTheUrlWithAnEmptyPost() throws Exception {
        Method method = ShiprocketWebhookController.class.getDeclaredMethod(
            "accept",
            String.class,
            String.class
        );
        RequestBody requestBody = method.getParameters()[0].getAnnotation(RequestBody.class);

        assertThat(requestBody).isNotNull();
        assertThat(requestBody.required()).isFalse();
    }
}
