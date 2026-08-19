package in.craves.notification.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InternalAdminRoleAuthorizationTest {
    @Test
    void isolatesNotificationRecoveryAdministration() {
        CravesPrincipal notificationAdmin = new CravesPrincipal(
            UUID.randomUUID(), "+910000000000", Set.of("NOTIFICATION_ADMIN", "ADMIN")
        );

        assertThat(notificationAdmin.hasAnyRole("PLATFORM_ADMIN", "NOTIFICATION_ADMIN")).isTrue();
        assertThat(notificationAdmin.hasAnyRole("PAYMENTS_ADMIN", "SUBSCRIPTION_ADMIN")).isFalse();
    }

    @Test
    void doesNotTreatAuthenticatedCustomersAsInternalAdmins() {
        CravesPrincipal customer = new CravesPrincipal(
            UUID.randomUUID(), "+919999999999", Set.of("CUSTOMER")
        );

        assertThat(customer.hasAnyRole("NOTIFICATION_ADMIN", "PLATFORM_ADMIN")).isFalse();
    }
}
