package in.craves.integration.refund;

import in.craves.integration.config.PaymentProviderProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RefundProductionReadinessService {
    private final RefundWorkflowProperties refund;
    private final PaymentProviderProperties payment;
    private final JdbcTemplate jdbcTemplate;

    public RefundProductionReadinessService(
        RefundWorkflowProperties refund,
        PaymentProviderProperties payment,
        JdbcTemplate jdbcTemplate
    ) {
        this.refund = refund;
        this.payment = payment;
        this.jdbcTemplate = jdbcTemplate;
    }

    public ReadinessResponse status() {
        long executable = refundCount("status IN ('REQUESTED', 'RETRY') AND cf_refund_id IS NULL");
        long reconcilable = refundCount("status IN ('PENDING', 'ONHOLD') AND cf_refund_id IS NOT NULL");
        long processing = refundCount("status = 'PROCESSING'");
        long refundDead = refundCount("status = 'DEAD_LETTER'");
        long statusPending = outboxCount("status IN ('PENDING', 'FAILED', 'PROCESSING')");
        long statusDead = outboxCount("status = 'DEAD_LETTER'");
        long inboxFailed = inboxCount("processing_status IN ('FAILED', 'REJECTED')");

        List<String> blockers = new ArrayList<>();
        if (!"PRODUCTION".equals(payment.normalizedEnvironment())) {
            blockers.add("CASHFREE_ENVIRONMENT_NOT_PRODUCTION");
        }
        if (!payment.productionReady()) {
            blockers.add("CASHFREE_PRODUCTION_NOT_READY");
        }
        if (!refund.isConsumerEnabled()) {
            blockers.add("REFUND_REQUEST_CONSUMER_DISABLED");
        }
        if (!refund.isStatusPublisherEnabled()) {
            blockers.add("REFUND_STATUS_PUBLISHER_DISABLED");
        }
        if (!StringUtils.hasText(refund.getFullyQualifiedNamespace())
            && !StringUtils.hasText(refund.getConnectionString())) {
            blockers.add("SERVICE_BUS_NOT_CONFIGURED");
        }
        if (refundDead > 0) {
            blockers.add("REFUND_DEAD_LETTER_NOT_EMPTY");
        }
        if (statusDead > 0) {
            blockers.add("REFUND_STATUS_OUTBOX_DEAD_LETTER_NOT_EMPTY");
        }
        if (inboxFailed > 0) {
            blockers.add("REFUND_REQUEST_INBOX_FAILURES_PRESENT");
        }

        boolean downstreamReady = blockers.isEmpty();
        boolean providerExecutionReady = downstreamReady
            && refund.isProductionProviderExecutionApproved()
            && refund.isProviderExecutionEnabled();
        boolean reconciliationReady = downstreamReady
            && refund.isProductionReconciliationApproved()
            && refund.isReconciliationEnabled();
        return new ReadinessResponse(
            payment.normalizedEnvironment(),
            downstreamReady,
            providerExecutionReady,
            reconciliationReady,
            refund.isConsumerEnabled(),
            refund.isStatusPublisherEnabled(),
            refund.isProductionProviderExecutionApproved(),
            refund.isProductionReconciliationApproved(),
            executable,
            reconcilable,
            processing,
            refundDead,
            statusPending,
            statusDead,
            inboxFailed,
            List.copyOf(blockers)
        );
    }

    private long refundCount(String predicate) {
        return count("SELECT COUNT(*) FROM payment_schema.refund WHERE " + predicate);
    }

    private long outboxCount(String predicate) {
        return count("SELECT COUNT(*) FROM payment_schema.refund_status_outbox WHERE " + predicate);
    }

    private long inboxCount(String predicate) {
        return count("SELECT COUNT(*) FROM payment_schema.refund_request_inbox WHERE " + predicate);
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    public record ReadinessResponse(
        String paymentEnvironment,
        boolean downstreamReady,
        boolean providerExecutionReady,
        boolean reconciliationReady,
        boolean consumerEnabled,
        boolean statusPublisherEnabled,
        boolean productionProviderExecutionApproved,
        boolean productionReconciliationApproved,
        long executableRefundCount,
        long reconcilableRefundCount,
        long processingRefundCount,
        long refundDeadLetterCount,
        long statusOutboxPendingCount,
        long statusOutboxDeadLetterCount,
        long requestInboxFailureCount,
        List<String> blockers
    ) {
    }
}
