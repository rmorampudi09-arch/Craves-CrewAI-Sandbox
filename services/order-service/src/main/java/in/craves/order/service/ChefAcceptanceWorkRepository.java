package in.craves.order.service;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ChefAcceptanceWorkRepository {
    private final JdbcTemplate jdbcTemplate;

    public ChefAcceptanceWorkRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ReminderCandidate> findInitialNotificationCandidates(int batchSize) {
        return jdbcTemplate.query(
            """
                SELECT id, kitchen_id
                FROM order_schema.customer_order
                WHERE status = 'CHEF_ACCEPTANCE_PENDING'
                  AND chef_acceptance_requested_at IS NOT NULL
                  AND chef_acceptance_expires_at > now()
                  AND chef_acceptance_initial_recorded_at IS NULL
                ORDER BY chef_acceptance_requested_at ASC, id ASC
                LIMIT ?
                """,
            (resultSet, rowNumber) -> new ReminderCandidate(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("kitchen_id", UUID.class)
            ),
            batchSize
        );
    }

    public List<ReminderCandidate> findFirstReminderCandidates(int reminderMinutes, int batchSize) {
        return jdbcTemplate.query(
            """
                SELECT id, kitchen_id
                FROM order_schema.customer_order
                WHERE status = 'CHEF_ACCEPTANCE_PENDING'
                  AND chef_acceptance_requested_at IS NOT NULL
                  AND chef_acceptance_expires_at > now()
                  AND chef_acceptance_initial_recorded_at IS NOT NULL
                  AND chef_acceptance_reminder_10_recorded_at IS NULL
                  AND chef_acceptance_requested_at <= now() - (? * INTERVAL '1 minute')
                ORDER BY chef_acceptance_requested_at ASC, id ASC
                LIMIT ?
                """,
            (resultSet, rowNumber) -> new ReminderCandidate(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("kitchen_id", UUID.class)
            ),
            reminderMinutes,
            batchSize
        );
    }

    public List<ReminderCandidate> findSecondReminderCandidates(int reminderMinutes, int batchSize) {
        return jdbcTemplate.query(
            """
                SELECT id, kitchen_id
                FROM order_schema.customer_order
                WHERE status = 'CHEF_ACCEPTANCE_PENDING'
                  AND chef_acceptance_requested_at IS NOT NULL
                  AND chef_acceptance_expires_at > now()
                  AND chef_acceptance_reminder_10_recorded_at IS NOT NULL
                  AND chef_acceptance_reminder_20_recorded_at IS NULL
                  AND chef_acceptance_requested_at <= now() - (? * INTERVAL '1 minute')
                ORDER BY chef_acceptance_requested_at ASC, id ASC
                LIMIT ?
                """,
            (resultSet, rowNumber) -> new ReminderCandidate(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("kitchen_id", UUID.class)
            ),
            reminderMinutes,
            batchSize
        );
    }

    public List<UUID> findExpiredOrderIds(int batchSize) {
        return jdbcTemplate.query(
            """
                SELECT id
                FROM order_schema.customer_order
                WHERE status = 'CHEF_ACCEPTANCE_PENDING'
                  AND chef_acceptance_expires_at IS NOT NULL
                  AND chef_acceptance_expires_at <= now()
                ORDER BY chef_acceptance_expires_at ASC, id ASC
                LIMIT ?
                """,
            (resultSet, rowNumber) -> resultSet.getObject("id", UUID.class),
            batchSize
        );
    }

    public record ReminderCandidate(UUID orderId, UUID kitchenId) {
    }
}
