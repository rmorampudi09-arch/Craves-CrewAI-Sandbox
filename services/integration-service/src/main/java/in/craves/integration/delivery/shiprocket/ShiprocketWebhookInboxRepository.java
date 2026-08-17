package in.craves.integration.delivery.shiprocket;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ShiprocketWebhookInboxRepository {
    private static final String PROVIDER_ID = "shiprocket";
    private final JdbcTemplate jdbc;

    public ShiprocketWebhookInboxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean store(String providerEventId, String authenticationFingerprint, JsonNode payload) {
        int inserted = jdbc.update("""
            INSERT INTO delivery_schema.delivery_webhook_inbox
                (id, provider_id, provider_event_id, signature_hash, processing_status, raw_payload, received_at)
            VALUES (?, ?, ?, ?, 'RECEIVED', ?::jsonb, now())
            ON CONFLICT (provider_id, provider_event_id) DO NOTHING
            """,
            UUID.randomUUID(),
            PROVIDER_ID,
            providerEventId,
            authenticationFingerprint,
            payload.toString()
        );
        return inserted == 1;
    }
}
