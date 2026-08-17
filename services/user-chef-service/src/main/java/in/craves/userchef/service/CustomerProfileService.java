package in.craves.userchef.service;

import in.craves.userchef.exception.ApiException;
import in.craves.userchef.security.CurrentUser;
import in.craves.userchef.web.ApiDtos.ActiveLocationType;
import in.craves.userchef.web.ApiDtos.AddressLabel;
import in.craves.userchef.web.ApiDtos.CustomerAddressRequest;
import in.craves.userchef.web.ApiDtos.CustomerAddressResponse;
import in.craves.userchef.web.ApiDtos.CustomerLocationRecommendationResponse;
import in.craves.userchef.web.ApiDtos.CustomerProfileRequest;
import in.craves.userchef.web.ApiDtos.CustomerProfileResponse;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CustomerProfileService {
    private static final BigDecimal MIN_LATITUDE = new BigDecimal("-90");
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("90");
    private static final BigDecimal MIN_LONGITUDE = new BigDecimal("-180");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");

    private final JdbcTemplate jdbcTemplate;

    public CustomerProfileService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public CustomerProfileResponse getProfile(CurrentUser user) {
        List<CustomerProfileResponse> rows = jdbcTemplate.query(
            "SELECT * FROM customer_profile WHERE identity_id = ?",
            this::mapProfile,
            user.identityId()
        );
        if (rows.isEmpty()) {
            throw ApiException.notFound("CUSTOMER_PROFILE_NOT_FOUND", "Customer profile has not been created yet");
        }
        return rows.getFirst();
    }

    @Transactional
    public CustomerProfileResponse upsertProfile(CurrentUser user, CustomerProfileRequest request) {
        List<UUID> existing = jdbcTemplate.query(
            "SELECT id FROM customer_profile WHERE identity_id = ?",
            (rs, rowNum) -> rs.getObject("id", UUID.class),
            user.identityId()
        );

        if (existing.isEmpty()) {
            UUID id = UUID.randomUUID();
            jdbcTemplate.update(
                "INSERT INTO customer_profile (id, identity_id, registered_phone_number, first_name, last_name, email, created_at, updated_at) " +
                    "VALUES (?, ?, ?, ?, ?, ?, now(), now())",
                id,
                user.identityId(),
                user.phoneNumber(),
                request.firstName(),
                request.lastName(),
                blankToNull(request.email())
            );
        } else {
            jdbcTemplate.update(
                "UPDATE customer_profile SET registered_phone_number = ?, first_name = ?, last_name = ?, email = ?, updated_at = now() " +
                    "WHERE identity_id = ?",
                user.phoneNumber(),
                request.firstName(),
                request.lastName(),
                blankToNull(request.email()),
                user.identityId()
            );
        }
        return getProfile(user);
    }

    public List<CustomerAddressResponse> listAddresses(CurrentUser user) {
        return jdbcTemplate.query(
            "SELECT * FROM customer_address WHERE identity_id = ? AND is_active = true " +
                "ORDER BY is_default DESC, updated_at DESC, created_at DESC",
            this::mapAddress,
            user.identityId()
        );
    }

    public CustomerAddressResponse getAddress(CurrentUser user, UUID addressId) {
        return getActiveAddress(user.identityId(), addressId);
    }

    public CustomerLocationRecommendationResponse recommendLocation(
        CurrentUser user,
        BigDecimal latitude,
        BigDecimal longitude,
        int matchRadiusMeters
    ) {
        validateCoordinates(latitude, longitude);
        if (matchRadiusMeters <= 0) {
            throw ApiException.badRequest(
                "INVALID_MATCH_RADIUS",
                "Saved-address match radius must be greater than zero metres"
            );
        }

        List<NearestAddress> nearest = jdbcTemplate.query(
            "WITH current_location AS (" +
                "SELECT ST_SetSRID(ST_MakePoint(CAST(? AS double precision), CAST(? AS double precision)), 4326)::geography AS point" +
                ") " +
                "SELECT ca.*, ST_Distance(ca.location, current_location.point) AS distance_meters " +
                "FROM customer_address ca CROSS JOIN current_location " +
                "WHERE ca.identity_id = ? AND ca.is_active = true AND ca.location IS NOT NULL " +
                "ORDER BY ca.location <-> current_location.point " +
                "LIMIT 1",
            (rs, rowNum) -> new NearestAddress(
                mapAddress(rs, rowNum),
                Math.round(rs.getDouble("distance_meters"))
            ),
            longitude,
            latitude,
            user.identityId()
        );

        if (nearest.isEmpty() || nearest.getFirst().distanceMeters() > matchRadiusMeters) {
            return new CustomerLocationRecommendationResponse(
                ActiveLocationType.LIVE_GPS,
                latitude,
                longitude,
                null,
                nearest.isEmpty() ? null : nearest.getFirst().distanceMeters(),
                matchRadiusMeters
            );
        }

        NearestAddress selected = nearest.getFirst();
        return new CustomerLocationRecommendationResponse(
            ActiveLocationType.SAVED_ADDRESS,
            selected.address().latitude(),
            selected.address().longitude(),
            selected.address(),
            selected.distanceMeters(),
            matchRadiusMeters
        );
    }

    @Transactional
    public CustomerAddressResponse addAddress(CurrentUser user, CustomerAddressRequest request) {
        validateAddress(request);
        boolean hasActiveAddress = hasActiveAddresses(user.identityId());
        boolean makeDefault = Boolean.TRUE.equals(request.isDefault()) || !hasActiveAddress;
        if (makeDefault) {
            clearDefaultAddress(user.identityId());
        }

        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO customer_address (id, identity_id, address_label, recipient_name, contact_phone_number, " +
                "address_line1, address_line2, landmark, area_name, district_name, city, state, postal_code, latitude, longitude, " +
                "is_default, is_active, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, true, now(), now())",
            id,
            user.identityId(),
            labelOrDefault(request.addressLabel()).name(),
            request.recipientName().trim(),
            request.contactPhoneNumber().trim(),
            request.addressLine1().trim(),
            blankToNull(request.addressLine2()),
            blankToNull(request.landmark()),
            request.areaName().trim(),
            blankToNull(request.districtName()),
            request.city().trim(),
            request.state().trim(),
            request.postalCode().trim(),
            request.latitude(),
            request.longitude(),
            makeDefault
        );
        ensureDefaultAddress(user.identityId());
        return getActiveAddress(user.identityId(), id);
    }

    @Transactional
    public CustomerAddressResponse updateAddress(CurrentUser user, UUID addressId, CustomerAddressRequest request) {
        validateAddress(request);
        getActiveAddress(user.identityId(), addressId);
        if (Boolean.TRUE.equals(request.isDefault())) {
            clearDefaultAddress(user.identityId());
        }
        jdbcTemplate.update(
            "UPDATE customer_address SET address_label = ?, recipient_name = ?, contact_phone_number = ?, " +
                "address_line1 = ?, address_line2 = ?, landmark = ?, area_name = ?, district_name = ?, city = ?, state = ?, " +
                "postal_code = ?, latitude = ?, longitude = ?, is_default = ?, updated_at = now() " +
                "WHERE id = ? AND identity_id = ? AND is_active = true",
            labelOrDefault(request.addressLabel()).name(),
            request.recipientName().trim(),
            request.contactPhoneNumber().trim(),
            request.addressLine1().trim(),
            blankToNull(request.addressLine2()),
            blankToNull(request.landmark()),
            request.areaName().trim(),
            blankToNull(request.districtName()),
            request.city().trim(),
            request.state().trim(),
            request.postalCode().trim(),
            request.latitude(),
            request.longitude(),
            Boolean.TRUE.equals(request.isDefault()),
            addressId,
            user.identityId()
        );
        ensureDefaultAddress(user.identityId());
        return getActiveAddress(user.identityId(), addressId);
    }

    @Transactional
    public void deleteAddress(CurrentUser user, UUID addressId) {
        getActiveAddress(user.identityId(), addressId);
        jdbcTemplate.update(
            "UPDATE customer_address SET is_active = false, is_default = false, updated_at = now() " +
                "WHERE id = ? AND identity_id = ? AND is_active = true",
            addressId,
            user.identityId()
        );
        ensureDefaultAddress(user.identityId());
    }

    public CustomerAddressResponse getAddressForInternal(UUID identityId, UUID addressId) {
        if (identityId == null || addressId == null) {
            throw ApiException.badRequest(
                "CUSTOMER_ADDRESS_LOOKUP_INVALID",
                "Customer identity and address ID are required"
            );
        }
        return getActiveAddress(identityId, addressId);
    }

    private CustomerAddressResponse getActiveAddress(UUID identityId, UUID addressId) {
        List<CustomerAddressResponse> rows = jdbcTemplate.query(
            "SELECT * FROM customer_address WHERE id = ? AND identity_id = ? AND is_active = true",
            this::mapAddress,
            addressId,
            identityId
        );
        if (rows.isEmpty()) {
            throw ApiException.notFound("CUSTOMER_ADDRESS_NOT_FOUND", "Active customer address was not found");
        }
        return rows.getFirst();
    }

    private boolean hasActiveAddresses(UUID identityId) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM customer_address WHERE identity_id = ? AND is_active = true",
            Integer.class,
            identityId
        );
        return count != null && count > 0;
    }

    private void clearDefaultAddress(UUID identityId) {
        jdbcTemplate.update(
            "UPDATE customer_address SET is_default = false, updated_at = now() " +
                "WHERE identity_id = ? AND is_default = true",
            identityId
        );
    }

    private void ensureDefaultAddress(UUID identityId) {
        Integer defaultCount = jdbcTemplate.queryForObject(
            "SELECT count(*) FROM customer_address " +
                "WHERE identity_id = ? AND is_active = true AND is_default = true",
            Integer.class,
            identityId
        );
        if (defaultCount != null && defaultCount > 0) {
            return;
        }

        List<UUID> candidateIds = jdbcTemplate.query(
            "SELECT id FROM customer_address WHERE identity_id = ? AND is_active = true " +
                "ORDER BY updated_at DESC, created_at DESC LIMIT 1",
            (rs, rowNum) -> rs.getObject("id", UUID.class),
            identityId
        );
        if (!candidateIds.isEmpty()) {
            jdbcTemplate.update(
                "UPDATE customer_address SET is_default = true, updated_at = now() WHERE id = ?",
                candidateIds.getFirst()
            );
        }
    }

    private CustomerProfileResponse mapProfile(ResultSet rs, int rowNum) throws SQLException {
        return new CustomerProfileResponse(
            rs.getObject("id", UUID.class),
            rs.getObject("identity_id", UUID.class),
            rs.getString("registered_phone_number"),
            rs.getString("first_name"),
            rs.getString("last_name"),
            rs.getString("email"),
            instant(rs, "created_at"),
            instant(rs, "updated_at")
        );
    }

    private CustomerAddressResponse mapAddress(ResultSet rs, int rowNum) throws SQLException {
        return new CustomerAddressResponse(
            rs.getObject("id", UUID.class),
            rs.getObject("identity_id", UUID.class),
            AddressLabel.valueOf(rs.getString("address_label")),
            rs.getString("recipient_name"),
            rs.getString("contact_phone_number"),
            rs.getString("address_line1"),
            rs.getString("address_line2"),
            rs.getString("landmark"),
            rs.getString("area_name"),
            rs.getString("district_name"),
            rs.getString("city"),
            rs.getString("state"),
            rs.getString("postal_code"),
            rs.getBigDecimal("latitude"),
            rs.getBigDecimal("longitude"),
            rs.getBoolean("is_default"),
            rs.getBoolean("is_active"),
            instant(rs, "created_at"),
            instant(rs, "updated_at")
        );
    }

    private static void validateAddress(CustomerAddressRequest request) {
        if (request == null) {
            throw ApiException.badRequest("CUSTOMER_ADDRESS_REQUIRED", "Customer address is required");
        }
        if (!StringUtils.hasText(request.recipientName())) {
            throw ApiException.badRequest("RECIPIENT_NAME_REQUIRED", "Recipient name is required");
        }
        if (!StringUtils.hasText(request.areaName())) {
            throw ApiException.badRequest("AREA_NAME_REQUIRED", "Area name is required");
        }
        if (!StringUtils.hasText(request.postalCode())) {
            throw ApiException.badRequest("POSTAL_CODE_REQUIRED", "Postal code is required");
        }
        validateCoordinates(request.latitude(), request.longitude());
    }

    private static void validateCoordinates(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            throw ApiException.badRequest(
                "ADDRESS_COORDINATES_REQUIRED",
                "Latitude and longitude are required for a saved delivery address"
            );
        }
        if (latitude.compareTo(MIN_LATITUDE) < 0 || latitude.compareTo(MAX_LATITUDE) > 0) {
            throw ApiException.badRequest("INVALID_LATITUDE", "Latitude must be between -90 and 90");
        }
        if (longitude.compareTo(MIN_LONGITUDE) < 0 || longitude.compareTo(MAX_LONGITUDE) > 0) {
            throw ApiException.badRequest("INVALID_LONGITUDE", "Longitude must be between -180 and 180");
        }
    }

    private static AddressLabel labelOrDefault(AddressLabel label) {
        return label == null ? AddressLabel.HOME : label;
    }

    private static Instant instant(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toInstant();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private record NearestAddress(CustomerAddressResponse address, long distanceMeters) {
    }
}
