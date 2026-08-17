package in.craves.auth.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "craves.jwt")
public class JwtProperties {
    private String issuer = "https://api.craves.in/auth";
    private String audience = "craves-api";
    private Duration accessTokenTtl = Duration.ofMinutes(15);
    private Duration refreshTokenTtl = Duration.ofDays(30);
    private String privateKeyPemBase64;
    private String publicKeyPemBase64;
    private boolean allowGeneratedLocalKeys = false;

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

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public Duration getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    public void setRefreshTokenTtl(Duration refreshTokenTtl) {
        this.refreshTokenTtl = refreshTokenTtl;
    }

    public String getPrivateKeyPemBase64() {
        return privateKeyPemBase64;
    }

    public void setPrivateKeyPemBase64(String privateKeyPemBase64) {
        this.privateKeyPemBase64 = privateKeyPemBase64;
    }

    public String getPublicKeyPemBase64() {
        return publicKeyPemBase64;
    }

    public void setPublicKeyPemBase64(String publicKeyPemBase64) {
        this.publicKeyPemBase64 = publicKeyPemBase64;
    }

    public boolean isAllowGeneratedLocalKeys() {
        return allowGeneratedLocalKeys;
    }

    public void setAllowGeneratedLocalKeys(boolean allowGeneratedLocalKeys) {
        this.allowGeneratedLocalKeys = allowGeneratedLocalKeys;
    }
}
