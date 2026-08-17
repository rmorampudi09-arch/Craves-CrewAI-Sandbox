package in.craves.auth.api;

import java.util.List;
import java.util.UUID;

public record InternalRoleGrantResponse(
    UUID identityId,
    List<String> roles
) {
}
