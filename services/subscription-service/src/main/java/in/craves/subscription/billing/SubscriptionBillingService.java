package in.craves.subscription.billing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import in.craves.subscription.billing.SubscriptionBillingRepository.BillingClaim;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class SubscriptionBillingService {
    private static final Logger LOGGER = LoggerFactory.getLogger(SubscriptionBillingService.class);

    private final SubscriptionBillingProperties properties;
    private final SubscriptionBillingRepository repository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Autowired
    public SubscriptionBillingService(
        SubscriptionBillingProperties properties,
        SubscriptionBillingRepository repository,
        ObjectMapper objectMapper
    ) {
        this(properties, repository, objectMapper, Clock.systemUTC());
    }

    SubscriptionBillingService(
        SubscriptionBillingProperties properties,
        SubscriptionBillingRepository repository,
        ObjectMapper objectMapper,
        Clock clock
    ) {
        this.properties = properties;
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    public BillingSummary generateDueInvoices() {
        List<BillingClaim> claims = repository.claimDue(
            properties.getHorizonDays(), properties.getStaleLockMinutes(), properties.getBatchSize()
        );
        int created = 0;
        int duplicate = 0;
        int failed = 0;
        for (BillingClaim claim : claims) {
            try {
                validate(claim);
                LocalDate cycleEnd = cycleEnd(claim.cycleStart(), claim.billingPeriod());
                UUID invoiceId = UUID.randomUUID();
                UUID outboxId = UUID.randomUUID();
                if (repository.createInvoiceAndOutbox(
                    claim, cycleEnd, invoiceId, outboxId, event(outboxId, invoiceId, claim, cycleEnd)
                )) {
                    created++;
                } else {
                    duplicate++;
                }
            } catch (RuntimeException exception) {
                failed++;
                repository.releaseAfterFailure(claim);
                LOGGER.error(
                    "Subscription billing generation failed subscriptionId={} cycleStart={}",
                    claim.subscriptionId(), claim.cycleStart(), exception
                );
            }
        }
        return new BillingSummary(claims.size(), created, duplicate, failed);
    }

    static LocalDate cycleEnd(LocalDate cycleStart, String billingPeriod) {
        return switch (billingPeriod.toUpperCase(Locale.ROOT)) {
            case "WEEKLY" -> cycleStart.plusWeeks(1);
            case "MONTHLY" -> cycleStart.plusMonths(1);
            default -> throw new IllegalArgumentException("Unsupported billing period " + billingPeriod);
        };
    }

    private void validate(BillingClaim claim) {
        if (claim.customerIdentityId() == null || claim.planId() == null || claim.cycleStart() == null) {
            throw new IllegalStateException("Billing claim is missing required identifiers or cycle date");
        }
        if (claim.amount() == null || claim.amount().signum() <= 0) {
            throw new IllegalStateException("Automated billing requires a positive approved plan amount");
        }
        if (claim.currency() == null || claim.currency().length() != 3) {
            throw new IllegalStateException("Billing claim currency is invalid");
        }
    }

    private ObjectNode event(
        UUID eventId,
        UUID invoiceId,
        BillingClaim claim,
        LocalDate cycleEnd
    ) {
        ObjectNode data = objectMapper.createObjectNode();
        data.put("invoiceId", invoiceId.toString());
        data.put("subscriptionId", claim.subscriptionId().toString());
        data.put("planId", claim.planId().toString());
        data.put("customerIdentityId", claim.customerIdentityId().toString());
        if (claim.chefIdentityId() != null) {
            data.put("chefIdentityId", claim.chefIdentityId().toString());
        }
        data.put("cycleStart", claim.cycleStart().toString());
        data.put("cycleEnd", cycleEnd.toString());
        data.put("amount", claim.amount());
        data.put("currency", claim.currency().toUpperCase(Locale.ROOT));

        ObjectNode event = objectMapper.createObjectNode();
        event.put("eventId", eventId.toString());
        event.put("eventType", "SUBSCRIPTION_PAYMENT_REQUESTED");
        event.put("eventVersion", "v1");
        event.put("occurredAt", Instant.now(clock).toString());
        event.put("correlationId", invoiceId.toString());
        event.put("causationId", claim.subscriptionId().toString());
        event.put("subject", invoiceId.toString());
        event.set("data", data);
        return event;
    }

    public record BillingSummary(int claimed, int created, int duplicate, int failed) {
    }
}
