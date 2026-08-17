package in.craves.order.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InternalAdminRoleAuthorizationTest {
    @Test
    void isolatesOrderOperationsFromPaymentAdministration() {
        CravesPrincipal operations = new CravesPrincipal(
            UUID.randomUUID(), "+910000000000", Set.of("OPERATIONS_ADMIN", "ADMIN")
        );

        assertThat(operations.hasAnyRole("PLATFORM_ADMIN", "OPERATIONS_ADMIN")).isTrue();
        assertThat(operations.hasAnyRole("PAYMENTS_ADMIN", "CHEF_ADMIN")).isFalse();
    }
}
