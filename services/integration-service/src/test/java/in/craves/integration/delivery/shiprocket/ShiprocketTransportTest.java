package in.craves.integration.delivery.shiprocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.craves.integration.config.ShiprocketProperties;
import in.craves.integration.delivery.shiprocket.ShiprocketTransport.ShiprocketApiException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ShiprocketTransportTest {
    private ShiprocketProperties properties;
    private ShiprocketAuthClient authClient;
    private ObjectMapper objectMapper;
    private HttpClient httpClient;
    private ShiprocketTransport transport;

    @BeforeEach
    void setUp() {
        properties = new ShiprocketProperties();
        properties.setReadRetryAttempts(2);
        authClient = mock(ShiprocketAuthClient.class);
        objectMapper = new ObjectMapper();
        httpClient = mock(HttpClient.class);
        transport = new ShiprocketTransport(properties, authClient, objectMapper, httpClient);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void mutationRefreshesExpiredTokenAndRetriesExactlyOnceAfterExplicit401() throws Exception {
        HttpResponse<String> unauthorized = response(401, "{\"message\":\"Unauthenticated\"}");
        HttpResponse<String> accepted = response(200, "{\"awb_code\":\"AWB-1\"}");
        when(authClient.bearerToken()).thenReturn("stale-token", "fresh-token");
        doReturn(unauthorized, accepted)
            .when(httpClient)
            .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        JsonNode result = transport.mutate("/shipments/create/forward-shipment", body());

        assertThat(result.path("awb_code").asText()).isEqualTo("AWB-1");
        verify(authClient).invalidate();
        verify(authClient, times(2)).bearerToken();
        verify(httpClient, times(2))
            .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void mutationDoesNotBlindRetryProvider5xxAndMarksOutcomeUncertain() throws Exception {
        HttpResponse<String> unavailable = response(503, "{\"message\":\"temporarily unavailable\"}");
        when(authClient.bearerToken()).thenReturn("token");
        doReturn(unavailable)
            .when(httpClient)
            .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        assertThatThrownBy(() -> transport.mutate("/shipments/create/forward-shipment", body()))
            .isInstanceOf(ShiprocketApiException.class)
            .satisfies(error -> {
                ShiprocketApiException apiError = (ShiprocketApiException) error;
                assertThat(apiError.httpStatus()).isEqualTo(503);
                assertThat(apiError.uncertainMutation()).isTrue();
            });

        verify(httpClient, times(1))
            .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        verify(authClient, never()).invalidate();
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void mutationTreatsRateLimitAsReconciliationRequiredWithoutBlindRetry() throws Exception {
        HttpResponse<String> limited = response(429, "{\"message\":\"rate limited\"}");
        when(authClient.bearerToken()).thenReturn("token");
        doReturn(limited)
            .when(httpClient)
            .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        assertThatThrownBy(() -> transport.mutate("/shipments/create/forward-shipment", body()))
            .isInstanceOf(ShiprocketApiException.class)
            .satisfies(error -> assertThat(((ShiprocketApiException) error).uncertainMutation()).isTrue());

        verify(httpClient, times(1))
            .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void definiteClientErrorIsNotRetriedOrMisclassifiedAsUncertain() throws Exception {
        HttpResponse<String> badRequest = response(400, "{\"message\":\"invalid request\"}");
        when(authClient.bearerToken()).thenReturn("token");
        doReturn(badRequest)
            .when(httpClient)
            .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        assertThatThrownBy(() -> transport.mutate("/shipments/create/forward-shipment", body()))
            .isInstanceOf(ShiprocketApiException.class)
            .satisfies(error -> {
                ShiprocketApiException apiError = (ShiprocketApiException) error;
                assertThat(apiError.httpStatus()).isEqualTo(400);
                assertThat(apiError.uncertainMutation()).isFalse();
            });

        verify(httpClient, times(1))
            .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
        verify(authClient, never()).invalidate();
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void readRefreshesTokenAfter401WithoutMutatingProviderState() throws Exception {
        HttpResponse<String> unauthorized = response(401, "{\"message\":\"Unauthenticated\"}");
        HttpResponse<String> success = response(200, "{\"data\":{}}");
        when(authClient.bearerToken()).thenReturn("stale-token", "fresh-token");
        doReturn(unauthorized, success)
            .when(httpClient)
            .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));

        JsonNode result = transport.get("/courier/serviceability/", java.util.Map.of("only_local", "1"));

        assertThat(result.path("data").isObject()).isTrue();
        verify(authClient).invalidate();
        verify(authClient, times(2)).bearerToken();
        verify(httpClient, times(2))
            .send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class));
    }

    private ObjectNode body() {
        return objectMapper.createObjectNode().put("order_id", "CRAVES-ORDER-1");
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> response(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }
}
