package in.craves.integration.delivery.command;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.integration.delivery.command.DeliveryCommandModels.RoutingResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DeliveryJobRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public DeliveryJobRepository(JdbcTemplate jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public Optional<UUID> findIdByChefSubOrderId(UUID chefSubOrderId) {
        List<UUID> rows = jdbc.query("""
            SELECT id
            FROM delivery_schema.delivery_job
            WHERE chef_sub_order_id = ?
            """, (rs, rowNumber) -> rs.getObject("id", UUID.class), chefSubOrderId);
        return rows.stream().findFirst();
    }

    public UUID insert(UUID orderId, UUID chefSubOrderId, RoutingResult routingResult) {
        UUID deliveryJobId = UUID.randomUUID();
        Instant observedAt = routingResult.delivery().observedAt() == null
            ? Instant.now()
            : routingResult.delivery().observedAt();
        String normalizedStatus = routingResult.delivery().status().name();
        int inserted = jdbc.update("""
            INSERT INTO delivery_schema.delivery_job
                (id, chef_sub_order_id, order_id, assignment_id, provider_id, provider_delivery_id,
                 status, provider_status, tracking_url, quote_snapshot, booked_at,
                 last_status_observed_at, last_status_source, next_tracking_at,
                 created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, now(), ?, 'CREATE',
                    CASE WHEN ? IN ('DELIVERED', 'CANCELLED', 'RETURNED', 'FAILED')
                         THEN NULL ELSE now() END,
                    now(), now())
            ON CONFLICT (chef_sub_order_id) DO NOTHING
            """,
            deliveryJobId,
            chefSubOrderId,
            orderId,
            routingResult.intelligenceAssignment().assignmentId(),
            routingResult.providerId(),
            routingResult.delivery().providerDeliveryId(),
            normalizedStatus,
            routingResult.delivery().providerStatus(),
            routingResult.delivery().trackingUrl(),
            writeJson(routingResult),
            databaseTimestamp(observedAt),
            normalizedStatus
        );
        if (inserted == 1) {
            return deliveryJobId;
        }
        return findIdByChefSubOrderId(chefSubOrderId)
            .orElseThrow(() -> new IllegalStateException("Delivery job conflict occurred without an existing row"));
    }

    static OffsetDateTime databaseTimestamp(Instant value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Delivery routing audit could not be serialized", ex);
        }
    }
}
