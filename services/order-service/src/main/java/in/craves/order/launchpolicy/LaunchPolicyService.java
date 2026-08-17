package in.craves.order.launchpolicy;

import in.craves.order.exception.OrderApiException;
import in.craves.order.launchpolicy.LaunchPolicyModels.ActivateLaunchPolicyRequest;
import in.craves.order.launchpolicy.LaunchPolicyModels.CreateLaunchPolicyRequest;
import in.craves.order.launchpolicy.LaunchPolicyModels.LaunchPolicyResponse;
import in.craves.order.security.CravesPrincipal;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class LaunchPolicyService {
    private final LaunchPolicyRepository repository;

    public LaunchPolicyService(LaunchPolicyRepository repository) {
        this.repository = repository;
    }

    public List<LaunchPolicyResponse> list(CravesPrincipal principal) {
        requireAdmin(principal);
        return repository.list();
    }

    public LaunchPolicyResponse create(CravesPrincipal principal, CreateLaunchPolicyRequest request) {
        requireAdmin(principal);
        return repository.create(request, principal.identityId());
    }

    public LaunchPolicyResponse activate(
        CravesPrincipal principal,
        UUID policyId,
        ActivateLaunchPolicyRequest request
    ) {
        requireAdmin(principal);
        if (repository.findById(policyId).isEmpty()) {
            throw OrderApiException.notFound("LAUNCH_POLICY_NOT_FOUND", "Launch policy was not found");
        }
        return repository.activate(policyId, principal.identityId(), request.reason());
    }

    public LaunchPolicyResponse requireActive() {
        return repository.findActive().orElseThrow(() -> OrderApiException.serviceUnavailable(
            "LAUNCH_POLICY_NOT_CONFIGURED",
            "Ordering is temporarily unavailable because the launch policy is not active."
        ));
    }

    private static void requireAdmin(CravesPrincipal principal) {
        if (principal == null || !principal.hasAnyRole("PLATFORM_ADMIN", "OPERATIONS_ADMIN")) {
            throw new OrderApiException(
                org.springframework.http.HttpStatus.FORBIDDEN,
                "LAUNCH_POLICY_ROLE_REQUIRED",
                "Launch policy administration role is required"
            );
        }
    }
}
