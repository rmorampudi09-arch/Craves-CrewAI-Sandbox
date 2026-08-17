package in.craves.integration.web;

import in.craves.integration.security.CravesPrincipal;
import in.craves.integration.settlement.ChefFinancialModels.CreateEarningRequest;
import in.craves.integration.settlement.ChefFinancialModels.CreateSettlementBatchRequest;
import in.craves.integration.settlement.ChefFinancialModels.EarningResponse;
import in.craves.integration.settlement.ChefFinancialModels.ReasonRequest;
import in.craves.integration.settlement.ChefFinancialModels.SettlementBatchResponse;
import in.craves.integration.settlement.ChefFinancialModels.SettlementStatusRequest;
import in.craves.integration.settlement.ChefFinancialService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ChefFinancialController {
    private final ChefFinancialService service;

    public ChefFinancialController(ChefFinancialService service) {
        this.service = service;
    }

    @PostMapping("/api/v1/admin/chef-earnings")
    public EarningResponse create(
        Authentication authentication,
        @Valid @RequestBody CreateEarningRequest request
    ) {
        return service.create(principal(authentication), request);
    }

    @GetMapping("/api/v1/admin/chef-earnings")
    public List<EarningResponse> listAdmin(
        Authentication authentication,
        @RequestParam(required = false) String status,
        @RequestParam(defaultValue = "100") int limit
    ) {
        return service.listAll(principal(authentication), status, limit);
    }

    @PostMapping("/api/v1/admin/chef-earnings/{entryId}/approve")
    public EarningResponse approve(
        Authentication authentication,
        @PathVariable UUID entryId,
        @Valid @RequestBody ReasonRequest request
    ) {
        return service.approve(principal(authentication), entryId, request.reason());
    }

    @PostMapping("/api/v1/admin/chef-earnings/{entryId}/reverse")
    public EarningResponse reverse(
        Authentication authentication,
        @PathVariable UUID entryId,
        @Valid @RequestBody ReasonRequest request
    ) {
        return service.reverse(principal(authentication), entryId, request.reason());
    }

    @GetMapping("/api/v1/chef/earnings")
    public List<EarningResponse> listChef(
        Authentication authentication,
        @RequestParam(defaultValue = "100") int limit
    ) {
        return service.listChef(principal(authentication), limit);
    }

    @PostMapping("/api/v1/admin/chef-settlements")
    public SettlementBatchResponse createBatch(
        Authentication authentication,
        @Valid @RequestBody CreateSettlementBatchRequest request
    ) {
        return service.createBatch(principal(authentication), request);
    }

    @GetMapping("/api/v1/admin/chef-settlements")
    public List<SettlementBatchResponse> listBatches(
        Authentication authentication,
        @RequestParam(defaultValue = "100") int limit
    ) {
        return service.listBatches(principal(authentication), limit);
    }

    @PatchMapping("/api/v1/admin/chef-settlements/{batchId}/status")
    public SettlementBatchResponse changeBatchStatus(
        Authentication authentication,
        @PathVariable UUID batchId,
        @Valid @RequestBody SettlementStatusRequest request
    ) {
        return service.changeBatchStatus(principal(authentication), batchId, request);
    }

    private static CravesPrincipal principal(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof CravesPrincipal principal
            ? principal
            : null;
    }
}
