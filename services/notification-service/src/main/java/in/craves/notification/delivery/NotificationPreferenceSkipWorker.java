package in.craves.notification.delivery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "craves.notification.delivery", name = "worker-enabled", havingValue = "true")
public class NotificationPreferenceSkipWorker {
    private final JdbcTemplate jdbcTemplate;

    public NotificationPreferenceSkipWorker(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Scheduled(fixedDelayString = "${craves.notification.delivery.fixed-delay-ms:5000}")
    public void skipDisabledPreferences() {
        jdbcTemplate.update(
            """
            UPDATE notification_schema.notification_request request
               SET status = 'SKIPPED',
                   last_error = 'Recipient disabled this notification channel',
                   next_attempt_at = NULL,
                   lock_token = NULL,
                   locked_at = NULL,
                   updated_at = now()
             WHERE request.status IN ('PENDING', 'FAILED')
               AND request.channel IN ('PUSH', 'EMAIL')
               AND EXISTS (
                   SELECT 1
                     FROM notification_schema.notification_preference preference
                    WHERE preference.recipient_identity_id = request.recipient_identity_id
                      AND preference.channel = request.channel
                      AND preference.enabled = false
               )
            """
        );
    }
}
