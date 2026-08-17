package in.craves.integration.delivery.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class DeliveryCommandRepositoryTest {

    @Test
    void convertsInstantToUtcOffsetDateTimeForPostgresBinding() {
        Instant value = Instant.parse("2026-07-23T21:29:27Z");

        OffsetDateTime result = DeliveryCommandRepository.toDatabaseTimestamp(value);

        assertThat(result).isEqualTo(OffsetDateTime.parse("2026-07-23T21:29:27Z"));
        assertThat(result.getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(result.toInstant()).isEqualTo(value);
    }
}
