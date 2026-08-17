package in.craves.userchef.web;

import in.craves.userchef.security.CurrentUser;
import in.craves.userchef.service.ChefApplicationService;
import in.craves.userchef.web.ApiDtos.KycDocumentResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class ChefEvidenceController {
    private final ChefApplicationService service;

    public ChefEvidenceController(ChefApplicationService service) {
        this.service = service;
    }

    @GetMapping("/chef/application/evidence")
    public List<KycDocumentResponse> mine(@AuthenticationPrincipal CurrentUser user) {
        return service.listMyApplicationEvidence(user);
    }

    @GetMapping("/backoffice/chef-reviews/{applicationId}/evidence")
    public List<KycDocumentResponse> review(
        @AuthenticationPrincipal CurrentUser user,
        @PathVariable UUID applicationId
    ) {
        return service.listApplicationEvidenceForAdmin(user, applicationId);
    }
}
