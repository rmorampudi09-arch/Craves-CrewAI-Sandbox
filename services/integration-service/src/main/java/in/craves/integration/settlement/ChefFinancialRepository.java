package in.craves.integration.settlement;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.integration.settlement.ChefFinancialModels.CreateEarningRequest;
import in.craves.integration.settlement.ChefFinancialModels.CreateSettlementBatchRequest;
import in.craves.integration.settlement.ChefFinancialModels.EarningResponse;
import in.craves.integration.settlement.ChefFinancialModels.SettlementBatchResponse;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ChefFinancialRepository {
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public ChefFinancialRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public EarningResponse create(CreateEarningRequest request, UUID actor) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO payment_schema.chef_earning_entry " +
                "(id, order_id, chef_identity_id, order_source, currency, gross_amount, commission_amount, " +
                "tax_withheld_amount, adjustment_amount, net_payable, allocation_reference, status, reason, " +
                "created_by_identity_id, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'DRAFT', ?, ?, now(), now())",
            id, request.orderId(), request.chefIdentityId(), request.orderSource(), request.currency(),
            request.grossAmount(), request.commissionAmount(), request.taxWithheldAmount(),
            request.adjustmentAmount(), request.netPayable(), request.allocationReference(), request.reason(), actor
        );
        EarningResponse created = get(id);
        audit(created, "CREATE", null, "DRAFT", actor, request.reason());
        return created;
    }

    public EarningResponse get(UUID id) {
        return jdbcTemplate.query(
            "SELECT * FROM payment_schema.chef_earning_entry WHERE id = ?",
            this::mapEarning,
            id
        ).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("Chef earning entry was not found"));
    }

    public List<EarningResponse> listForChef(UUID chefIdentityId, int limit) {
        return jdbcTemplate.query(
            "SELECT * FROM payment_schema.chef_earning_entry WHERE chef_identity_id = ? " +
                "ORDER BY created_at DESC LIMIT ?",
            this::mapEarning,
            chefIdentityId,
            limit
        );
    }

    public List<EarningResponse> listAll(String status, int limit) {
        if (status == null || status.isBlank()) {
            return jdbcTemplate.query(
                "SELECT * FROM payment_schema.chef_earning_entry ORDER BY created_at DESC LIMIT ?",
                this::mapEarning,
                limit
            );
        }
        return jdbcTemplate.query(
            "SELECT * FROM payment_schema.chef_earning_entry WHERE status = ? ORDER BY created_at DESC LIMIT ?",
            this::mapEarning,
            status,
            limit
        );
    }

    @Transactional
    public EarningResponse approve(UUID id, UUID actor, String reason) {
        EarningResponse existing = lockEarning(id);
        if ("APPROVED".equals(existing.status())) {
            return existing;
        }
        requireStatus(existing, "DRAFT");
        jdbcTemplate.update(
            "UPDATE payment_schema.chef_earning_entry SET status = 'APPROVED', approved_by_identity_id = ?, " +
                "approved_at = now(), version = version + 1, updated_at = now() WHERE id = ?",
            actor, id
        );
        EarningResponse result = get(id);
        audit(result, "APPROVE", existing.status(), result.status(), actor, reason);
        return result;
    }

    @Transactional
    public EarningResponse reverse(UUID id, UUID actor, String reason) {
        EarningResponse existing = lockEarning(id);
        if ("REVERSED".equals(existing.status())) {
            return existing;
        }
        if ("SETTLEMENT_PENDING".equals(existing.status())) {
            throw new IllegalStateException("Earning entry is part of a pending settlement batch");
        }
        if (!List.of("DRAFT", "APPROVED", "SETTLED").contains(existing.status())) {
            throw new IllegalStateException("Earning entry cannot be reversed from status " + existing.status());
        }
        jdbcTemplate.update(
            "UPDATE payment_schema.chef_earning_entry SET status = 'REVERSED', reversed_by_identity_id = ?, " +
                "reversed_at = now(), version = version + 1, updated_at = now() WHERE id = ?",
            actor, id
        );
        EarningResponse result = get(id);
        audit(result, "REVERSE", existing.status(), result.status(), actor, reason);
        return result;
    }

    @Transactional
    public SettlementBatchResponse createBatch(
        CreateSettlementBatchRequest request,
        UUID actor
    ) {
        List<EarningResponse> entries = lockApprovedEntries(request.earningEntryIds());
        if (entries.size() != request.earningEntryIds().stream().distinct().count()) {
            throw new IllegalArgumentException("One or more earning entries were not found or not approved");
        }
        for (EarningResponse entry : entries) {
            if (!request.currency().equalsIgnoreCase(entry.currency())) {
                throw new IllegalArgumentException("Settlement batch currency must match every earning entry");
            }
        }
        BigDecimal total = entries.stream()
            .map(EarningResponse::netPayable)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .setScale(2);
        UUID batchId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO payment_schema.chef_settlement_batch " +
                "(id, batch_reference, currency, total_amount, entry_count, status, created_by_identity_id, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, 'DRAFT', ?, now(), now())",
            batchId, request.batchReference(), request.currency(), total, entries.size(), actor
        );
        for (EarningResponse entry : entries) {
            jdbcTemplate.update(
                "INSERT INTO payment_schema.chef_settlement_item " +
                    "(batch_id, earning_entry_id, chef_identity_id, amount, created_at) VALUES (?, ?, ?, ?, now())",
                batchId, entry.id(), entry.chefIdentityId(), entry.netPayable()
            );
            jdbcTemplate.update(
                "UPDATE payment_schema.chef_earning_entry SET status = 'SETTLEMENT_PENDING', " +
                    "version = version + 1, updated_at = now() WHERE id = ?",
                entry.id()
            );
            audit(
                get(entry.id()),
                "ADD_TO_SETTLEMENT",
                "APPROVED",
                "SETTLEMENT_PENDING",
                actor,
                request.reason()
            );
        }
        settlementAudit(batchId, "CREATE", null, "DRAFT", actor, request.reason(), null);
        return getBatch(batchId);
    }

    public SettlementBatchResponse getBatch(UUID id) {
        return jdbcTemplate.query(
            "SELECT * FROM payment_schema.chef_settlement_batch WHERE id = ?",
            this::mapBatch,
            id
        ).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("Settlement batch was not found"));
    }

    public List<SettlementBatchResponse> listBatches(int limit) {
        return jdbcTemplate.query(
            "SELECT * FROM payment_schema.chef_settlement_batch ORDER BY created_at DESC LIMIT ?",
            this::mapBatch,
            limit
        );
    }

    @Transactional
    public SettlementBatchResponse changeBatchStatus(
        UUID id,
        String target,
        String externalReference,
        UUID actor,
        String reason
    ) {
        SettlementBatchResponse existing = lockBatch(id);
        if (target.equals(existing.status())) {
            return existing;
        }
        switch (target) {
            case "SUBMITTED" -> {
                requireBatchStatus(existing, "DRAFT");
                if (externalReference == null || externalReference.isBlank()) {
                    throw new IllegalArgumentException("External settlement reference is required when submitting");
                }
                jdbcTemplate.update(
                    "UPDATE payment_schema.chef_settlement_batch SET status = 'SUBMITTED', external_reference = ?, " +
                        "submitted_by_identity_id = ?, submitted_at = now(), failure_reason = NULL, updated_at = now() WHERE id = ?",
                    externalReference, actor, id
                );
            }
            case "SETTLED" -> {
                requireBatchStatus(existing, "SUBMITTED");
                jdbcTemplate.update(
                    "UPDATE payment_schema.chef_settlement_batch SET status = 'SETTLED', completed_by_identity_id = ?, " +
                        "completed_at = now(), updated_at = now() WHERE id = ?",
                    actor, id
                );
                updateBatchEntries(id, "SETTLED");
            }
            case "FAILED" -> {
                requireBatchStatus(existing, "SUBMITTED");
                jdbcTemplate.update(
                    "UPDATE payment_schema.chef_settlement_batch SET status = 'FAILED', completed_by_identity_id = ?, " +
                        "completed_at = now(), failure_reason = ?, updated_at = now() WHERE id = ?",
                    actor, reason, id
                );
                updateBatchEntries(id, "APPROVED");
            }
            case "CANCELLED" -> {
                requireBatchStatus(existing, "DRAFT");
                jdbcTemplate.update(
                    "UPDATE payment_schema.chef_settlement_batch SET status = 'CANCELLED', completed_by_identity_id = ?, " +
                        "completed_at = now(), updated_at = now() WHERE id = ?",
                    actor, id
                );
                updateBatchEntries(id, "APPROVED");
            }
            default -> throw new IllegalArgumentException("Unsupported settlement status " + target);
        }
        SettlementBatchResponse result = getBatch(id);
        settlementAudit(id, "STATUS_CHANGE", existing.status(), result.status(), actor, reason, externalReference);
        return result;
    }

    private List<EarningResponse> lockApprovedEntries(List<UUID> ids) {
        return jdbcTemplate.query(
            "SELECT * FROM payment_schema.chef_earning_entry WHERE id = ANY (?) AND status = 'APPROVED' FOR UPDATE",
            ps -> ps.setArray(1, ps.getConnection().createArrayOf("uuid", ids.toArray())),
            this::mapEarning
        );
    }

    private EarningResponse lockEarning(UUID id) {
        return jdbcTemplate.query(
            "SELECT * FROM payment_schema.chef_earning_entry WHERE id = ? FOR UPDATE",
            this::mapEarning,
            id
        ).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("Chef earning entry was not found"));
    }

    private SettlementBatchResponse lockBatch(UUID id) {
        return jdbcTemplate.query(
            "SELECT * FROM payment_schema.chef_settlement_batch WHERE id = ? FOR UPDATE",
            this::mapBatch,
            id
        ).stream().findFirst().orElseThrow(() -> new IllegalArgumentException("Settlement batch was not found"));
    }

    private void updateBatchEntries(UUID batchId, String status) {
        jdbcTemplate.update(
            "UPDATE payment_schema.chef_earning_entry earning SET status = ?, version = version + 1, updated_at = now() " +
                "FROM payment_schema.chef_settlement_item item WHERE item.batch_id = ? AND item.earning_entry_id = earning.id",
            status, batchId
        );
    }

    private void audit(
        EarningResponse entry,
        String action,
        String oldStatus,
        String newStatus,
        UUID actor,
        String reason
    ) {
        try {
            jdbcTemplate.update(
                "INSERT INTO payment_schema.chef_earning_audit " +
                    "(id, earning_entry_id, action, old_status, new_status, actor_identity_id, reason, snapshot, created_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), now())",
                UUID.randomUUID(), entry.id(), action, oldStatus, newStatus, actor, reason,
                objectMapper.writeValueAsString(entry)
            );
        } catch (Exception exception) {
            throw new IllegalStateException("Chef earning audit serialization failed", exception);
        }
    }

    private void settlementAudit(
        UUID batchId,
        String action,
        String oldStatus,
        String newStatus,
        UUID actor,
        String reason,
        String externalReference
    ) {
        jdbcTemplate.update(
            "INSERT INTO payment_schema.chef_settlement_audit " +
                "(id, batch_id, action, old_status, new_status, actor_identity_id, reason, external_reference, created_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, now())",
            UUID.randomUUID(), batchId, action, oldStatus, newStatus, actor, reason, externalReference
        );
    }

    private EarningResponse mapEarning(ResultSet rs, int rowNum) throws SQLException {
        return new EarningResponse(
            rs.getObject("id", UUID.class),
            rs.getObject("order_id", UUID.class),
            rs.getObject("chef_identity_id", UUID.class),
            rs.getString("order_source"),
            rs.getString("currency"),
            rs.getBigDecimal("gross_amount"),
            rs.getBigDecimal("commission_amount"),
            rs.getBigDecimal("tax_withheld_amount"),
            rs.getBigDecimal("adjustment_amount"),
            rs.getBigDecimal("net_payable"),
            rs.getString("allocation_reference"),
            rs.getString("status"),
            rs.getString("reason"),
            instant(rs, "approved_at"),
            instant(rs, "reversed_at"),
            instant(rs, "created_at"),
            instant(rs, "updated_at")
        );
    }

    private SettlementBatchResponse mapBatch(ResultSet rs, int rowNum) throws SQLException {
        return new SettlementBatchResponse(
            rs.getObject("id", UUID.class),
            rs.getString("batch_reference"),
            rs.getString("currency"),
            rs.getBigDecimal("total_amount"),
            rs.getInt("entry_count"),
            rs.getString("status"),
            rs.getString("external_reference"),
            rs.getString("failure_reason"),
            instant(rs, "created_at"),
            instant(rs, "submitted_at"),
            instant(rs, "completed_at"),
            instant(rs, "updated_at")
        );
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        var value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private static void requireStatus(EarningResponse entry, String expected) {
        if (!expected.equals(entry.status())) {
            throw new IllegalStateException("Earning entry must be " + expected + " but is " + entry.status());
        }
    }

    private static void requireBatchStatus(SettlementBatchResponse batch, String expected) {
        if (!expected.equals(batch.status())) {
            throw new IllegalStateException("Settlement batch must be " + expected + " but is " + batch.status());
        }
    }
}
