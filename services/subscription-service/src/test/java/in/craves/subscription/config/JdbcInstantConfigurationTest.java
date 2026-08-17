package in.craves.subscription.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class JdbcInstantConfigurationTest {

    @Test
    void convertsInstantToJdbcTimestamp() {
        Instant instant = Instant.parse("2026-08-14T00:11:22.135Z");

        Object normalized = JdbcInstantConfiguration.normalizeParameter(instant);

        assertThat(normalized).isInstanceOf(Timestamp.class);
        assertThat(((Timestamp) normalized).toInstant()).isEqualTo(instant);
    }

    @Test
    void leavesNonInstantParametersUnchanged() {
        String value = "HOLD";

        assertThat(JdbcInstantConfiguration.normalizeParameter(value)).isSameAs(value);
        assertThat(JdbcInstantConfiguration.normalizeParameter(null)).isNull();
    }
}
