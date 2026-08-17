package in.craves.integration.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.integration.config.CravesJwtProperties;
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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

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
                throw unauthorized("Access token is invalid");
            }
            String signingInput = parts[0] + "." + parts[1];
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey());
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            if (!signature.verify(Base64.getUrlDecoder().decode(pad(parts[2])))) {
                throw unauthorized("Access token signature is invalid");
            }
            Map<String, Object> payload = objectMapper.readValue(
                Base64.getUrlDecoder().decode(pad(parts[1])),
                new TypeReference<>() { }
            );
            validateClaims(payload);
            return new CravesPrincipal(
                UUID.fromString(String.valueOf(payload.get("sub"))),
                value(payload.get("phone_number")),
                roles(payload.get("roles"))
            );
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (Exception exception) {
            throw unauthorized("Access token is invalid");
        }
    }

    private void validateClaims(Map<String, Object> payload) {
        if (!properties.getIssuer().equals(value(payload.get("iss")))) {
            throw unauthorized("Access token issuer is invalid");
        }
        if (!audienceMatches(payload.get("aud"))) {
            throw unauthorized("Access token audience is invalid");
        }
        Object exp = payload.get("exp");
        if (!(exp instanceof Number number) || Instant.ofEpochSecond(number.longValue()).isBefore(Instant.now())) {
            throw unauthorized("Access token has expired");
        }
    }

    private boolean audienceMatches(Object audience) {
        if (audience instanceof String text) {
            return properties.getAudience().equals(text);
        }
        if (audience instanceof List<?> values) {
            return values.stream().anyMatch(value -> properties.getAudience().equals(String.valueOf(value)));
        }
        return false;
    }

    private RSAPublicKey publicKey() throws Exception {
        String configured = properties.getVerificationPemBase64();
        if (!StringUtils.hasText(configured)) {
            throw unauthorized("Token verification key is not configured");
        }
        String normalized = configured.trim();
        normalized += "=".repeat((4 - normalized.length() % 4) % 4);
        String pem = new String(Base64.getDecoder().decode(normalized), StandardCharsets.UTF_8);
        String body = pem.replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
        return (RSAPublicKey) KeyFactory.getInstance("RSA")
            .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(body)));
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

    private static ResponseStatusException unauthorized(String message) {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, message);
    }
}
