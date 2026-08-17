package in.craves.catalog.security;

import java.util.List;
import java.util.UUID;

public record CurrentUser(
    UUID identityId,
    String phoneNumber,
    List<String> roles
) {
    public boolean hasRole(String role) {
        return roles != null && roles.contains(role);
    }
}
