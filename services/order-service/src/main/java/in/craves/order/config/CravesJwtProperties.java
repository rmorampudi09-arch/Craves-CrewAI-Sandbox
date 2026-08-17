package in.craves.order.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "craves.jwt")
public class CravesJwtProperties {
    private String issuer;
    private String audience;
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
