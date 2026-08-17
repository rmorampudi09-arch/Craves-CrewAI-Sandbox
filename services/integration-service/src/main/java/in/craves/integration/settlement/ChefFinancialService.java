package in.craves.integration.settlement;

import in.craves.integration.security.CravesPrincipal;
import in.craves.integration.settlement.ChefFinancialModels.CreateEarningRequest;
import in.craves.integration.settlement.ChefFinancialModels.CreateSettlementBatchRequest;
import in.craves.integration.settlement.ChefFinancialModels.EarningResponse;
import in.craves.integration.settlement.ChefFinancialModels.SettlementBatchResponse;
import in.craves.integration.settlement.ChefFinancialModels.SettlementStatusRequest;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ChefFinancialService {
    private static final Set<String> ORDER_SOURCES = Set.of("ON_DEMAND", "SUBSCRIPTION");
    private static final Set<String> BATCH_TARGETS = Set.of("SUBMITTED", "SETTLED", "FAILED", "CANCELLED");

    private final ChefFinancialRepository repository;

    public ChefFinancialService(ChefFinancialRepository repository) {
        this.repository = repository;
    }

    public EarningResponse create(CravesPrincipal principal, CreateEarningRequest request) {
        requireFinanceOperator(principal);
        String source = normalize(request.orderSource());
        if (!ORDER_SOURCES.contains(source)) {
            throw badRequest("orderSource must be ON_DEMAND or SUBSCRIPTION");
        }
        String currency = normalizeCurrency(request.currency());
        BigDecimal gross = money(request.grossAmount());
        BigDecimal commission = money(request.commissionAmount());
        BigDecimal tax = money(request.taxWithheldAmount());
        BigDecimal adjustment = moneySigned(request.adjustmentAmount());
        BigDecimal expectedNet = gross.subtract(commission).subtract(tax).add(adjustment).setScale(2, RoundingMode.HALF_UP);
        BigDecimal suppliedNet = money(request.netPayable());
        if (expectedNet.signum() < 0 || expectedNet.compareTo(suppliedNet) != 0) {
            throw badRequest("netPayable must equal grossAmount - commissionAmount - taxWithheldAmount + adjustmentAmount");
        }
        CreateEarningRequest normalized = new CreateEarningRequest(
            request.orderId(), request.chefIdentityId(), source, currency,
            gross, commission, tax, adjustment, suppliedNet,
            request.allocationReference().trim(), request.reason().trim()
        );
        return repository.create(normalized, principal.identityId());
    }

    public EarningResponse approve(CravesPrincipal principal, UUID id, String reason) {
        requireFinanceOperator(principal);
        return translate(() -> repository.approve(id, principal.identityId(), requiredReason(reason)));
    }

    public EarningResponse reverse(CravesPrincipal principal, UUID id, String reason) {
        requireFinanceOperator(principal);
        return translate(() -> repository.reverse(id, principal.identityId(), requiredReason(reason)));
    }

    public List<EarningResponse> listChef(CravesPrincipal principal, int limit) {
        requireRole(principal, "CHEF");
        return repository.listForChef(principal.identityId(), bounded(limit));
    }

    public List<EarningResponse> listAll(CravesPrincipal principal, String status, int limit) {
        requireFinanceReader(principal);
        String normalized = StringUtils.hasText(status) ? normalize(status) : null;
        return repository.listAll(normalized, bounded(limit));
    }

    public SettlementBatchResponse createBatch(CravesPrincipal principal, CreateSettlementBatchRequest request) {
        requireFinanceOperator(principal);
        CreateSettlementBatchRequest normalized = new CreateSettlementBatchRequest(
            request.batchReference().trim(), normalizeCurrency(request.currency()),
            request.earningEntryIds().stream().distinct().toList(), request.reason().trim()
        );
        return translate(() -> repository.createBatch(normalized, principal.identityId()));
    }

    public SettlementBatchResponse changeBatchStatus(
        CravesPrincipal principal, UUID batchId, SettlementStatusRequest request
    ) {
        requireFinanceOperator(principal);
        String status = normalize(request.status());
        if (!BATCH_TARGETS.contains(status)) {
            throw badRequest("Unsupported settlement batch status");
        }
        String externalReference = StringUtils.hasText(request.externalReference())
            ? request.externalReference().trim() : null;
        return translate(() -> repository.changeBatchStatus(
            batchId, status, externalReference, principal.identityId(), request.reason().trim()
        ));
    }

    public List<SettlementBatchResponse> listBatches(CravesPrincipal principal, int limit) {
        requireFinanceReader(principal);
        return repository.listBatches(bounded(limit));
    }

    private static int bounded(int value) {
        return Math.max(1, Math.min(value, 500));
    }

    private static BigDecimal money(BigDecimal value) {
        if (value == null || value.signum() < 0) throw badRequest("Money values must be zero or greater");
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal moneySigned(BigDecimal value) {
        if (value == null) throw badRequest("adjustmentAmount is required");
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private static String normalizeCurrency(String value) {
        String normalized = normalize(value);
        if (normalized.length() != 3) throw badRequest("currency must be a three-character code");
        return normalized;
    }

    private static String normalize(String value) {
        if (!StringUtils.hasText(value)) throw badRequest("Required value is missing");
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static String requiredReason(String value) {
        if (!StringUtils.hasText(value)) throw badRequest("A reason is required");
        String normalized = value.trim();
        if (normalized.length() > 1000) throw badRequest("Reason must be 1000 characters or fewer");
        return normalized;
    }

    private static void requireFinanceOperator(CravesPrincipal principal) {
        if (principal == null || !principal.hasAnyRole("PLATFORM_ADMIN", "PAYMENTS_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Payments administration role is required");
        }
    }

    private static void requireFinanceReader(CravesPrincipal principal) {
        if (principal == null || !principal.hasAnyRole("PLATFORM_ADMIN", "PAYMENTS_ADMIN", "AUDIT_ADMIN")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Financial ledger read role is required");
        }
    }

    private static void requireRole(CravesPrincipal principal, String role) {
        if (principal == null || !principal.hasRole(role)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, role + " role is required");
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }

    private static <T> T translate(SupplierWithException<T> supplier) {
        try {
            return supplier.get();
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage());
        } catch (IllegalStateException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, exception.getMessage());
        }
    }

    @FunctionalInterface
    private interface SupplierWithException<T> {
        T get();
    }
}
