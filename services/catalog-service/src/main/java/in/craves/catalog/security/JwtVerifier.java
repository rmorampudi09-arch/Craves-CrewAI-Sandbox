package in.craves.catalog.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.catalog.config.CravesJwtProperties;
import in.craves.catalog.exception.ApiException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class JwtVerifier {
    private final CravesJwtProperties properties;
    private final ObjectMapper objectMapper;

    public JwtVerifier(CravesJwtProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public CravesPrincipal verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw ApiException.unauthorized("INVALID_TOKEN", "Access token is invalid");
            }

            String signingInput = parts[0] + "." + parts[1];
            byte[] signatureBytes = Base64.getUrlDecoder().decode(pad(parts[2]));
            RSAPublicKey publicKey = publicKey();

            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey);
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            if (!signature.verify(signatureBytes)) {
                throw ApiException.unauthorized("INVALID_TOKEN_SIGNATURE", "Access token signature is invalid");
            }

            Map<String, Object> payload = objectMapper.readValue(
                Base64.getUrlDecoder().decode(pad(parts[1])),
                new TypeReference<>() {
                }
            );

            validateClaims(payload);
            UUID identityId = UUID.fromString(String.valueOf(payload.get("sub")));
            String phoneNumber = value(payload.get("phone_number"));
            return new CravesPrincipal(identityId, phoneNumber, roles(payload.get("roles")));
        } catch (ApiException ex) {
            throw ex;
        } catch (Exception ex) {
            throw ApiException.unauthorized("INVALID_TOKEN", "Access token is invalid");
        }
    }

    private void validateClaims(Map<String, Object> payload) {
        if (!properties.getIssuer().equals(value(payload.get("iss")))) {
            throw ApiException.unauthorized("INVALID_TOKEN_ISSUER", "Access token issuer is invalid");
        }
        if (!audienceMatches(payload.get("aud"))) {
            throw ApiException.unauthorized("INVALID_TOKEN_AUDIENCE", "Access token audience is invalid");
        }
        Object exp = payload.get("exp");
        if (!(exp instanceof Number number) || Instant.ofEpochSecond(number.longValue()).isBefore(Instant.now())) {
            throw ApiException.unauthorized("TOKEN_EXPIRED", "Access token has expired");
        }
    }

    private boolean audienceMatches(Object aud) {
        if (aud instanceof String value) {
            return properties.getAudience().equals(value);
        }
        if (aud instanceof List<?> values) {
            return values.stream().anyMatch(value -> properties.getAudience().equals(String.valueOf(value)));
        }
        return false;
    }

    private RSAPublicKey publicKey() throws Exception {
        String configured = properties.getVerificationPemBase64();
        if (!StringUtils.hasText(configured)) {
            throw ApiException.unauthorized("TOKEN_VERIFICATION_NOT_CONFIGURED", "Token verification key is not configured");
        }
        String normalized = configured.trim();
        normalized += "=".repeat((4 - normalized.length() % 4) % 4);
        String pem = new String(Base64.getDecoder().decode(normalized), StandardCharsets.UTF_8);
        String body = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
        byte[] keyBytes = Base64.getDecoder().decode(body);
        return (RSAPublicKey) KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
    }

    private static Set<String> roles(Object value) {
        Set<String> result = new LinkedHashSet<>();
        if (value instanceof List<?> list) {
            for (Object role : list) {
                if (role != null) {
                    result.add(String.valueOf(role));
                }
            }
        }
        return result;
    }

    private static String value(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static String pad(String value) {
        return value + "=".repeat((4 - value.length() % 4) % 4);
    }
}
