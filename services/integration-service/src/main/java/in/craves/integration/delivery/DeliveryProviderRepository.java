package in.craves.integration.delivery;

import in.craves.integration.delivery.DeliveryIntelligenceModels.ProviderRegistrationRequest;
import in.craves.integration.delivery.DeliveryIntelligenceModels.ProviderResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DeliveryProviderRepository {
    private final JdbcTemplate jdbc;
    private final DeliveryJsonSupport json;

    public DeliveryProviderRepository(JdbcTemplate jdbc, DeliveryJsonSupport json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public ProviderResponse upsert(ProviderRegistrationRequest request) {
        jdbc.update("""
            INSERT INTO delivery_schema.delivery_provider
                (provider_id, display_name, adapter_type, is_active, service_areas, capabilities, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?::jsonb, ?::jsonb, now(), now())
            ON CONFLICT (provider_id) DO UPDATE SET
                display_name = EXCLUDED.display_name,
                adapter_type = EXCLUDED.adapter_type,
                is_active = EXCLUDED.is_active,
                service_areas = EXCLUDED.service_areas,
                capabilities = EXCLUDED.capabilities,
                updated_at = now()
            """, normalize(request.providerId()), request.displayName(), request.adapterType(), request.active(),
            json.write(request.serviceAreas() == null ? List.of() : request.serviceAreas()),
            json.write(request.capabilities() == null ? Map.of() : request.capabilities()));
        return find(request.providerId()).orElseThrow();
    }

    public Optional<ProviderResponse> find(String providerId) {
        return jdbc.query("SELECT * FROM delivery_schema.delivery_provider WHERE provider_id = ?",
            (rs, rowNum) -> map(rs), normalize(providerId)).stream().findFirst();
    }

    private ProviderResponse map(ResultSet rs) throws SQLException {
        return new ProviderResponse(
            rs.getString("provider_id"),
            rs.getString("display_name"),
            rs.getString("adapter_type"),
            rs.getBoolean("is_active"),
            json.readStringList(rs.getString("service_areas")),
            json.readBooleanMap(rs.getString("capabilities")),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant()
        );
    }

    static String normalize(String value) {
        return value.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
