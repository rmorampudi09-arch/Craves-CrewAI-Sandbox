package in.craves.auth.api;

import java.util.UUID;

public record InternalIdentityEmailResponse(
    UUID identityId,
    String email,
    boolean emailVerified,
    String status
) {
}
