package in.craves.integration.delivery.provider;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DeliveryProviderPickupLocationRepository {
    private final JdbcTemplate jdbc;

    public DeliveryProviderPickupLocationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<String> findVerifiedExternalLocation(String providerId, UUID pickupLocationReference) {
        if (providerId == null || providerId.isBlank() || pickupLocationReference == null) {
            return Optional.empty();
        }
        return jdbc.query(
            """
                SELECT external_location_code
                FROM delivery_schema.delivery_provider_pickup_location
                WHERE provider_id = ?
                  AND pickup_location_reference = ?
                  AND is_verified = TRUE
                """,
            (rs, rowNum) -> rs.getString("external_location_code"),
            normalize(providerId),
            pickupLocationReference
        ).stream().filter(value -> value != null && !value.isBlank()).findFirst();
    }

    public boolean isVerified(String providerId, UUID pickupLocationReference) {
        return findVerifiedExternalLocation(providerId, pickupLocationReference).isPresent();
    }

    public int countVerified(String providerId) {
        if (providerId == null || providerId.isBlank()) {
            return 0;
        }
        Integer count = jdbc.queryForObject(
            """
                SELECT COUNT(*)
                FROM delivery_schema.delivery_provider_pickup_location
                WHERE provider_id = ?
                  AND is_verified = TRUE
                  AND NULLIF(BTRIM(external_location_code), '') IS NOT NULL
                """,
            Integer.class,
            normalize(providerId)
        );
        return count == null ? 0 : count;
    }

    private static String normalize(String providerId) {
        return providerId.trim().toLowerCase(java.util.Locale.ROOT);
    }
}
