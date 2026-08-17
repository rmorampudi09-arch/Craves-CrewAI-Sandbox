package in.craves.notification.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

@Component
public class JwtVerifier {
    private final ObjectMapper objectMapper;
    private final String issuer;
    private final String audience;
    private final String verificationPemBase64;

    public JwtVerifier(ObjectMapper objectMapper,
                       @Value("${craves.jwt.issuer:craves-auth-service}") String issuer,
                       @Value("${craves.jwt.audience:craves-clients}") String audience,
                       @Value("${craves.jwt.verification-pem-base64:}") String verificationPemBase64) {
        this.objectMapper = objectMapper;
        this.issuer = issuer;
        this.audience = audience;
        this.verificationPemBase64 = verificationPemBase64;
    }

    public CravesPrincipal verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access token is invalid");
            }
            String signingInput = parts[0] + "." + parts[1];
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(publicKey());
            signature.update(signingInput.getBytes(StandardCharsets.UTF_8));
            if (!signature.verify(Base64.getUrlDecoder().decode(pad(parts[2])))) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access token signature is invalid");
            }
            Map<String, Object> payload = objectMapper.readValue(Base64.getUrlDecoder().decode(pad(parts[1])), new TypeReference<>() {});
            validateClaims(payload);
            return new CravesPrincipal(UUID.fromString(String.valueOf(payload.get("sub"))), value(payload.get("phone_number")), roles(payload.get("roles")));
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access token is invalid");
        }
    }

    private void validateClaims(Map<String, Object> payload) {
        if (!issuer.equals(value(payload.get("iss")))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access token issuer is invalid");
        }
        if (!audienceMatches(payload.get("aud"))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access token audience is invalid");
        }
        Object exp = payload.get("exp");
        if (!(exp instanceof Number number) || Instant.ofEpochSecond(number.longValue()).isBefore(Instant.now())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access token has expired");
        }
    }

    private boolean audienceMatches(Object aud) {
        if (aud instanceof String text) {
            return audience.equals(text);
        }
        if (aud instanceof List<?> values) {
            return values.stream().anyMatch(value -> audience.equals(String.valueOf(value)));
        }
        return false;
    }

    private RSAPublicKey publicKey() throws Exception {
        if (!StringUtils.hasText(verificationPemBase64)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token verification key is not configured");
        }
        String normalized = verificationPemBase64.trim();
        normalized += "=".repeat((4 - normalized.length() % 4) % 4);
        String pem = new String(Base64.getDecoder().decode(normalized), StandardCharsets.UTF_8);
        String body = pem.replace("-----BEGIN PUBLIC KEY-----", "").replace("-----END PUBLIC KEY-----", "").replaceAll("\\s", "");
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
