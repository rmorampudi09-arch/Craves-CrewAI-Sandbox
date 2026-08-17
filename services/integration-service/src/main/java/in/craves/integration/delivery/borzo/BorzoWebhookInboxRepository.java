package in.craves.integration.delivery.borzo;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BorzoWebhookInboxRepository {
    private static final String PROVIDER_ID = "borzo";
    private final JdbcTemplate jdbc;

    public BorzoWebhookInboxRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean store(String providerEventId, String signatureFingerprint, JsonNode payload) {
        int inserted = jdbc.update("""
            INSERT INTO delivery_schema.delivery_webhook_inbox
                (id, provider_id, provider_event_id, signature_hash, processing_status, raw_payload, received_at)
            VALUES (?, ?, ?, ?, 'RECEIVED', ?::jsonb, now())
            ON CONFLICT (provider_id, provider_event_id) DO NOTHING
            """,
            UUID.randomUUID(),
            PROVIDER_ID,
            providerEventId,
            signatureFingerprint,
            payload.toString()
        );
        return inserted == 1;
    }
}
