package in.craves.integration.payment;

import in.craves.integration.config.PaymentProviderProperties;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class CashfreeProductionReadinessService {
    private final PaymentProviderProperties provider;
    private final CashfreeWebhookProperties webhook;
    private final JdbcTemplate jdbcTemplate;

    public CashfreeProductionReadinessService(
        PaymentProviderProperties provider,
        CashfreeWebhookProperties webhook,
        JdbcTemplate jdbcTemplate
    ) {
        this.provider = provider;
        this.webhook = webhook;
        this.jdbcTemplate = jdbcTemplate;
    }

    public ReadinessResponse status() {
        List<String> blockers = new ArrayList<>();
        if (!"PRODUCTION".equals(provider.normalizedEnvironment())) {
            blockers.add("PAYMENT_ENVIRONMENT_NOT_PRODUCTION");
        }
        if (!provider.productionActivationApproved()) {
            blockers.add("PRODUCTION_ACTIVATION_NOT_APPROVED");
        }
        if (!StringUtils.hasText(provider.clientId())) {
            blockers.add("CLIENT_ID_SECRET_NOT_BOUND");
        }
        if (!StringUtils.hasText(provider.clientKey())) {
            blockers.add("CLIENT_KEY_SECRET_NOT_BOUND");
        }
        if (!StringUtils.hasText(provider.webhookUrl())) {
            blockers.add("WEBHOOK_URL_NOT_CONFIGURED");
        }
        if (!webhook.isWorkerEnabled()) {
            blockers.add("WEBHOOK_WORKER_DISABLED");
        }
        if (!StringUtils.hasText(provider.apiVersion())) {
            blockers.add("API_VERSION_NOT_CONFIGURED");
        }
        long pending = count("processing_status IN ('RECEIVED', 'PROCESSING', 'FAILED')");
        long deadLetter = count("processing_status = 'DEAD_LETTER'");
        if (deadLetter > 0) {
            blockers.add("WEBHOOK_DEAD_LETTER_NOT_EMPTY");
        }
        boolean configurationReady = blockers.isEmpty() && provider.productionReady();
        return new ReadinessResponse(
            provider.normalizedEnvironment(),
            configurationReady,
            configurationReady && provider.productionPaymentExecutionEnabled(),
            provider.productionPaymentExecutionEnabled(),
            webhook.isWorkerEnabled(),
            provider.apiVersion(),
            provider.allowedWebhookVersions().stream().sorted().toList(),
            pending,
            deadLetter,
            List.copyOf(blockers)
        );
    }

    private long count(String predicate) {
        Long value = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM payment_schema.cashfree_webhook_delivery WHERE " + predicate,
            Long.class
        );
        return value == null ? 0L : value;
    }

    public record ReadinessResponse(
        String environment,
        boolean configurationReady,
        boolean productionPaymentReady,
        boolean productionPaymentExecutionEnabled,
        boolean webhookWorkerEnabled,
        String apiVersion,
        List<String> allowedWebhookVersions,
        long webhookPendingCount,
        long webhookDeadLetterCount,
        List<String> blockers
    ) {
    }
}
