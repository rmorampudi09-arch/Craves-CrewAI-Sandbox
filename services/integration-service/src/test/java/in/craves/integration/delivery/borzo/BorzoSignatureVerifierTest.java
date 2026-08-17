package in.craves.integration.delivery.borzo;

import static org.assertj.core.api.Assertions.assertThat;

import in.craves.integration.config.BorzoProperties;
import org.junit.jupiter.api.Test;

class BorzoSignatureVerifierTest {

    @Test
    void validatesOfficialHexHmacSha256Shape() {
        BorzoProperties properties = new BorzoProperties();
        properties.setCallbackSecret("test-secret");
        BorzoSignatureVerifier verifier = new BorzoSignatureVerifier(properties);

        String body = "{\"event_type\":\"delivery_changed\"}";
        String signature = "299a28b35885681ec91420b8b3420f875b4a564cf7c836acc2d3e37639e4e8b9";

        assertThat(verifier.isValid(body, signature)).isTrue();
        assertThat(verifier.isValid(body + " ", signature)).isFalse();
        assertThat(verifier.isValid(body, "deadbeef")).isFalse();
    }

    @Test
    void rejectsCallbacksWhenSecretIsMissing() {
        BorzoSignatureVerifier verifier = new BorzoSignatureVerifier(new BorzoProperties());

        assertThat(verifier.isConfigured()).isFalse();
        assertThat(verifier.isValid("{}", "abc")).isFalse();
    }
}
