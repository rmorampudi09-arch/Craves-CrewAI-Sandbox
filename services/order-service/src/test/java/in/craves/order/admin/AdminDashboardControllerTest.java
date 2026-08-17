package in.craves.order.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import in.craves.order.admin.AdminDashboardService.DashboardSummary;
import in.craves.order.admin.AdminDashboardService.Metrics;
import in.craves.order.security.CravesPrincipal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class AdminDashboardControllerTest {
    @Mock
    private AdminDashboardService dashboardService;

    @Test
    void rejectsAuthenticatedIdentityWithoutDashboardReadRole() {
        AdminDashboardController controller = new AdminDashboardController(dashboardService);
        var principal = new CravesPrincipal(UUID.randomUUID(), "+910000000000", Set.of("CUSTOMER"));
        var authentication = new UsernamePasswordAuthenticationToken(principal, null);

        assertThatThrownBy(() -> controller.summary(authentication))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void rejectsLegacyAdminCompatibilityRoleByItself() {
        AdminDashboardController controller = new AdminDashboardController(dashboardService);
        var principal = new CravesPrincipal(UUID.randomUUID(), "+910000000000", Set.of("ADMIN"));
        var authentication = new UsernamePasswordAuthenticationToken(principal, null);

        assertThatThrownBy(() -> controller.summary(authentication))
            .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void returnsNoStoreSummaryForPlatformAdmin() {
        AdminDashboardController controller = new AdminDashboardController(dashboardService);
        var principal = new CravesPrincipal(UUID.randomUUID(), "+910000000000", Set.of("PLATFORM_ADMIN"));
        var authentication = new UsernamePasswordAuthenticationToken(principal, null);
        var summary = new DashboardSummary(
            OffsetDateTime.now(ZoneOffset.UTC), new Metrics(4, 1, 1, 1, 1, 0, 0, 2),
            List.of(), List.of(), List.of()
        );
        when(dashboardService.loadSummary()).thenReturn(summary);

        var response = controller.summary(authentication);

        assertThat(response.getBody()).isSameAs(summary);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store");
        verify(dashboardService).loadSummary();
    }

    @Test
    void allowsOtherDashboardReadRoles() {
        for (String role : List.of("SUPPORT_ADMIN", "PAYMENTS_ADMIN", "AUDIT_ADMIN")) {
            AdminDashboardController controller = new AdminDashboardController(dashboardService);
            var principal = new CravesPrincipal(UUID.randomUUID(), "+910000000000", Set.of(role));
            var authentication = new UsernamePasswordAuthenticationToken(principal, null);
            var summary = new DashboardSummary(
                OffsetDateTime.now(ZoneOffset.UTC), new Metrics(4, 1, 1, 1, 1, 0, 0, 2),
                List.of(), List.of(), List.of()
            );
            when(dashboardService.loadSummary()).thenReturn(summary);

            var response = controller.summary(authentication);

            assertThat(response.getBody()).isSameAs(summary);
        }
    }
}
