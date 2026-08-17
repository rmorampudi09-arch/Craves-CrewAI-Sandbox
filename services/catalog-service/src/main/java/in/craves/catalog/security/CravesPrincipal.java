package in.craves.catalog.security;

import java.util.Set;
import java.util.UUID;

public record CravesPrincipal(UUID identityId, String phoneNumber, Set<String> roles) {
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
