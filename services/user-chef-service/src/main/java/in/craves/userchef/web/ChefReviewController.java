package in.craves.userchef.web;

import in.craves.userchef.security.CurrentUser;
import in.craves.userchef.service.ChefApplicationService;
import in.craves.userchef.web.ApiDtos.AdminDecisionRequest;
import in.craves.userchef.web.ApiDtos.ChefApplicationResponse;
import in.craves.userchef.web.ApiDtos.ChefApplicationStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/backoffice/chef-reviews")
public class ChefReviewController {
    private final ChefApplicationService service;

    public ChefReviewController(ChefApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public List<ChefApplicationResponse> list(
        @AuthenticationPrincipal CurrentUser user,
        @RequestParam(required = false) ChefApplicationStatus status
    ) {
        return service.listApplications(user, status);
    }

    @GetMapping("/{applicationId}")
    public ChefApplicationResponse get(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID applicationId) {
        return service.getApplicationForAdmin(user, applicationId);
    }

    @PostMapping("/{applicationId}/approve")
    public ChefApplicationResponse approve(@AuthenticationPrincipal CurrentUser user, @PathVariable UUID applicationId) {
        return service.approve(user, applicationId);
    }

    @PostMapping("/{applicationId}/reject")
    public ChefApplicationResponse reject(
        @AuthenticationPrincipal CurrentUser user,
        @PathVariable UUID applicationId,
        @RequestBody AdminDecisionRequest request
    ) {
        return service.reject(user, applicationId, request);
    }
}
