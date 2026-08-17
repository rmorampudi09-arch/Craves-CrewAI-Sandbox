package in.craves.order.launchpolicy;

import in.craves.order.launchpolicy.LaunchPolicyModels.ActivateLaunchPolicyRequest;
import in.craves.order.launchpolicy.LaunchPolicyModels.CreateLaunchPolicyRequest;
import in.craves.order.launchpolicy.LaunchPolicyModels.LaunchPolicyResponse;
import in.craves.order.security.CravesPrincipal;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/launch-policies")
public class LaunchPolicyController {
    private final LaunchPolicyService service;

    public LaunchPolicyController(LaunchPolicyService service) {
        this.service = service;
    }

    @GetMapping
    public List<LaunchPolicyResponse> list(Authentication authentication) {
        return service.list(principal(authentication));
    }

    @PostMapping
    public ResponseEntity<LaunchPolicyResponse> create(
        Authentication authentication,
        @Valid @RequestBody CreateLaunchPolicyRequest request
    ) {
        LaunchPolicyResponse response = service.create(principal(authentication), request);
        return ResponseEntity.created(URI.create("/api/v1/admin/launch-policies/" + response.id())).body(response);
    }

    @PostMapping("/{policyId}/activate")
    public LaunchPolicyResponse activate(
        Authentication authentication,
        @PathVariable UUID policyId,
        @Valid @RequestBody ActivateLaunchPolicyRequest request
    ) {
        return service.activate(principal(authentication), policyId, request);
    }

    private static CravesPrincipal principal(Authentication authentication) {
        return authentication != null && authentication.getPrincipal() instanceof CravesPrincipal principal
            ? principal
            : null;
    }
}
