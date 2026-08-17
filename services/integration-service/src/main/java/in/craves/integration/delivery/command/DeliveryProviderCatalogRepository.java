package in.craves.integration.delivery.command;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DeliveryProviderCatalogRepository {
    private final JdbcTemplate jdbc;

    public DeliveryProviderCatalogRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<String> activeProviderIds() {
        return jdbc.queryForList("""
            SELECT provider_id
            FROM delivery_schema.delivery_provider
            WHERE is_active = TRUE
            ORDER BY provider_id
            """, String.class);
    }
}
