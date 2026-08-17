package in.craves.subscription.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "craves.jwt")
public class CravesJwtProperties {
    private String issuer = "https://api.craves.in/auth";
    private String audience = "craves-api";
    private String verificationPemBase64;

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public String getVerificationPemBase64() {
        return verificationPemBase64;
    }

    public void setVerificationPemBase64(String verificationPemBase64) {
        this.verificationPemBase64 = verificationPemBase64;
    }
}
