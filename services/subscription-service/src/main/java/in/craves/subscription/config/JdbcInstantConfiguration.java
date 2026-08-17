package in.craves.subscription.config;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;

@Configuration(proxyBeanMethods = false)
public class JdbcInstantConfiguration {

    @Bean
    @Primary
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new InstantNormalizingJdbcTemplate(dataSource);
    }

    static Object normalizeParameter(Object value) {
        return value instanceof Instant instant ? Timestamp.from(instant) : value;
    }

    static final class InstantNormalizingJdbcTemplate extends JdbcTemplate {
        InstantNormalizingJdbcTemplate(DataSource dataSource) {
            super(dataSource);
        }

        @Override
        protected PreparedStatementSetter newArgPreparedStatementSetter(Object[] args) {
            Object[] normalized = args == null
                ? null
                : Arrays.stream(args).map(JdbcInstantConfiguration::normalizeParameter).toArray();
            return super.newArgPreparedStatementSetter(normalized);
        }
    }
}
