package in.craves.integration.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InternalAdminRoleAuthorizationTest {
    @Test
    void isolatesPaymentAdministrationFromDeliveryOperations() {
        CravesPrincipal payments = new CravesPrincipal(
            UUID.randomUUID(), "+910000000000", Set.of("PAYMENTS_ADMIN", "ADMIN")
        );

        assertThat(payments.hasAnyRole("PLATFORM_ADMIN", "PAYMENTS_ADMIN")).isTrue();
        assertThat(payments.hasAnyRole("OPERATIONS_ADMIN", "NOTIFICATION_ADMIN")).isFalse();
    }
}
