package in.craves.auth.config;

import in.craves.auth.security.RedisAuthAbuseProtectionFilter;
import java.util.ArrayList;
import java.util.List;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class ProductionReadinessValidator implements ApplicationListener<ApplicationReadyEvent> {
    private final Environment environment;
    private final JwtProperties jwtProperties;
    private final FirebaseProperties firebaseProperties;
    private final InternalServiceProperties internalServiceProperties;
    private final ProductionReadinessProperties productionReadinessProperties;
    private final ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider;
    private final ObjectProvider<RedisAuthAbuseProtectionFilter> redisAuthAbuseProtectionFilterProvider;

    public ProductionReadinessValidator(
        Environment environment,
        JwtProperties jwtProperties,
        FirebaseProperties firebaseProperties,
        InternalServiceProperties internalServiceProperties,
        ProductionReadinessProperties productionReadinessProperties,
        ObjectProvider<RedisConnectionFactory> redisConnectionFactoryProvider,
        ObjectProvider<RedisAuthAbuseProtectionFilter> redisAuthAbuseProtectionFilterProvider
    ) {
        this.environment = environment;
        this.jwtProperties = jwtProperties;
        this.firebaseProperties = firebaseProperties;
        this.internalServiceProperties = internalServiceProperties;
        this.productionReadinessProperties = productionReadinessProperties;
        this.redisConnectionFactoryProvider = redisConnectionFactoryProvider;
        this.redisAuthAbuseProtectionFilterProvider = redisAuthAbuseProtectionFilterProvider;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (!isProductionProfile()) {
            return;
        }
        List<String> violations = new ArrayList<>();
        if (productionReadinessProperties.isFailOnMissingSecrets()) {
            requireText(violations, "craves.jwt.issuer", jwtProperties.getIssuer());
            requireText(violations, "craves.jwt.audience", jwtProperties.getAudience());
            requireText(violations, "craves.jwt.private-key-pem-base64", jwtProperties.getPrivateKeyPemBase64());
            requireText(violations, "craves.firebase.project-id", firebaseProperties.getProjectId());
            requireText(violations, "craves.firebase.credentials-json-base64", firebaseProperties.getCredentialsJsonBase64());
            requireText(violations, "craves.internal.service-secret", internalServiceProperties.getServiceSecret());
        }
        if (jwtProperties.isAllowGeneratedLocalKeys()) {
            violations.add("Generated local JWT keys must be disabled in production");
        }
        if (productionReadinessProperties.isRequireRedis()) {
            if (redisConnectionFactoryProvider.getIfAvailable() == null) {
                violations.add("Redis connection factory is required in production");
            }
            if (redisAuthAbuseProtectionFilterProvider.getIfAvailable() == null) {
                violations.add("Redis-backed auth abuse protection filter must be active in production");
            }
        }
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Auth service production readiness validation failed: " + String.join("; ", violations));
        }
    }

    private boolean isProductionProfile() {
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                return true;
            }
        }
        return false;
    }

    private void requireText(List<String> violations, String property, String value) {
        if (!StringUtils.hasText(value)) {
            violations.add("Missing required production property: " + property);
        }
    }
}
