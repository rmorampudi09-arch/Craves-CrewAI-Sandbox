package in.craves.integration.refund;

import in.craves.integration.config.PaymentProviderProperties;
import in.craves.integration.config.PaymentRoutingProperties;
import in.craves.integration.config.RazorpayProviderProperties;
import in.craves.integration.refund.CashfreeRefundClient.RefundProviderConfigurationException;
import in.craves.integration.refund.CashfreeRefundClient.RefundProviderNonRetryableException;
import in.craves.integration.refund.CashfreeRefundClient.RefundProviderTransientException;
import in.craves.integration.refund.RefundModels.ProviderRefundResult;
import in.craves.integration.refund.RefundModels.RefundWorkItem;
import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RefundExecutionWorker {
    private static final Logger LOGGER = LoggerFactory.getLogger(RefundExecutionWorker.class);
    private static final long MAX_RETRY_DELAY_SECONDS = 3600;

    private final RefundWorkflowProperties properties;
    private final PaymentProviderProperties paymentProviderProperties;
    private final PaymentRoutingProperties paymentRoutingProperties;
    private final RazorpayProviderProperties razorpayProviderProperties;
    private final RefundRepository repository;
    private final CashfreeRefundClient cashfreeRefundClient;
    private final RazorpayRefundClient razorpayRefundClient;

    public RefundExecutionWorker(
        RefundWorkflowProperties properties,
        PaymentProviderProperties paymentProviderProperties,
        PaymentRoutingProperties paymentRoutingProperties,
        RazorpayProviderProperties razorpayProviderProperties,
        RefundRepository repository,
        CashfreeRefundClient cashfreeRefundClient,
        RazorpayRefundClient razorpayRefundClient
    ) {
        this.properties = properties;
        this.paymentProviderProperties = paymentProviderProperties;
        this.paymentRoutingProperties = paymentRoutingProperties;
        this.razorpayProviderProperties = razorpayProviderProperties;
        this.repository = repository;
        this.cashfreeRefundClient = cashfreeRefundClient;
        this.razorpayRefundClient = razorpayRefundClient;
    }

    @PostConstruct
    void validateProductionActivation() {
        if (!production()) {
            return;
        }
        if (properties.isProviderExecutionEnabled() && !properties.isProductionProviderExecutionApproved()) {
            throw new IllegalStateException(
                "CRAVES_REFUND_PRODUCTION_PROVIDER_EXECUTION_APPROVED must be true before production refund execution"
            );
        }
        if (properties.isReconciliationEnabled() && !properties.isProductionReconciliationApproved()) {
            throw new IllegalStateException(
                "CRAVES_REFUND_PRODUCTION_RECONCILIATION_APPROVED must be true before production refund reconciliation"
            );
        }
    }

    @Scheduled(fixedDelayString = "${craves.refund.worker-fixed-delay-ms:30000}")
    public void process() {
        boolean createEnabled = properties.isProviderExecutionEnabled();
        boolean reconcileEnabled = properties.isReconciliationEnabled();
        if (production()) {
            createEnabled = createEnabled && properties.isProductionProviderExecutionApproved();
            reconcileEnabled = reconcileEnabled && properties.isProductionReconciliationApproved();
        }
        if (!createEnabled && !reconcileEnabled) {
            return;
        }

        UUID lockToken = UUID.randomUUID();
        List<RefundWorkItem> workItems = repository.claimBatch(
            createEnabled,
            reconcileEnabled,
            properties.validatedWorkerBatchSize(),
            properties.validatedMaxProviderAttempts(),
            properties.validatedStaleLockSeconds(),
            lockToken
        );
        for (RefundWorkItem workItem : workItems) {
            processOne(workItem);
        }
    }

    private boolean production() {
        return paymentRoutingProperties.razorpay()
            ? razorpayProviderProperties.production()
            : !paymentProviderProperties.sandbox();
    }

    private void processOne(RefundWorkItem workItem) {
        try {
            ProviderRefundResult result;
            if ("RAZORPAY".equalsIgnoreCase(workItem.provider())) {
                result = workItem.providerRefundId() == null
                    ? razorpayRefundClient.createRefund(workItem)
                    : razorpayRefundClient.getRefund(workItem);
            } else {
                result = workItem.providerRefundId() == null
                    ? cashfreeRefundClient.createRefund(workItem)
                    : cashfreeRefundClient.getRefund(workItem);
            }
            applyResult(workItem, result);
        } catch (RefundProviderNonRetryableException exception) {
            markFailure(workItem, exception, true);
        } catch (RefundProviderTransientException | RefundProviderConfigurationException exception) {
            markFailure(workItem, exception, false);
        } catch (RuntimeException exception) {
            markFailure(workItem, exception, false);
        }
    }

    private void applyResult(RefundWorkItem workItem, ProviderRefundResult result) {
        Instant now = Instant.now();
        String providerStatus = result.providerStatus();
        String databaseStatus;
        String normalizedStatus;
        Instant nextAttemptAt;

        switch (providerStatus) {
            case "SUCCESS" -> {
                databaseStatus = "SUCCESS";
                normalizedStatus = "REFUNDED";
                nextAttemptAt = now;
            }
            case "PENDING" -> {
                databaseStatus = "PENDING";
                normalizedStatus = "REFUND_PENDING";
                nextAttemptAt = now.plus(5, ChronoUnit.MINUTES);
            }
            case "ONHOLD" -> {
                databaseStatus = "ONHOLD";
                normalizedStatus = "REFUND_PENDING";
                nextAttemptAt = now.plus(15, ChronoUnit.MINUTES);
            }
            case "FAILED" -> {
                databaseStatus = "FAILED";
                normalizedStatus = "REFUND_FAILED";
                nextAttemptAt = now;
            }
            case "CANCELLED" -> {
                databaseStatus = "CANCELLED";
                normalizedStatus = "REFUND_FAILED";
                nextAttemptAt = now;
            }
            default -> throw new RefundProviderTransientException(
                "Payment provider returned unsupported refund status " + providerStatus
            );
        }

        boolean updated = repository.applyProviderResult(
            workItem,
            result,
            databaseStatus,
            normalizedStatus,
            nextAttemptAt,
            now
        );
        if (updated) {
            LOGGER.info(
                "Refund provider result stored refundId={} chefSubOrderId={} providerStatus={} normalizedStatus={}",
                workItem.refundId(),
                workItem.chefSubOrderId(),
                providerStatus,
                normalizedStatus
            );
        } else {
            LOGGER.warn("Refund claim was no longer current refundId={}", workItem.refundId());
        }
    }

    private void markFailure(RefundWorkItem workItem, RuntimeException exception, boolean terminal) {
        Instant now = Instant.now();
        Instant nextAttemptAt = now.plusSeconds(retryDelaySeconds(workItem.attemptCount()));
        repository.markFailure(
            workItem,
            properties.validatedMaxProviderAttempts(),
            nextAttemptAt,
            safeMessage(exception),
            terminal,
            now
        );
        LOGGER.error(
            "Refund processing failed refundId={} chefSubOrderId={} attempt={} terminal={}",
            workItem.refundId(),
            workItem.chefSubOrderId(),
            workItem.attemptCount(),
            terminal,
            exception
        );
    }

    private long retryDelaySeconds(int attempt) {
        long base = properties.validatedRetryBaseDelaySeconds();
        int exponent = Math.max(0, Math.min(attempt - 1, 20));
        long multiplier = 1L << exponent;
        if (base > MAX_RETRY_DELAY_SECONDS / multiplier) {
            return MAX_RETRY_DELAY_SECONDS;
        }
        return Math.min(MAX_RETRY_DELAY_SECONDS, base * multiplier);
    }

    private static String safeMessage(Throwable exception) {
        String message = exception.getMessage();
        if (message == null || message.isBlank()) {
            return exception.getClass().getSimpleName();
        }
        String normalized = message.replace('\n', ' ').replace('\r', ' ').trim();
        return normalized.length() > 1000 ? normalized.substring(0, 1000) : normalized;
    }
}
