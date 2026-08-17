package in.craves.auth.api;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record IdentityResponse(
    UUID id,
    String firebaseUid,
    String phoneNumber,
    String email,
    boolean emailVerified,
    String displayName,
    String status,
    List<String> roles,
    Instant lastLoginAt
) {
}
