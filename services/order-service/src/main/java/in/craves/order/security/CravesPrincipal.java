package in.craves.order.security;

import java.util.Set;
import java.util.UUID;

public record CravesPrincipal(UUID identityId, String phoneNumber, Set<String> roles) {
    public boolean hasRole(String role) {
        return roles != null && roles.stream().anyMatch(existing -> existing.equalsIgnoreCase(role));
    }

    public boolean hasAnyRole(String... allowedRoles) {
        if (allowedRoles == null) {
            return false;
        }
        for (String allowedRole : allowedRoles) {
            if (hasRole(allowedRole)) {
                return true;
            }
        }
        return false;
    }
}
