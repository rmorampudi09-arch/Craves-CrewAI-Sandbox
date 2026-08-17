package in.craves.order.refund;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class RefundRequestConstraintMigrationTest {

    @Test
    void refundRequestConstraintAllowsTheCompleteRefundLifecycle() throws IOException {
        ClassPathResource migration = new ClassPathResource(
            "db/migration/V8__expand_refund_request_status_constraint.sql"
        );

        String sql;
        try (var inputStream = migration.getInputStream()) {
            sql = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
            .contains("'CHEF_REJECTED'")
            .contains("'REFUND_PENDING'")
            .contains("'REFUNDED'")
            .contains("'REFUND_FAILED'")
            .contains("chef_rejection_code IS NOT NULL")
            .contains("refund_requested_amount IS NOT NULL")
            .contains("refund_requested_amount > 0")
            .doesNotContain("refund_requested_at IS NULL OR status");
    }
}
