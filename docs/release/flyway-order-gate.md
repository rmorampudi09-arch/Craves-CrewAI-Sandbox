# Flyway migration ordering gate

Validates every `services/*/src/main/resources/db/migration/V*__*.sql` file.

It enforces deterministic lowercase migration names, unique strictly increasing versions per service, and an explicit `CRAVES-REVIEWED-DESTRUCTIVE-MIGRATION` marker for destructive SQL.

This module reads source files only. It never connects to PostgreSQL and never applies, repairs, cleans, or rolls back a migration.
