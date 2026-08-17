package in.craves.subscription;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;

class FlywayBootstrapSafetyTest {

    @Test
    void automaticBaselineMustRemainBelowFirstVersionedMigration() throws IOException {
        String applicationYaml = resource("application.yml");

        assertThat(applicationYaml)
            .contains("baseline-on-migrate: true")
            .contains("baseline-version: 0");

        assertThat(MigrationVersion.fromVersion("0"))
            .isLessThan(MigrationVersion.fromVersion("1"));
    }

    @Test
    void versionOnePointOneRepairsAPreviouslySkippedCoreBootstrap() throws IOException {
        String repair = resource("db/migration/V1_1__subscription_core_baseline_repair.sql");

        assertThat(MigrationVersion.fromVersion("1.1"))
            .isGreaterThan(MigrationVersion.fromVersion("1"))
            .isLessThan(MigrationVersion.fromVersion("2"));

        assertThat(repair)
            .contains("CREATE SCHEMA IF NOT EXISTS subscription_schema")
            .contains("CREATE TABLE IF NOT EXISTS subscription_schema.subscription_plan")
            .contains("CREATE TABLE IF NOT EXISTS subscription_schema.customer_subscription")
            .contains("CREATE TABLE IF NOT EXISTS subscription_schema.subscription_status_history");
    }

    private String resource(String path) throws IOException {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(path)) {
            assertThat(input)
                .as("required test resource %s", path)
                .isNotNull();
            return new String(input.readAllBytes(), UTF_8);
        }
    }
}
