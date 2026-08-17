package in.craves.auth.security;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RedisAuthAbuseProtectionFilterTest {
    @Test
    void disabledFilterDoesNotRequireProductionLimits() {
        RedisAuthAbuseProtectionFilter filter = new RedisAuthAbuseProtectionFilter(
            null,
            false,
            0,
            0,
            60,
            false,
            "craves:auth:rate"
        );

        assertThatCode(filter::validate).doesNotThrowAnyException();
    }

    @Test
    void enabledFilterRequiresExplicitPositiveLimits() {
        RedisAuthAbuseProtectionFilter filter = new RedisAuthAbuseProtectionFilter(
            null,
            true,
            0,
            0,
            60,
            false,
            "craves:auth:rate"
        );

        assertThatThrownBy(filter::validate)
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Explicit positive exchange and refresh rate limits");
    }

    @Test
    void enabledFilterAcceptsReviewedValues() {
        RedisAuthAbuseProtectionFilter filter = new RedisAuthAbuseProtectionFilter(
            null,
            true,
            10,
            20,
            60,
            false,
            "craves:auth:rate"
        );

        assertThatCode(filter::validate).doesNotThrowAnyException();
    }
}
