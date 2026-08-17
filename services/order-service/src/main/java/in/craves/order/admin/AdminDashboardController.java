package in.craves.order.admin;

import in.craves.order.admin.AdminDashboardService.DashboardSummary;
import in.craves.order.security.CravesPrincipal;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/dashboard")
public class AdminDashboardController {
    private static final String[] DASHBOARD_READ_ROLES = {
        "PLATFORM_ADMIN", "SUPPORT_ADMIN", "PAYMENTS_ADMIN", "AUDIT_ADMIN"
    };

    private final AdminDashboardService dashboardService;

    public AdminDashboardController(AdminDashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public ResponseEntity<DashboardSummary> summary(Authentication authentication) {
        requireDashboardReader(authentication);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(dashboardService.loadSummary());
    }

    static CravesPrincipal requireDashboardReader(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof CravesPrincipal principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Craves access token is required");
        }
        if (!principal.hasAnyRole(DASHBOARD_READ_ROLES)) {
            throw new ResponseStatusException(
                HttpStatus.FORBIDDEN,
                "PLATFORM_ADMIN, SUPPORT_ADMIN, PAYMENTS_ADMIN or AUDIT_ADMIN role is required"
            );
        }
        return principal;
    }
}
