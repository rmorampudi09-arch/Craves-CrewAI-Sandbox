package in.craves.subscription.order;

import in.craves.subscription.config.SecurityConfig;
import in.craves.subscription.security.CravesJwtAuthenticationFilter;
import in.craves.subscription.security.JwtVerifier;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OccurrenceOrderInternalController.class)
@Import({SecurityConfig.class, CravesJwtAuthenticationFilter.class})
class OccurrenceOrderInternalSecurityTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private JwtVerifier jwtVerifier;

    @MockBean
    private OccurrenceOrderProperties properties;

    @MockBean
    private OccurrenceOrderRepository repository;

    @Test
    void internalCallbackReachesSecretGuardWithoutBearerToken() throws Exception {
        UUID occurrenceId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        when(properties.validInternalAccess("wrong-internal-secret")).thenReturn(false);

        mockMvc.perform(post("/internal/v1/subscription-occurrences/{occurrenceId}/order-created", occurrenceId)
                .header("X-Craves-Internal-Secret", "wrong-internal-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"orderId\":\"" + orderId + "\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").value("Invalid internal service credential"));

        verifyNoInteractions(repository);
    }
}
