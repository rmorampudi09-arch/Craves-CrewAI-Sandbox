package in.craves.catalog.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import in.craves.catalog.config.CatalogDiscoveryProperties;
import in.craves.catalog.exception.ApiException;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class NearbyDiscoveryServiceValidationTest {
    private NearbyDiscoveryService service;

    @BeforeEach
    void setUp() {
        CatalogDiscoveryProperties properties = new CatalogDiscoveryProperties();
        properties.setMaxQueryRadiusMeters(50_000);
        properties.setMaxPageSize(100);
        service = new NearbyDiscoveryService(new JdbcTemplate(), properties);
    }

    @Test
    void rejectsMissingLatitude() {
        ApiException exception = catchThrowableOfType(
            () -> service.discoverKitchens(null, new BigDecimal("78.3915"), 5000, 0, 20),
            ApiException.class
        );

        assertThat(exception.getCode()).isEqualTo("LATITUDE_REQUIRED");
    }

    @Test
    void rejectsOutOfRangeLongitude() {
        ApiException exception = catchThrowableOfType(
            () -> service.discoverKitchens(
                new BigDecimal("17.4483"),
                new BigDecimal("181"),
                5000,
                0,
                20
            ),
            ApiException.class
        );

        assertThat(exception.getCode()).isEqualTo("INVALID_LONGITUDE");
    }

    @Test
    void rejectsZeroRadius() {
        ApiException exception = catchThrowableOfType(
            () -> service.discoverMenuItems(
                new BigDecimal("17.4483"),
                new BigDecimal("78.3915"),
                0,
                0,
                20
            ),
            ApiException.class
        );

        assertThat(exception.getCode()).isEqualTo("INVALID_RADIUS");
    }

    @Test
    void rejectsRadiusAboveTechnicalLimit() {
        ApiException exception = catchThrowableOfType(
            () -> service.discoverKitchens(
                new BigDecimal("17.4483"),
                new BigDecimal("78.3915"),
                50_001,
                0,
                20
            ),
            ApiException.class
        );

        assertThat(exception.getCode()).isEqualTo("RADIUS_TOO_LARGE");
    }

    @Test
    void rejectsNegativePage() {
        ApiException exception = catchThrowableOfType(
            () -> service.discoverKitchens(
                new BigDecimal("17.4483"),
                new BigDecimal("78.3915"),
                5000,
                -1,
                20
            ),
            ApiException.class
        );

        assertThat(exception.getCode()).isEqualTo("INVALID_PAGE");
    }

    @Test
    void rejectsPageSizeAboveTechnicalLimit() {
        ApiException exception = catchThrowableOfType(
            () -> service.discoverMenuItems(
                new BigDecimal("17.4483"),
                new BigDecimal("78.3915"),
                5000,
                0,
                101
            ),
            ApiException.class
        );

        assertThat(exception.getCode()).isEqualTo("INVALID_PAGE_SIZE");
    }
}
