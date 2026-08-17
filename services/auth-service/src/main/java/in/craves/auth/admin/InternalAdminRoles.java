package in.craves.auth.admin;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class InternalAdminRoles {
    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";
    public static final String SUPPORT_ADMIN = "SUPPORT_ADMIN";
    public static final String PAYMENTS_ADMIN = "PAYMENTS_ADMIN";
    public static final String OPERATIONS_ADMIN = "OPERATIONS_ADMIN";
    public static final String CHEF_ADMIN = "CHEF_ADMIN";
    public static final String COMPLIANCE_ADMIN = "COMPLIANCE_ADMIN";
    public static final String SUBSCRIPTION_ADMIN = "SUBSCRIPTION_ADMIN";
    public static final String NOTIFICATION_ADMIN = "NOTIFICATION_ADMIN";
    public static final String AUDIT_ADMIN = "AUDIT_ADMIN";

    private static final List<RoleDefinition> CATALOG = List.of(
        new RoleDefinition(PLATFORM_ADMIN, "Full internal administration and role management"),
        new RoleDefinition(SUPPORT_ADMIN, "Read-only customer support investigations and account status"),
        new RoleDefinition(PAYMENTS_ADMIN, "Payment, refund, earnings and settlement operations"),
        new RoleDefinition(OPERATIONS_ADMIN, "Order, delivery and launch-policy operations"),
        new RoleDefinition(CHEF_ADMIN, "Chef application decisions and onboarding operations"),
        new RoleDefinition(COMPLIANCE_ADMIN, "Chef KYC and compliance document review"),
        new RoleDefinition(SUBSCRIPTION_ADMIN, "Subscription plan, schedule and lifecycle operations"),
        new RoleDefinition(NOTIFICATION_ADMIN, "Notification delivery recovery operations"),
        new RoleDefinition(AUDIT_ADMIN, "Read-only operational and internal-role audit access")
    );

    private static final Set<String> CODES = Set.of(
        PLATFORM_ADMIN, SUPPORT_ADMIN, PAYMENTS_ADMIN, OPERATIONS_ADMIN, CHEF_ADMIN,
        COMPLIANCE_ADMIN, SUBSCRIPTION_ADMIN, NOTIFICATION_ADMIN, AUDIT_ADMIN
    );

    private InternalAdminRoles() {
    }

    public static List<RoleDefinition> catalog() {
        return CATALOG;
    }

    public static Set<String> codes() {
        return CODES;
    }

    public static String normalize(String role) {
        String normalized = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        if (!CODES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported internal administrator role: " + normalized);
        }
        return normalized;
    }

    public record RoleDefinition(String code, String description) {
    }
}
