package in.craves.auth.api;

import java.time.Instant;

public record AuthTokenResponse(
    String tokenType,
    String accessToken,
    long expiresIn,
    String refreshToken,
    Instant refreshTokenExpiresAt,
    IdentityResponse identity
) {
    public static AuthTokenResponse create(String accessToken, long expiresIn, String refreshToken, Instant refreshTokenExpiresAt, IdentityResponse identity) {
        return new AuthTokenResponse("Bearer", accessToken, expiresIn, refreshToken, refreshTokenExpiresAt, identity);
    }
}
