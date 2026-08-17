package in.craves.subscription;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class FlywaySchemaQualificationSafetyTest {

    private static final Pattern UNQUALIFIED_DROP_INDEX = Pattern.compile(
            "(?im)^\\s*DROP\\s+INDEX\\s+(?:IF\\s+EXISTS\\s+)?(?!subscription_schema\\.)[A-Za-z_][A-Za-z0-9_]*\\s*;"
    );

    @Test
    void subscriptionMigrationsMustSchemaQualifyDropIndexStatements() throws IOException {
        Path migrationDirectory = Path.of("src", "main", "resources", "db", "migration");
        List<String> violations = new ArrayList<>();

        try (var paths = Files.list(migrationDirectory)) {
            for (Path path : paths.filter(p -> p.getFileName().toString().endsWith(".sql")).toList()) {
                String sql = Files.readString(path);
                if (UNQUALIFIED_DROP_INDEX.matcher(sql).find()) {
                    violations.add(path.getFileName().toString());
                }
            }
        }

        assertTrue(
                violations.isEmpty(),
                () -> "Subscription Flyway migrations contain unqualified DROP INDEX statements: " + violations
        );
    }
}
