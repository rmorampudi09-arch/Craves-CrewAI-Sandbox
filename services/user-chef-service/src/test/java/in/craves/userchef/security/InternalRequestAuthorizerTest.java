package in.craves.userchef.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import in.craves.userchef.config.AuthInternalClientProperties;
import in.craves.userchef.exception.ApiException;
import org.junit.jupiter.api.Test;

class InternalRequestAuthorizerTest {
    @Test
    void acceptsMatchingSecret() {
        InternalRequestAuthorizer authorizer = authorizer("shared-secret");

        assertThatCode(() -> authorizer.requireValid("shared-secret")).doesNotThrowAnyException();
    }

    @Test
    void rejectsInvalidSecret() {
        InternalRequestAuthorizer authorizer = authorizer("shared-secret");

        assertThatThrownBy(() -> authorizer.requireValid("wrong-secret"))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                org.assertj.core.api.Assertions.assertThat(exception.getStatus()).isEqualTo(401);
                org.assertj.core.api.Assertions.assertThat(exception.getCode())
                    .isEqualTo("INVALID_INTERNAL_SERVICE_CREDENTIAL");
            });
    }

    @Test
    void failsClosedWhenSecretIsNotConfigured() {
        InternalRequestAuthorizer authorizer = authorizer("");

        assertThatThrownBy(() -> authorizer.requireValid("anything"))
            .isInstanceOfSatisfying(ApiException.class, exception -> {
                org.assertj.core.api.Assertions.assertThat(exception.getStatus()).isEqualTo(503);
                org.assertj.core.api.Assertions.assertThat(exception.getCode())
                    .isEqualTo("INTERNAL_AUTH_NOT_CONFIGURED");
            });
    }

    private static InternalRequestAuthorizer authorizer(String secret) {
        AuthInternalClientProperties properties = new AuthInternalClientProperties();
        properties.setServiceSecret(secret);
        return new InternalRequestAuthorizer(properties);
    }
}
