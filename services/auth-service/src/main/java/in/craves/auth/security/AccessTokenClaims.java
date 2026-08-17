package in.craves.auth.security;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AccessTokenClaims(
    UUID identityId,
    String firebaseUid,
    String phoneNumber,
    List<String> roles,
    long tokenVersion,
    Instant issuedAt,
    Instant expiresAt
) {
}
