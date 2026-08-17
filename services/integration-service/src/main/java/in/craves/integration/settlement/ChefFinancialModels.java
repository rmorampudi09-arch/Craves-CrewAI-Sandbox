package in.craves.integration.settlement;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ChefFinancialModels {
    private ChefFinancialModels() {
    }

    public record CreateEarningRequest(
        @NotNull UUID orderId,
        @NotNull UUID chefIdentityId,
        @NotBlank @Size(max = 30) String orderSource,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotNull @DecimalMin("0.00") BigDecimal grossAmount,
        @NotNull @DecimalMin("0.00") BigDecimal commissionAmount,
        @NotNull @DecimalMin("0.00") BigDecimal taxWithheldAmount,
        @NotNull BigDecimal adjustmentAmount,
        @NotNull @DecimalMin("0.00") BigDecimal netPayable,
        @NotBlank @Size(max = 160) String allocationReference,
        @NotBlank @Size(max = 1000) String reason
    ) {
    }

    public record ReasonRequest(@NotBlank @Size(max = 1000) String reason) {
    }

    public record EarningResponse(
        UUID id,
        UUID orderId,
        UUID chefIdentityId,
        String orderSource,
        String currency,
        BigDecimal grossAmount,
        BigDecimal commissionAmount,
        BigDecimal taxWithheldAmount,
        BigDecimal adjustmentAmount,
        BigDecimal netPayable,
        String allocationReference,
        String status,
        String reason,
        Instant approvedAt,
        Instant reversedAt,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record CreateSettlementBatchRequest(
        @NotBlank @Size(max = 160) String batchReference,
        @NotBlank @Size(min = 3, max = 3) String currency,
        @NotEmpty @Size(max = 500) List<UUID> earningEntryIds,
        @NotBlank @Size(max = 1000) String reason
    ) {
    }

    public record SettlementStatusRequest(
        @NotBlank @Size(max = 40) String status,
        @Size(max = 200) String externalReference,
        @NotBlank @Size(max = 1000) String reason
    ) {
    }

    public record SettlementBatchResponse(
        UUID id,
        String batchReference,
        String currency,
        BigDecimal totalAmount,
        int entryCount,
        String status,
        String externalReference,
        String failureReason,
        Instant createdAt,
        Instant submittedAt,
        Instant completedAt,
        Instant updatedAt
    ) {
    }
}
