package in.craves.integration.payment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CashfreeWebhookInboxServiceCompatibilityTest {
    @Test
    void derivedIdempotencyKeyIsStableForLegacyWebhookRetries() {
        String payload = "{\"data\":{\"payment\":{\"payment_status\":\"SUCCESS\"}}}";

        String first = CashfreeWebhookInboxService.derivedIdempotencyKey("2023-08-01", payload);
        String retry = CashfreeWebhookInboxService.derivedIdempotencyKey("2023-08-01", payload);

        assertThat(first)
            .isEqualTo(retry)
            .startsWith("derived-")
            .hasSize(72);
    }

    @Test
    void derivedIdempotencyKeyChangesWhenWebhookPayloadChanges() {
        String success = CashfreeWebhookInboxService.derivedIdempotencyKey(
            "2023-08-01",
            "{\"payment_status\":\"SUCCESS\"}"
        );
        String failed = CashfreeWebhookInboxService.derivedIdempotencyKey(
            "2023-08-01",
            "{\"payment_status\":\"FAILED\"}"
        );

        assertThat(success).isNotEqualTo(failed);
    }
}
