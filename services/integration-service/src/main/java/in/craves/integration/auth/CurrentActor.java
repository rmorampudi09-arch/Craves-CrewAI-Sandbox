package in.craves.integration.auth;

import java.util.Set;
import java.util.UUID;

public record CurrentActor(UUID identityId, String phoneNumber, Set<String> roles) {
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
