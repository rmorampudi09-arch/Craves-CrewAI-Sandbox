package in.craves.catalog.service;

import in.craves.catalog.config.CatalogDiscoveryProperties;
import in.craves.catalog.exception.ApiException;
import in.craves.catalog.security.CravesPrincipal;
import in.craves.catalog.service.MediaStorageService.StoredMedia;
import in.craves.catalog.web.ApiDtos.AvailabilityRequest;
import in.craves.catalog.web.ApiDtos.DiscoveryRadiusResponse;
import in.craves.catalog.web.ApiDtos.FoodType;
import in.craves.catalog.web.ApiDtos.KitchenProfileRequest;
import in.craves.catalog.web.ApiDtos.KitchenProfileResponse;
import in.craves.catalog.web.ApiDtos.KitchenStatus;
import in.craves.catalog.web.ApiDtos.MenuItemImageResponse;
import in.craves.catalog.web.ApiDtos.MenuItemRequest;
import in.craves.catalog.web.ApiDtos.MenuItemResponse;
import in.craves.catalog.web.ApiDtos.MenuItemStatus;
import in.craves.catalog.web.ApiDtos.PublicKitchenDiscoveryResponse;
import in.craves.catalog.web.ApiDtos.PublicKitchenSummaryResponse;
import in.craves.catalog.web.ApiDtos.SpiceLevel;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class CatalogService {
    private final JdbcTemplate jdbcTemplate;
    private final MediaStorageService mediaStorageService;
    private final CatalogDiscoveryProperties discoveryProperties;

    public CatalogService(
        JdbcTemplate jdbcTemplate,
        MediaStorageService mediaStorageService,
        CatalogDiscoveryProperties discoveryProperties
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.mediaStorageService = mediaStorageService;
        this.discoveryProperties = discoveryProperties;
    }

    public KitchenProfileResponse getMyKitchen(CravesPrincipal principal) {
        requireChef(principal);
        return findKitchenByIdentity(principal.identityId())
            .orElseThrow(() -> ApiException.notFound("KITCHEN_PROFILE_NOT_FOUND", "Kitchen profile was not found"));
    }

    @Transactional
    public KitchenProfileResponse upsertMyKitchen(CravesPrincipal principal, KitchenProfileRequest request) {
        requireChef(principal);
        KitchenStatus status = request.status() == null ? KitchenStatus.DRAFT : request.status();
        Optional<UUID> existingId = findKitchenIdByIdentity(principal.identityId());
        if (existingId.isEmpty()) {
            UUID id = UUID.randomUUID();
            jdbcTemplate.update(
                "INSERT INTO catalog_schema.kitchen_profile (id, identity_id, kitchen_name, display_name, description, phone_number, email, address_line1, address_line2, landmark, area_name, city, state, postal_code, latitude, longitude, status, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())",
                id,
                principal.identityId(),
                request.kitchenName(),
                blankToNull(request.displayName()),
                blankToNull(request.description()),
                blankToNull(request.phoneNumber()),
                blankToNull(request.email()),
                request.addressLine1(),
                blankToNull(request.addressLine2()),
                blankToNull(request.landmark()),
                blankToNull(request.areaName()),
                request.city(),
                request.state(),
                blankToNull(request.postalCode()),
                request.latitude(),
                request.longitude(),
                status.name()
            );
        } else {
            jdbcTemplate.update(
                "UPDATE catalog_schema.kitchen_profile SET kitchen_name = ?, display_name = ?, description = ?, phone_number = ?, email = ?, address_line1 = ?, address_line2 = ?, landmark = ?, area_name = ?, city = ?, state = ?, postal_code = ?, latitude = ?, longitude = ?, status = ?, updated_at = now() WHERE identity_id = ?",
                request.kitchenName(),
                blankToNull(request.displayName()),
                blankToNull(request.description()),
                blankToNull(request.phoneNumber()),
                blankToNull(request.email()),
                request.addressLine1(),
                blankToNull(request.addressLine2()),
                blankToNull(request.landmark()),
                blankToNull(request.areaName()),
                request.city(),
                request.state(),
                blankToNull(request.postalCode()),
                request.latitude(),
                request.longitude(),
                status.name(),
                principal.identityId()
            );
        }
        return getMyKitchen(principal);
    }

    public List<MenuItemResponse> listMyMenuItems(CravesPrincipal principal) {
        requireChef(principal);
        UUID kitchenId = requireMyKitchenId(principal.identityId());
        return findMenuItems("WHERE kitchen_id = ? ORDER BY created_at DESC", kitchenId);
    }

    @Transactional
    public MenuItemResponse createMenuItem(CravesPrincipal principal, MenuItemRequest request) {
        requireChef(principal);
        validateDeliveryMetadata(request);
        UUID kitchenId = requireMyKitchenId(principal.identityId());
        UUID menuItemId = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO catalog_schema.menu_item (id, kitchen_id, item_name, description, category, food_type, price, currency, serves_count, preparation_time_minutes, spice_level, unit_package_weight_grams, thermobox_required, is_available, status, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, now(), now())",
            menuItemId,
            kitchenId,
            request.itemName(),
            blankToNull(request.description()),
            request.category(),
            request.foodType().name(),
            request.price(),
            defaultCurrency(request.currency()),
            request.servesCount(),
            request.preparationTimeMinutes(),
            request.spiceLevel() == null ? null : request.spiceLevel().name(),
            request.unitPackageWeightGrams(),
            request.thermoboxRequired(),
            Boolean.TRUE.equals(request.available()),
            statusOrDefault(request.status()).name()
        );
        return getMyMenuItem(principal, menuItemId);
    }

    @Transactional
    public MenuItemResponse updateMenuItem(CravesPrincipal principal, UUID menuItemId, MenuItemRequest request) {
        requireChef(principal);
        validateDeliveryMetadata(request);
        UUID kitchenId = requireMyKitchenId(principal.identityId());
        int updated = jdbcTemplate.update(
            "UPDATE catalog_schema.menu_item SET item_name = ?, description = ?, category = ?, food_type = ?, price = ?, currency = ?, serves_count = ?, preparation_time_minutes = ?, spice_level = ?, unit_package_weight_grams = ?, thermobox_required = ?, is_available = ?, status = ?, updated_at = now() WHERE id = ? AND kitchen_id = ?",
            request.itemName(),
            blankToNull(request.description()),
            request.category(),
            request.foodType().name(),
            request.price(),
            defaultCurrency(request.currency()),
            request.servesCount(),
            request.preparationTimeMinutes(),
            request.spiceLevel() == null ? null : request.spiceLevel().name(),
            request.unitPackageWeightGrams(),
            request.thermoboxRequired(),
            Boolean.TRUE.equals(request.available()),
            statusOrDefault(request.status()).name(),
            menuItemId,
            kitchenId
        );
        if (updated == 0) {
            throw ApiException.notFound("MENU_ITEM_NOT_FOUND", "Menu item was not found");
        }
        return getMyMenuItem(principal, menuItemId);
    }

    @Transactional
    public MenuItemResponse updateAvailability(CravesPrincipal principal, UUID menuItemId, AvailabilityRequest request) {
        requireChef(principal);
        UUID kitchenId = requireMyKitchenId(principal.identityId());
        MenuItemResponse existing = getMyMenuItem(principal, menuItemId);
        if (request.available() && (existing.unitPackageWeightGrams() == null || existing.thermoboxRequired() == null)) {
            throw ApiException.badRequest(
                "DELIVERY_METADATA_REQUIRED",
                "Package weight and thermobox requirement must be set before making a menu item available"
            );
        }
        int updated = jdbcTemplate.update(
            "UPDATE catalog_schema.menu_item SET is_available = ?, updated_at = now() WHERE id = ? AND kitchen_id = ?",
            request.available(),
            menuItemId,
            kitchenId
        );
        if (updated == 0) {
            throw ApiException.notFound("MENU_ITEM_NOT_FOUND", "Menu item was not found");
        }
        jdbcTemplate.update(
            "INSERT INTO catalog_schema.menu_item_availability_audit (id, menu_item_id, chef_identity_id, old_available, new_available, reason, created_at) VALUES (?, ?, ?, ?, ?, ?, now())",
            UUID.randomUUID(),
            menuItemId,
            principal.identityId(),
            existing.available(),
            request.available(),
            blankToNull(request.reason())
        );
        return getMyMenuItem(principal, menuItemId);
    }

    @Transactional
    public MenuItemImageResponse uploadMenuItemImage(CravesPrincipal principal, UUID menuItemId, MultipartFile file, boolean primary) {
        requireChef(principal);
        UUID kitchenId = requireMyKitchenId(principal.identityId());
        getMyMenuItem(principal, menuItemId);
        StoredMedia stored = mediaStorageService.uploadMenuImage(kitchenId, menuItemId, file);
        boolean shouldBePrimary = primary || listImages(menuItemId).isEmpty();
        if (shouldBePrimary) {
            jdbcTemplate.update("UPDATE catalog_schema.menu_item_image SET is_primary = false WHERE menu_item_id = ?", menuItemId);
        }
        UUID imageId = UUID.randomUUID();
        Integer maxSort = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(sort_order), -1) FROM catalog_schema.menu_item_image WHERE menu_item_id = ?",
            Integer.class,
            menuItemId
        );
        int sortOrder = maxSort == null ? 0 : maxSort + 1;
        jdbcTemplate.update(
            "INSERT INTO catalog_schema.menu_item_image (id, menu_item_id, blob_container, blob_name, content_type, file_size_bytes, public_url, sort_order, is_primary, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, now())",
            imageId,
            menuItemId,
            stored.blobContainer(),
            stored.blobName(),
            stored.contentType(),
            stored.fileSizeBytes(),
            stored.publicUrl(),
            sortOrder,
            shouldBePrimary
        );
        return getImage(imageId);
    }

    public PublicKitchenDiscoveryResponse discoverKitchens(BigDecimal latitude, BigDecimal longitude, String city, String areaName, BigDecimal requestedRadiusKm) {
        RadiusPolicy policy = resolveRadius(city, areaName, requestedRadiusKm);
        List<PublicKitchenSummaryResponse> kitchens;
        if (latitude != null && longitude != null) {
            kitchens = discoverNearby(latitude, longitude, city, policy.radiusKm());
        } else {
            kitchens = discoverByCity(city);
        }
        return new PublicKitchenDiscoveryResponse(
            new DiscoveryRadiusResponse(policy.city(), policy.areaName(), policy.radiusKm(), policy.maxRadiusKm()),
            kitchens
        );
    }

    public KitchenProfileResponse getPublicKitchen(UUID kitchenId) {
        List<KitchenProfileResponse> rows = jdbcTemplate.query(
            "SELECT * FROM catalog_schema.kitchen_profile WHERE id = ? AND status = 'ACTIVE'",
            this::mapKitchen,
            kitchenId
        );
        if (rows.isEmpty()) {
            throw ApiException.notFound("KITCHEN_NOT_FOUND", "Kitchen was not found");
        }
        return rows.getFirst();
    }

    public List<MenuItemResponse> getPublicMenuItems(UUID kitchenId) {
        getPublicKitchen(kitchenId);
        return findMenuItems("WHERE kitchen_id = ? AND status = 'ACTIVE' AND is_available = true ORDER BY category, item_name", kitchenId);
    }

    public MenuItemResponse getPublicMenuItem(UUID menuItemId) {
        List<MenuItemResponse> rows = findMenuItems(
            "WHERE id = ? AND status = 'ACTIVE' AND is_available = true AND kitchen_id IN (SELECT id FROM catalog_schema.kitchen_profile WHERE status = 'ACTIVE')",
            menuItemId
        );
        if (rows.isEmpty()) {
            throw ApiException.notFound("MENU_ITEM_NOT_FOUND", "Menu item was not found");
        }
        return rows.getFirst();
    }

    private List<PublicKitchenSummaryResponse> discoverNearby(BigDecimal latitude, BigDecimal longitude, String city, BigDecimal radiusKm) {
        List<Object> args = new ArrayList<>();
        args.add(latitude);
        args.add(longitude);
        args.add(latitude);
        String cityClause = "";
        if (StringUtils.hasText(city)) {
            cityClause = " AND lower(city) = lower(?)";
            args.add(city.trim());
        }
        args.add(radiusKm);
        String sql = "SELECT k.*, " +
            "(SELECT COUNT(*) FROM catalog_schema.menu_item mi WHERE mi.kitchen_id = k.id AND mi.status = 'ACTIVE' AND mi.is_available = true) AS active_menu_item_count " +
            "FROM (SELECT kp.*, (6371.0 * acos(LEAST(1.0, GREATEST(-1.0, cos(radians(?)) * cos(radians(latitude)) * cos(radians(longitude) - radians(?)) + sin(radians(?)) * sin(radians(latitude)))))) AS distance_km " +
            "FROM catalog_schema.kitchen_profile kp WHERE status = 'ACTIVE' AND latitude IS NOT NULL AND longitude IS NOT NULL" + cityClause + ") k " +
            "WHERE k.distance_km <= ? AND (SELECT COUNT(*) FROM catalog_schema.menu_item mi WHERE mi.kitchen_id = k.id AND mi.status = 'ACTIVE' AND mi.is_available = true) > 0 " +
            "ORDER BY k.distance_km ASC LIMIT 50";
        return jdbcTemplate.query(sql, this::mapPublicKitchen, args.toArray());
    }

    private List<PublicKitchenSummaryResponse> discoverByCity(String city) {
        List<Object> args = new ArrayList<>();
        String cityClause = "";
        if (StringUtils.hasText(city)) {
            cityClause = " AND lower(k.city) = lower(?)";
            args.add(city.trim());
        }
        String sql = "SELECT k.*, NULL::numeric AS distance_km, " +
            "(SELECT COUNT(*) FROM catalog_schema.menu_item mi WHERE mi.kitchen_id = k.id AND mi.status = 'ACTIVE' AND mi.is_available = true) AS active_menu_item_count " +
            "FROM catalog_schema.kitchen_profile k WHERE k.status = 'ACTIVE'" + cityClause + " " +
            "AND (SELECT COUNT(*) FROM catalog_schema.menu_item mi WHERE mi.kitchen_id = k.id AND mi.status = 'ACTIVE' AND mi.is_available = true) > 0 " +
            "ORDER BY k.updated_at DESC LIMIT 50";
        return jdbcTemplate.query(sql, this::mapPublicKitchen, args.toArray());
    }

    private RadiusPolicy resolveRadius(String city, String areaName, BigDecimal requestedRadiusKm) {
        String effectiveCity = StringUtils.hasText(city) ? city.trim() : "Hyderabad";
        String effectiveArea = StringUtils.hasText(areaName) ? areaName.trim() : "DEFAULT";
        Optional<RadiusPolicy> areaPolicy = findPolicy(effectiveCity, effectiveArea);
        Optional<RadiusPolicy> defaultPolicy = areaPolicy.isPresent() ? areaPolicy : findPolicy(effectiveCity, "DEFAULT");
        RadiusPolicy policy = defaultPolicy.orElse(new RadiusPolicy(effectiveCity, effectiveArea, discoveryProperties.getDefaultRadiusKm(), discoveryProperties.getMaxRadiusKm()));
        BigDecimal radius = requestedRadiusKm == null ? policy.radiusKm() : requestedRadiusKm.min(policy.maxRadiusKm());
        return new RadiusPolicy(policy.city(), policy.areaName(), radius, policy.maxRadiusKm());
    }

    private Optional<RadiusPolicy> findPolicy(String city, String areaName) {
        List<RadiusPolicy> rows = jdbcTemplate.query(
            "SELECT city, area_name, default_radius_km, max_radius_km FROM catalog_schema.service_area_policy WHERE lower(city) = lower(?) AND lower(area_name) = lower(?) AND is_active = true",
            (rs, rowNum) -> new RadiusPolicy(rs.getString("city"), rs.getString("area_name"), rs.getBigDecimal("default_radius_km"), rs.getBigDecimal("max_radius_km")),
            city,
            areaName
        );
        return rows.stream().findFirst();
    }

    private Optional<KitchenProfileResponse> findKitchenByIdentity(UUID identityId) {
        List<KitchenProfileResponse> rows = jdbcTemplate.query(
            "SELECT * FROM catalog_schema.kitchen_profile WHERE identity_id = ?",
            this::mapKitchen,
            identityId
        );
        return rows.stream().findFirst();
    }

    private Optional<UUID> findKitchenIdByIdentity(UUID identityId) {
        List<UUID> rows = jdbcTemplate.query(
            "SELECT id FROM catalog_schema.kitchen_profile WHERE identity_id = ?",
            (rs, rowNum) -> rs.getObject("id", UUID.class),
            identityId
        );
        return rows.stream().findFirst();
    }

    private UUID requireMyKitchenId(UUID identityId) {
        return findKitchenIdByIdentity(identityId)
            .orElseThrow(() -> ApiException.badRequest("KITCHEN_PROFILE_REQUIRED", "Create kitchen profile before managing menu items"));
    }

    private MenuItemResponse getMyMenuItem(CravesPrincipal principal, UUID menuItemId) {
        UUID kitchenId = requireMyKitchenId(principal.identityId());
        List<MenuItemResponse> rows = findMenuItems("WHERE id = ? AND kitchen_id = ?", menuItemId, kitchenId);
        if (rows.isEmpty()) {
            throw ApiException.notFound("MENU_ITEM_NOT_FOUND", "Menu item was not found");
        }
        return rows.getFirst();
    }

    private List<MenuItemResponse> findMenuItems(String whereClause, Object... args) {
        String sql = "SELECT * FROM catalog_schema.menu_item " + whereClause;
        return jdbcTemplate.query(sql, this::mapMenuItem, args);
    }

    private List<MenuItemImageResponse> listImages(UUID menuItemId) {
        return jdbcTemplate.query(
            "SELECT * FROM catalog_schema.menu_item_image WHERE menu_item_id = ? ORDER BY is_primary DESC, sort_order ASC, created_at ASC",
            this::mapImage,
            menuItemId
        );
    }

    private MenuItemImageResponse getImage(UUID imageId) {
        return jdbcTemplate.query(
            "SELECT * FROM catalog_schema.menu_item_image WHERE id = ?",
            this::mapImage,
            imageId
        ).getFirst();
    }

    private KitchenProfileResponse mapKitchen(ResultSet rs, int rowNum) throws SQLException {
        return new KitchenProfileResponse(
            rs.getObject("id", UUID.class),
            rs.getObject("identity_id", UUID.class),
            rs.getString("kitchen_name"),
            rs.getString("display_name"),
            rs.getString("description"),
            rs.getString("phone_number"),
            rs.getString("email"),
            rs.getString("address_line1"),
            rs.getString("address_line2"),
            rs.getString("landmark"),
            rs.getString("area_name"),
            rs.getString("city"),
            rs.getString("state"),
            rs.getString("postal_code"),
            rs.getBigDecimal("latitude"),
            rs.getBigDecimal("longitude"),
            KitchenStatus.valueOf(rs.getString("status")),
            instant(rs, "created_at"),
            instant(rs, "updated_at")
        );
    }

    private PublicKitchenSummaryResponse mapPublicKitchen(ResultSet rs, int rowNum) throws SQLException {
        return new PublicKitchenSummaryResponse(
            rs.getObject("id", UUID.class),
            rs.getString("kitchen_name"),
            rs.getString("display_name"),
            rs.getString("description"),
            rs.getString("area_name"),
            rs.getString("city"),
            rs.getBigDecimal("latitude"),
            rs.getBigDecimal("longitude"),
            rs.getBigDecimal("distance_km"),
            rs.getLong("active_menu_item_count")
        );
    }

    private MenuItemResponse mapMenuItem(ResultSet rs, int rowNum) throws SQLException {
        UUID menuItemId = rs.getObject("id", UUID.class);
        String spiceLevel = rs.getString("spice_level");
        return new MenuItemResponse(
            menuItemId,
            rs.getObject("kitchen_id", UUID.class),
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
            rs.getBoolean("is_available"),
            MenuItemStatus.valueOf(rs.getString("status")),
            listImages(menuItemId),
            instant(rs, "created_at"),
            instant(rs, "updated_at")
        );
    }

    private MenuItemImageResponse mapImage(ResultSet rs, int rowNum) throws SQLException {
        return new MenuItemImageResponse(
            rs.getObject("id", UUID.class),
            rs.getObject("menu_item_id", UUID.class),
            rs.getString("blob_container"),
            rs.getString("blob_name"),
            rs.getString("content_type"),
            rs.getLong("file_size_bytes"),
            rs.getString("public_url"),
            rs.getInt("sort_order"),
            rs.getBoolean("is_primary"),
            instant(rs, "created_at")
        );
    }

    private void requireChef(CravesPrincipal principal) {
        if (principal == null || !principal.hasRole("CHEF")) {
            throw ApiException.forbidden("CHEF_ROLE_REQUIRED", "Chef role is required");
        }
    }

    private static void validateDeliveryMetadata(MenuItemRequest request) {
        if (request.unitPackageWeightGrams() == null || request.unitPackageWeightGrams() <= 0) {
            throw ApiException.badRequest(
                "PACKAGE_WEIGHT_REQUIRED",
                "Unit packaged weight must be greater than zero grams"
            );
        }
        if (request.thermoboxRequired() == null) {
            throw ApiException.badRequest(
                "THERMOBOX_REQUIREMENT_REQUIRED",
                "Thermobox requirement must be explicitly supplied"
            );
        }
    }

    private static String defaultCurrency(String currency) {
        return StringUtils.hasText(currency) ? currency.trim().toUpperCase() : "INR";
    }

    private static MenuItemStatus statusOrDefault(MenuItemStatus status) {
        return status == null ? MenuItemStatus.DRAFT : status;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static Integer integerOrNull(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    private static Boolean booleanOrNull(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record RadiusPolicy(String city, String areaName, BigDecimal radiusKm, BigDecimal maxRadiusKm) {
    }
}
