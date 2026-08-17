package in.craves.catalog.service;

import in.craves.catalog.config.CatalogDiscoveryProperties;
import in.craves.catalog.exception.ApiException;
import in.craves.catalog.web.ApiDtos.FoodType;
import in.craves.catalog.web.ApiDtos.SpiceLevel;
import in.craves.catalog.web.DiscoveryDtos.NearbyKitchenDiscoveryResponse;
import in.craves.catalog.web.DiscoveryDtos.NearbyKitchenSummaryResponse;
import in.craves.catalog.web.DiscoveryDtos.NearbyMenuItemDiscoveryResponse;
import in.craves.catalog.web.DiscoveryDtos.NearbyMenuItemSummaryResponse;
import in.craves.catalog.web.DiscoveryDtos.PageMetadata;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class NearbyDiscoveryService {
    private static final BigDecimal MIN_LATITUDE = new BigDecimal("-90");
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("90");
    private static final BigDecimal MIN_LONGITUDE = new BigDecimal("-180");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");

    private static final String REQUEST_LOCATION_CTE = """
        WITH request_location AS (
            SELECT public.ST_SetSRID(
                public.ST_MakePoint(
                    CAST(? AS double precision),
                    CAST(? AS double precision)
                ),
                4326
            )::public.geography AS location
        )
        """;

    private static final String KITCHEN_FILTER = """
        FROM catalog_schema.kitchen_profile kp
        CROSS JOIN request_location rl
        WHERE kp.status = 'ACTIVE'
          AND kp.location IS NOT NULL
          AND public.ST_DWithin(kp.location, rl.location, ?)
          AND EXISTS (
              SELECT 1
              FROM catalog_schema.menu_item mi
              WHERE mi.kitchen_id = kp.id
                AND mi.status = 'ACTIVE'
                AND mi.is_available = true
                AND mi.unit_package_weight_grams IS NOT NULL
                AND mi.thermobox_required IS NOT NULL
          )
        """;

    private static final String MENU_ITEM_FILTER = """
        FROM catalog_schema.menu_item mi
        JOIN catalog_schema.kitchen_profile kp ON kp.id = mi.kitchen_id
        CROSS JOIN request_location rl
        WHERE kp.status = 'ACTIVE'
          AND kp.location IS NOT NULL
          AND mi.status = 'ACTIVE'
          AND mi.is_available = true
          AND mi.unit_package_weight_grams IS NOT NULL
          AND mi.thermobox_required IS NOT NULL
          AND public.ST_DWithin(kp.location, rl.location, ?)
        """;

    private final JdbcTemplate jdbcTemplate;
    private final CatalogDiscoveryProperties discoveryProperties;

    public NearbyDiscoveryService(
        JdbcTemplate jdbcTemplate,
        CatalogDiscoveryProperties discoveryProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.discoveryProperties = discoveryProperties;
    }

    public NearbyKitchenDiscoveryResponse discoverKitchens(
        BigDecimal latitude,
        BigDecimal longitude,
        int radiusMeters,
        int page,
        int size
    ) {
        validateQuery(latitude, longitude, radiusMeters, page, size);
        long totalElements = countNearbyKitchens(latitude, longitude, radiusMeters);
        List<NearbyKitchenSummaryResponse> kitchens = totalElements == 0
            ? List.of()
            : queryNearbyKitchens(latitude, longitude, radiusMeters, page, size);
        return new NearbyKitchenDiscoveryResponse(
            latitude,
            longitude,
            radiusMeters,
            pageMetadata(page, size, totalElements),
            kitchens
        );
    }

    public NearbyMenuItemDiscoveryResponse discoverMenuItems(
        BigDecimal latitude,
        BigDecimal longitude,
        int radiusMeters,
        int page,
        int size
    ) {
        validateQuery(latitude, longitude, radiusMeters, page, size);
        long totalElements = countNearbyMenuItems(latitude, longitude, radiusMeters);
        List<NearbyMenuItemSummaryResponse> menuItems = totalElements == 0
            ? List.of()
            : queryNearbyMenuItems(latitude, longitude, radiusMeters, page, size);
        return new NearbyMenuItemDiscoveryResponse(
            latitude,
            longitude,
            radiusMeters,
            pageMetadata(page, size, totalElements),
            menuItems
        );
    }

    private long countNearbyKitchens(BigDecimal latitude, BigDecimal longitude, int radiusMeters) {
        Long count = jdbcTemplate.queryForObject(
            REQUEST_LOCATION_CTE + "SELECT COUNT(*) " + KITCHEN_FILTER,
            Long.class,
            longitude,
            latitude,
            radiusMeters
        );
        return count == null ? 0 : count;
    }

    private List<NearbyKitchenSummaryResponse> queryNearbyKitchens(
        BigDecimal latitude,
        BigDecimal longitude,
        int radiusMeters,
        int page,
        int size
    ) {
        String sql = REQUEST_LOCATION_CTE + """
            SELECT
                kp.id,
                kp.kitchen_name,
                kp.display_name,
                kp.description,
                kp.area_name,
                kp.city,
                kp.state,
                kp.latitude,
                kp.longitude,
                ROUND(public.ST_Distance(kp.location, rl.location))::bigint AS distance_meters,
                (
                    SELECT COUNT(*)
                    FROM catalog_schema.menu_item mi
                    WHERE mi.kitchen_id = kp.id
                      AND mi.status = 'ACTIVE'
                      AND mi.is_available = true
                      AND mi.unit_package_weight_grams IS NOT NULL
                      AND mi.thermobox_required IS NOT NULL
                ) AS active_menu_item_count
            """ + KITCHEN_FILTER + """
            ORDER BY distance_meters ASC, kp.id ASC
            LIMIT ? OFFSET ?
            """;
        return jdbcTemplate.query(
            sql,
            this::mapKitchen,
            longitude,
            latitude,
            radiusMeters,
            size,
            offset(page, size)
        );
    }

    private long countNearbyMenuItems(BigDecimal latitude, BigDecimal longitude, int radiusMeters) {
        Long count = jdbcTemplate.queryForObject(
            REQUEST_LOCATION_CTE + "SELECT COUNT(*) " + MENU_ITEM_FILTER,
            Long.class,
            longitude,
            latitude,
            radiusMeters
        );
        return count == null ? 0 : count;
    }

    private List<NearbyMenuItemSummaryResponse> queryNearbyMenuItems(
        BigDecimal latitude,
        BigDecimal longitude,
        int radiusMeters,
        int page,
        int size
    ) {
        String sql = REQUEST_LOCATION_CTE + """
            SELECT
                mi.id,
                mi.kitchen_id,
                kp.kitchen_name,
                kp.display_name AS kitchen_display_name,
                kp.area_name,
                kp.city,
                kp.state,
                kp.latitude AS kitchen_latitude,
                kp.longitude AS kitchen_longitude,
                ROUND(public.ST_Distance(kp.location, rl.location))::bigint AS distance_meters,
                mi.item_name,
                mi.description,
                mi.category,
                mi.food_type,
                mi.price,
                mi.currency,
                mi.serves_count,
                mi.preparation_time_minutes,
                mi.spice_level,
                mi.unit_package_weight_grams,
                mi.thermobox_required,
                (
                    SELECT mii.public_url
                    FROM catalog_schema.menu_item_image mii
                    WHERE mii.menu_item_id = mi.id
                    ORDER BY mii.is_primary DESC, mii.sort_order ASC, mii.created_at ASC
                    LIMIT 1
                ) AS primary_image_url
            """ + MENU_ITEM_FILTER + """
            ORDER BY distance_meters ASC, mi.category ASC, mi.item_name ASC, mi.id ASC
            LIMIT ? OFFSET ?
            """;
        return jdbcTemplate.query(
            sql,
            this::mapMenuItem,
            longitude,
            latitude,
            radiusMeters,
            size,
            offset(page, size)
        );
    }

    private NearbyKitchenSummaryResponse mapKitchen(ResultSet rs, int rowNum) throws SQLException {
        return new NearbyKitchenSummaryResponse(
            rs.getObject("id", UUID.class),
            rs.getString("kitchen_name"),
            rs.getString("display_name"),
            rs.getString("description"),
            rs.getString("area_name"),
            rs.getString("city"),
            rs.getString("state"),
            rs.getBigDecimal("latitude"),
            rs.getBigDecimal("longitude"),
            rs.getLong("distance_meters"),
            rs.getLong("active_menu_item_count")
        );
    }

    private NearbyMenuItemSummaryResponse mapMenuItem(ResultSet rs, int rowNum) throws SQLException {
        String spiceLevel = rs.getString("spice_level");
        return new NearbyMenuItemSummaryResponse(
            rs.getObject("id", UUID.class),
            rs.getObject("kitchen_id", UUID.class),
            rs.getString("kitchen_name"),
            rs.getString("kitchen_display_name"),
            rs.getString("area_name"),
            rs.getString("city"),
            rs.getString("state"),
            rs.getBigDecimal("kitchen_latitude"),
            rs.getBigDecimal("kitchen_longitude"),
            rs.getLong("distance_meters"),
            rs.getString("item_name"),
            rs.getString("description"),
            rs.getString("category"),
            FoodType.valueOf(rs.getString("food_type")),
            rs.getBigDecimal("price"),
            rs.getString("currency"),
            integerOrNull(rs, "serves_count"),
            integerOrNull(rs, "preparation_time_minutes"),
            spiceLevel == null ? null : SpiceLevel.valueOf(spiceLevel),
            integerOrNull(rs, "unit_package_weight_grams"),
            booleanOrNull(rs, "thermobox_required"),
            rs.getString("primary_image_url")
        );
    }

    private void validateQuery(
        BigDecimal latitude,
        BigDecimal longitude,
        int radiusMeters,
        int page,
        int size
    ) {
        if (latitude == null) {
            throw ApiException.badRequest("LATITUDE_REQUIRED", "Latitude is required for nearby discovery");
        }
        if (longitude == null) {
            throw ApiException.badRequest("LONGITUDE_REQUIRED", "Longitude is required for nearby discovery");
        }
        if (latitude.compareTo(MIN_LATITUDE) < 0 || latitude.compareTo(MAX_LATITUDE) > 0) {
            throw ApiException.badRequest("INVALID_LATITUDE", "Latitude must be between -90 and 90");
        }
        if (longitude.compareTo(MIN_LONGITUDE) < 0 || longitude.compareTo(MAX_LONGITUDE) > 0) {
            throw ApiException.badRequest("INVALID_LONGITUDE", "Longitude must be between -180 and 180");
        }
        if (radiusMeters <= 0) {
            throw ApiException.badRequest("INVALID_RADIUS", "radiusMeters must be greater than zero");
        }
        if (radiusMeters > discoveryProperties.getMaxQueryRadiusMeters()) {
            throw ApiException.badRequest(
                "RADIUS_TOO_LARGE",
                "radiusMeters exceeds the configured discovery query limit"
            );
        }
        if (page < 0) {
            throw ApiException.badRequest("INVALID_PAGE", "page must be zero or greater");
        }
        if (size <= 0 || size > discoveryProperties.getMaxPageSize()) {
            throw ApiException.badRequest(
                "INVALID_PAGE_SIZE",
                "size must be between 1 and the configured maximum page size"
            );
        }
    }

    private static PageMetadata pageMetadata(int page, int size, long totalElements) {
        long totalPages = totalElements == 0 ? 0 : ((totalElements - 1) / size) + 1;
        boolean hasNext = ((long) page + 1L) * size < totalElements;
        return new PageMetadata(page, size, totalElements, totalPages, hasNext);
    }

    private static long offset(int page, int size) {
        return Math.multiplyExact((long) page, size);
    }

    private static Integer integerOrNull(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Boolean booleanOrNull(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }
}
