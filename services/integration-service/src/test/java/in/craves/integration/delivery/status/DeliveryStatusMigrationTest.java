package in.craves.integration.delivery.status;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DeliveryStatusMigrationTest {

    @Test
    void v102ContainsDurableWebhookAndTrackingSafetyGuards() throws IOException {
        String migration = resource(
            "db/migration/V102__delivery_webhook_status_reconciliation.sql"
        );

        assertThat(migration)
            .contains("processing_started_at TIMESTAMPTZ")
            .contains("attempt_count INTEGER NOT NULL DEFAULT 0")
            .contains("'DEAD_LETTER'")
            .contains("ix_delivery_webhook_process_due")
            .contains("tracking_dead_lettered_at TIMESTAMPTZ")
            .contains("ix_delivery_job_tracking_dead_letter")
            .contains("applied BOOLEAN NOT NULL DEFAULT TRUE")
            .contains("ignored_reason VARCHAR(120)")
            .contains("They are deliberately not scheduled for polling")
            .doesNotContain("ELSE COALESCE(next_tracking_at, now())");
    }

    private static String resource(String path) throws IOException {
        try (InputStream stream = DeliveryStatusMigrationTest.class
            .getClassLoader()
            .getResourceAsStream(path)) {
            if (stream == null) {
                throw new IOException("Missing test resource " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
