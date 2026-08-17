package in.craves.integration.refund;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class RefundRequestServiceTest {
    @Test
    void createsCashfreeCompatibleStableRefundReference() {
        UUID orderId = UUID.fromString("12345678-1234-5678-9012-123456789012");
        String reference = RefundRequestService.refundReference(orderId);

        assertThat(reference)
            .isEqualTo("CRV12345678123456789012123456789012")
            .hasSize(35)
            .matches("[A-Za-z0-9]+");
    }

    @Test
    void createsStableUuidIdempotencyKey() {
        UUID orderId = UUID.fromString("12345678-1234-5678-9012-123456789012");

        UUID first = RefundRequestService.deterministicIdempotencyKey(orderId);
        UUID second = RefundRequestService.deterministicIdempotencyKey(orderId);

        assertThat(first).isEqualTo(second);
        assertThat(first).isNotEqualTo(orderId);
    }
}
