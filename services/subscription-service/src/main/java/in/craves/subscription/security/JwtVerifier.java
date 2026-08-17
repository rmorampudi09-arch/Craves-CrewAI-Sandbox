package in.craves.subscription.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.subscription.config.CravesJwtProperties;
import in.craves.subscription.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class JwtVerifier {
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();

    private final CravesJwtProperties properties;
    private final ObjectMapper objectMapper;
    private volatile RSAPublicKey cachedVerificationKey;

    public JwtVerifier(CravesJwtProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public CurrentUser verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw ApiException.unauthorized("INVALID_ACCESS_TOKEN", "Invalid access token format");
            }

            String signingInput = parts[0] + "." + parts[1];
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(verificationKey());
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            if (!signature.verify(URL_DECODER.decode(parts[2]))) {
                throw ApiException.unauthorized("INVALID_ACCESS_TOKEN", "Invalid access token signature");
            }

            Map<String, Object> claims = objectMapper.readValue(
                URL_DECODER.decode(parts[1]),
                new TypeReference<Map<String, Object>>() {
                }
            );

            String issuer = stringClaim(claims, "iss");
            String audience = stringClaim(claims, "aud");
            if (!properties.getIssuer().equals(issuer)) {
                throw ApiException.unauthorized("INVALID_ACCESS_TOKEN", "Invalid access token issuer");
            }
            if (!properties.getAudience().equals(audience)) {
                throw ApiException.unauthorized("INVALID_ACCESS_TOKEN", "Invalid access token audience");
            }

            Instant expiresAt = Instant.ofEpochSecond(longClaim(claims, "exp"));
            if (!expiresAt.isAfter(Instant.now())) {
                throw ApiException.unauthorized("ACCESS_TOKEN_EXPIRED", "Access token has expired");
            }

            UUID identityId = UUID.fromString(stringClaim(claims, "sub"));
            String firebaseUid = stringClaim(claims, "firebase_uid");
            String phoneNumber = stringClaim(claims, "phone_number");
            List<String> roles = objectMapper.convertValue(claims.get("roles"), new TypeReference<List<String>>() {
            });

            return new CurrentUser(identityId, firebaseUid, phoneNumber, roles == null ? List.of() : roles);
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiException.unauthorized("INVALID_ACCESS_TOKEN", "Invalid access token");
        }
    }

    private RSAPublicKey verificationKey() throws Exception {
        RSAPublicKey existing = cachedVerificationKey;
        if (existing != null) {
            return existing;
        }
        if (!StringUtils.hasText(properties.getVerificationPemBase64())) {
            throw ApiException.unauthorized("JWT_VERIFICATION_NOT_CONFIGURED", "JWT verification is not configured");
        }

        String pem = new String(Base64.getDecoder().decode(properties.getVerificationPemBase64()), StandardCharsets.UTF_8);
        String normalized = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
        byte[] decoded = Base64.getMimeDecoder().decode(normalized);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(decoded);
        cachedVerificationKey = (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(spec);
        return cachedVerificationKey;
    }

    private static String stringClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        if (value == null) {
            throw ApiException.unauthorized("INVALID_ACCESS_TOKEN", "Missing access token claim: " + name);
        }
        return String.valueOf(value);
    }

    private static long longClaim(Map<String, Object> claims, String name) {
        Object value = claims.get(name);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            throw ApiException.unauthorized("INVALID_ACCESS_TOKEN", "Missing access token claim: " + name);
        }
        return Long.parseLong(String.valueOf(value));
    }
}
