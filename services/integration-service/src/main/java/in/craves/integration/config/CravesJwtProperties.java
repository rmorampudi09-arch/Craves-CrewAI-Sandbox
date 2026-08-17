package in.craves.integration.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class CravesJwtProperties {
    private final String issuer;
    private final String audience;
    private final String verificationPemBase64;

    public CravesJwtProperties(
        @Value("${CRAVES_JWT_ISSUER:https://api.craves.in/auth}") String issuer,
        @Value("${CRAVES_JWT_AUDIENCE:craves-api}") String audience,
        @Value("${CRAVES_JWT_VERIFICATION_PEM_BASE64:}") String verificationPemBase64
    ) {
        this.issuer = issuer;
        this.audience = audience;
        this.verificationPemBase64 = verificationPemBase64;
    }

    public String getIssuer() { return issuer; }
    public String getAudience() { return audience; }
    public String getVerificationPemBase64() { return verificationPemBase64; }
}
