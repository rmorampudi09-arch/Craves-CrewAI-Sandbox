package in.craves.auth.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InternalAdminRolesTest {
    @Test
    void exposesNineUniqueLeastPrivilegeRoles() {
        assertThat(InternalAdminRoles.catalog()).hasSize(9);
        assertThat(InternalAdminRoles.catalog().stream().map(InternalAdminRoles.RoleDefinition::code))
            .doesNotHaveDuplicates()
            .contains(
                "PLATFORM_ADMIN", "SUPPORT_ADMIN", "PAYMENTS_ADMIN", "OPERATIONS_ADMIN",
                "CHEF_ADMIN", "COMPLIANCE_ADMIN", "SUBSCRIPTION_ADMIN",
                "NOTIFICATION_ADMIN", "AUDIT_ADMIN"
            );
    }

    @Test
    void normalizesKnownRolesAndRejectsLegacyOrCustomerRoles() {
        assertThat(InternalAdminRoles.normalize(" payments_admin ")).isEqualTo("PAYMENTS_ADMIN");
        assertThatThrownBy(() -> InternalAdminRoles.normalize("ADMIN"))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InternalAdminRoles.normalize("CUSTOMER"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
