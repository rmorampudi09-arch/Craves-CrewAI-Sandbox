package in.craves.userchef.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class InternalAdminRoleAuthorizationTest {
    @Test
    void matchesInternalRolesCaseInsensitivelyWithoutTreatingLegacyAdminAsSpecificAccess() {
        CurrentUser support = new CurrentUser(UUID.randomUUID(), "firebase", "+910000000000", List.of("support_admin"));
        CurrentUser legacy = new CurrentUser(UUID.randomUUID(), "firebase", "+910000000001", List.of("ADMIN"));

        assertThat(support.hasAnyRole("PLATFORM_ADMIN", "SUPPORT_ADMIN")).isTrue();
        assertThat(legacy.hasAnyRole("SUPPORT_ADMIN", "CHEF_ADMIN")).isFalse();
    }
}
