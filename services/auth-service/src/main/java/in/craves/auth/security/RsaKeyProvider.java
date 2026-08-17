package in.craves.auth.security;

import in.craves.auth.config.JwtProperties;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class RsaKeyProvider {
    private final PrivateKey privateKey;
    private final PublicKey publicKey;

    public RsaKeyProvider(JwtProperties properties) {
        try {
            if (StringUtils.hasText(properties.getPrivateKeyPemBase64())) {
                this.privateKey = parsePrivateKey(properties.getPrivateKeyPemBase64());
                this.publicKey = StringUtils.hasText(properties.getPublicKeyPemBase64())
                    ? parsePublicKey(properties.getPublicKeyPemBase64())
                    : derivePublicKey(this.privateKey);
            } else if (properties.isAllowGeneratedLocalKeys()) {
                KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                KeyPair pair = generator.generateKeyPair();
                this.privateKey = pair.getPrivate();
                this.publicKey = pair.getPublic();
            } else {
                throw new IllegalStateException("CRAVES_JWT_PRIVATE_KEY_PEM_BASE64 is required");
            }
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to initialize JWT RSA keys", ex);
        }
    }

    public PrivateKey privateKey() {
        return privateKey;
    }

    public PublicKey publicKey() {
        return publicKey;
    }

    private static PrivateKey parsePrivateKey(String pemBase64) throws Exception {
        byte[] pemBytes = Base64.getDecoder().decode(pemBase64);
        String pem = new String(pemBytes, StandardCharsets.UTF_8);
        String key = pem
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(key);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }

    private static PublicKey parsePublicKey(String pemBase64) throws Exception {
        byte[] pemBytes = Base64.getDecoder().decode(pemBase64);
        String pem = new String(pemBytes, StandardCharsets.UTF_8);
        String key = pem
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(key);
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(der));
    }

    private static PublicKey derivePublicKey(PrivateKey privateKey) throws Exception {
        if (!(privateKey instanceof RSAPrivateCrtKey rsaPrivateKey)) {
            throw new IllegalStateException("Public key is required when the private key is not an RSA CRT key");
        }
        RSAPublicKeySpec publicKeySpec = new RSAPublicKeySpec(rsaPrivateKey.getModulus(), rsaPrivateKey.getPublicExponent());
        return KeyFactory.getInstance("RSA").generatePublic(publicKeySpec);
    }
}
