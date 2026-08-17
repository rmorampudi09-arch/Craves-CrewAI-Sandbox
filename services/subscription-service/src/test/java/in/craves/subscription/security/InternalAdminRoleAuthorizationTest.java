package in.craves.subscription.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InternalAdminRoleAuthorizationTest {
    @Test
    void isolatesSubscriptionAdministration() {
        CurrentUser subscriptionAdmin = new CurrentUser(
            UUID.randomUUID(), "firebase", "+910000000000", List.of("SUBSCRIPTION_ADMIN", "ADMIN")
        );

        assertThat(subscriptionAdmin.hasAnyRole("PLATFORM_ADMIN", "SUBSCRIPTION_ADMIN")).isTrue();
        assertThat(subscriptionAdmin.hasAnyRole("PAYMENTS_ADMIN", "OPERATIONS_ADMIN")).isFalse();
    }
}
