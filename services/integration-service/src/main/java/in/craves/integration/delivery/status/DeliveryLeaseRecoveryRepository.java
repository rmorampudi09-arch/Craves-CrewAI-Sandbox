package in.craves.integration.delivery.status;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Converts stale final-attempt leases into explicit terminal states.
 * This prevents a replica crash after the last claim from leaving work permanently in-flight.
 */
@Repository
public class DeliveryLeaseRecoveryRepository {
    private final JdbcTemplate jdbc;

    public DeliveryLeaseRecoveryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public int deadLetterExhaustedCreateReconciliationLeases(int maximumAttempts,
                                                              int staleMinutes) {
        return jdbc.update("""
            UPDATE delivery_schema.delivery_command
            SET status = 'DEAD_LETTER',
                reconciliation_processing_started_at = NULL,
                next_reconciliation_at = NULL,
                last_error = COALESCE(
                    last_error,
                    'Create reconciliation worker lease expired after the final attempt'
                ),
                updated_at = now()
            WHERE status = 'RECONCILIATION_PENDING'
              AND reconciliation_attempt_count >= ?
              AND reconciliation_processing_started_at
                    < now() - make_interval(mins => ?)
            """,
            maximumAttempts,
            staleMinutes
        );
    }

    @Transactional
    public int deadLetterExhaustedWebhookLeases(int maximumAttempts,
                                                int staleMinutes) {
        return jdbc.update("""
            UPDATE delivery_schema.delivery_webhook_inbox
            SET processing_status = 'DEAD_LETTER',
                processing_started_at = NULL,
                error_message = COALESCE(
                    error_message,
                    'Webhook processing lease expired after the final attempt'
                ),
                processed_at = now()
            WHERE processing_status = 'PROCESSING'
              AND attempt_count >= ?
              AND processing_started_at < now() - make_interval(mins => ?)
            """,
            maximumAttempts,
            staleMinutes
        );
    }

    @Transactional
    public int deadLetterExhaustedTrackingLeases(int maximumAttempts,
                                                 int staleMinutes) {
        return jdbc.update("""
            UPDATE delivery_schema.delivery_job
            SET next_tracking_at = NULL,
                tracking_processing_started_at = NULL,
                tracking_dead_lettered_at = now(),
                last_tracking_error = COALESCE(
                    last_tracking_error,
                    'Tracking reconciliation lease expired after the final attempt'
                ),
                updated_at = now()
            WHERE tracking_attempt_count >= ?
              AND tracking_dead_lettered_at IS NULL
              AND tracking_processing_started_at
                    < now() - make_interval(mins => ?)
            """,
            maximumAttempts,
            staleMinutes
        );
    }
}
