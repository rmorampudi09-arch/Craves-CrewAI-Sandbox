package in.craves.auth.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.auth.config.JwtProperties;
import in.craves.auth.domain.AuthIdentity;
import in.craves.auth.exception.AuthException;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class CravesJwtService {
    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final JwtProperties jwtProperties;
    private final RsaKeyProvider keyProvider;
    private final ObjectMapper objectMapper;

    public CravesJwtService(JwtProperties jwtProperties, RsaKeyProvider keyProvider, ObjectMapper objectMapper) {
        this.jwtProperties = jwtProperties;
        this.keyProvider = keyProvider;
        this.objectMapper = objectMapper;
    }

    public String issueAccessToken(AuthIdentity identity, List<String> roles) {
        try {
            Instant now = Instant.now();
            Instant expiresAt = now.plus(jwtProperties.getAccessTokenTtl());

            Map<String, Object> header = new LinkedHashMap<>();
            header.put("alg", "RS256");
            header.put("typ", "JWT");

            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("iss", jwtProperties.getIssuer());
            claims.put("aud", jwtProperties.getAudience());
            claims.put("sub", identity.getId().toString());
            claims.put("iat", now.getEpochSecond());
            claims.put("exp", expiresAt.getEpochSecond());
            claims.put("jti", UUID.randomUUID().toString());
            claims.put("firebase_uid", identity.getFirebaseUid());
            claims.put("phone_number", identity.getPhoneNumber());
            claims.put("roles", roles);
            claims.put("token_version", identity.getTokenVersion());

            String headerPart = encodeJson(header);
            String claimPart = encodeJson(claims);
            String signingInput = headerPart + "." + claimPart;
            String signaturePart = sign(signingInput);
            return signingInput + "." + signaturePart;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to issue access token", ex);
        }
    }

    public AccessTokenClaims verifyAccessToken(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw AuthException.unauthorized("INVALID_ACCESS_TOKEN", "Invalid access token format");
            }

            String signingInput = parts[0] + "." + parts[1];
            if (!verifySignature(signingInput, parts[2])) {
                throw AuthException.unauthorized("INVALID_ACCESS_TOKEN", "Invalid access token signature");
            }

            Map<String, Object> claims = objectMapper.readValue(
                URL_DECODER.decode(parts[1]),
                new TypeReference<Map<String, Object>>() {
                }
            );

            String issuer = stringClaim(claims, "iss");
            String audience = stringClaim(claims, "aud");
            long expiresAtEpoch = longClaim(claims, "exp");
            long issuedAtEpoch = longClaim(claims, "iat");

            if (!jwtProperties.getIssuer().equals(issuer)) {
                throw AuthException.unauthorized("INVALID_ACCESS_TOKEN", "Invalid access token issuer");
            }
            if (!jwtProperties.getAudience().equals(audience)) {
                throw AuthException.unauthorized("INVALID_ACCESS_TOKEN", "Invalid access token audience");
            }
            Instant expiresAt = Instant.ofEpochSecond(expiresAtEpoch);
            if (!expiresAt.isAfter(Instant.now())) {
                throw AuthException.unauthorized("ACCESS_TOKEN_EXPIRED", "Access token has expired");
            }

            UUID identityId = UUID.fromString(stringClaim(claims, "sub"));
            String firebaseUid = stringClaim(claims, "firebase_uid");
            String phoneNumber = stringClaim(claims, "phone_number");
            List<String> roles = objectMapper.convertValue(claims.get("roles"), new TypeReference<List<String>>() {
            });
            long tokenVersion = longClaim(claims, "token_version");

            return new AccessTokenClaims(
                identityId,
                firebaseUid,
                phoneNumber,
                roles,
                tokenVersion,
                Instant.ofEpochSecond(issuedAtEpoch),
                expiresAt
            );
        } catch (AuthException ex) {
            throw ex;
        } catch (Exception ex) {
            throw AuthException.unauthorized("INVALID_ACCESS_TOKEN", "Invalid access token");
        }
    }

    private String encodeJson(Map<String, Object> value) throws Exception {
        return URL_ENCODER.encodeToString(objectMapper.writeValueAsBytes(value));
    }

    private String sign(String signingInput) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(keyProvider.privateKey());
        signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
        return URL_ENCODER.encodeToString(signature.sign());
    }

    private boolean verifySignature(String signingInput, String signaturePart) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initVerify(keyProvider.publicKey());
        signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
        return signature.verify(URL_DECODER.decode(signaturePart));
    }

    private static String stringClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        if (value == null) {
            throw AuthException.unauthorized("INVALID_ACCESS_TOKEN", "Missing access token claim: " + name);
        }
        return String.valueOf(value);
    }

    private static long longClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            throw AuthException.unauthorized("INVALID_ACCESS_TOKEN", "Missing access token claim: " + name);
        }
        return Long.parseLong(String.valueOf(value));
    }
}
