package in.craves.auth.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.craves.auth.security.RedisAuthAbuseProtectionFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;

class ProductionReadinessValidatorTest {
    @Test
    void nonProductionProfileSkipsStrictValidation() {
        JwtProperties jwtProperties = new JwtProperties();
        FirebaseProperties firebaseProperties = new FirebaseProperties();
        InternalServiceProperties internalServiceProperties = new InternalServiceProperties();
        ProductionReadinessProperties productionReadinessProperties = new ProductionReadinessProperties();

        ProductionReadinessValidator validator = new ProductionReadinessValidator(
            environment("local"),
            jwtProperties,
            firebaseProperties,
            internalServiceProperties,
            productionReadinessProperties,
            emptyProvider(),
            emptyProvider()
        );

        assertThatCode(() -> validator.onApplicationEvent(applicationReadyEvent()))
            .doesNotThrowAnyException();
    }

    @Test
    void productionProfileFailsWhenRequiredSecretsMissing() {
        JwtProperties jwtProperties = new JwtProperties();
        FirebaseProperties firebaseProperties = new FirebaseProperties();
        InternalServiceProperties internalServiceProperties = new InternalServiceProperties();
        ProductionReadinessProperties productionReadinessProperties = new ProductionReadinessProperties();

        ProductionReadinessValidator validator = new ProductionReadinessValidator(
            environment("prod"),
            jwtProperties,
            firebaseProperties,
            internalServiceProperties,
            productionReadinessProperties,
            emptyProvider(),
            emptyProvider()
        );

        assertThatThrownBy(() -> validator.onApplicationEvent(applicationReadyEvent()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Missing required production property: craves.jwt.private-key-pem-base64")
            .hasMessageContaining("Redis connection factory is required in production");
    }

    @Test
    void productionProfilePassesWhenSecretsAndRedisRequirementsPresent() {
        JwtProperties jwtProperties = new JwtProperties();
        jwtProperties.setIssuer("https://api.craves.in/auth");
        jwtProperties.setAudience("craves-api");
        jwtProperties.setPrivateKeyPemBase64("cHJpdmF0ZQ==");

        FirebaseProperties firebaseProperties = new FirebaseProperties();
        firebaseProperties.setProjectId("craves-prod");
        firebaseProperties.setCredentialsJsonBase64("e30=");

        InternalServiceProperties internalServiceProperties = new InternalServiceProperties();
        internalServiceProperties.setServiceSecret("internal-secret");

        ProductionReadinessProperties productionReadinessProperties = new ProductionReadinessProperties();

        ProductionReadinessValidator validator = new ProductionReadinessValidator(
            environment("production"),
            jwtProperties,
            firebaseProperties,
            internalServiceProperties,
            productionReadinessProperties,
            providerOf(new RedisConnectionFactory() {
                @Override
                public org.springframework.data.redis.connection.RedisConnection getConnection() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public org.springframework.data.redis.connection.RedisClusterConnection getClusterConnection() {
                    throw new UnsupportedOperationException();
                }

                @Override
                public boolean getConvertPipelineAndTxResults() {
                    return false;
                }

                @Override
                public org.springframework.data.redis.connection.RedisSentinelConnection getSentinelConnection() {
                    throw new UnsupportedOperationException();
                }
            }),
            providerOf(new RedisAuthAbuseProtectionFilter(null, true, 10, 20, 60, false, "craves:auth:rate"))
        );

        assertThatCode(() -> validator.onApplicationEvent(applicationReadyEvent()))
            .doesNotThrowAnyException();
    }

    private Environment environment(String... profiles) {
        return new ApplicationContextRunner()
            .withPropertyValues()
            .run(context -> {
            })
            .getSourceApplicationContext()
            .getEnvironment();
    }

    private ApplicationReadyEvent applicationReadyEvent() {
        ConfigurableApplicationContext context = new ApplicationContextRunner().run(context -> {}).getSourceApplicationContext();
        return new ApplicationReadyEvent(new org.springframework.boot.SpringApplication(Object.class), new String[0], context);
    }

    private <T> ObjectProvider<T> emptyProvider() {
        return new SimpleObjectProvider<>(null);
    }

    private <T> ObjectProvider<T> providerOf(T value) {
        return new SimpleObjectProvider<>(value);
    }

    private static final class SimpleObjectProvider<T> implements ObjectProvider<T> {
        private final T value;

        private SimpleObjectProvider(T value) {
            this.value = value;
        }

        @Override
        public T getObject(Object... args) {
            return value;
        }

        @Override
        public T getIfAvailable() {
            return value;
        }

        @Override
        public T getIfUnique() {
            return value;
        }

        @Override
        public T getObject() {
            return value;
        }

        @Override
        public java.util.Iterator<T> iterator() {
            return value == null ? java.util.List.<T>of().iterator() : java.util.List.of(value).iterator();
        }

        @Override
        public java.util.stream.Stream<T> stream() {
            return value == null ? java.util.stream.Stream.empty() : java.util.stream.Stream.of(value);
        }

        @Override
        public java.util.stream.Stream<T> orderedStream() {
            return stream();
        }

        @Override
        public ResolvableType getResolvableType() {
            return ResolvableType.forClass(Object.class);
        }
    }
}
