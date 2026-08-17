package in.craves.order.launchpolicy;

import in.craves.order.exception.OrderApiException;
import in.craves.order.launchpolicy.LaunchPolicyModels.LaunchPolicyResponse;
import in.craves.order.security.CravesPrincipal;
import in.craves.order.service.CatalogClient;
import in.craves.order.service.CatalogClient.CatalogKitchen;
import in.craves.order.service.CustomerAddressClient;
import in.craves.order.service.CustomerAddressClient.CustomerAddress;
import in.craves.order.web.ApiDtos.CheckoutRequest;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Aspect
@Component
@ConditionalOnProperty(prefix = "craves.launch-policy", name = "enforcement-enabled", havingValue = "true")
public class LaunchPolicyCheckoutAspect {
    private static final double EARTH_RADIUS_METERS = 6_371_000.0d;

    private final LaunchPolicyService launchPolicyService;
    private final JdbcTemplate jdbcTemplate;
    private final CustomerAddressClient customerAddressClient;
    private final CatalogClient catalogClient;

    public LaunchPolicyCheckoutAspect(
        LaunchPolicyService launchPolicyService,
        JdbcTemplate jdbcTemplate,
        CustomerAddressClient customerAddressClient,
        CatalogClient catalogClient
    ) {
        this.launchPolicyService = launchPolicyService;
        this.jdbcTemplate = jdbcTemplate;
        this.customerAddressClient = customerAddressClient;
        this.catalogClient = catalogClient;
    }

    @Around("execution(* in.craves.order.service.OrderService.checkout(..)) && args(principal, request)")
    public Object enforce(ProceedingJoinPoint joinPoint, CravesPrincipal principal, CheckoutRequest request) throws Throwable {
        if (principal == null || request == null || request.deliveryAddressId() == null) {
            return joinPoint.proceed();
        }
        LaunchPolicyResponse policy = launchPolicyService.requireActive();
        if (!"INR".equals(policy.currency())) {
            throw OrderApiException.serviceUnavailable(
                "LAUNCH_POLICY_CURRENCY_UNSUPPORTED",
                "Ordering is temporarily unavailable because the active launch policy currency is unsupported."
            );
        }

        BigDecimal subtotal = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(ci.unit_price_snapshot * ci.quantity), 0) " +
                "FROM order_schema.cart c LEFT JOIN order_schema.cart_item ci ON ci.cart_id = c.id " +
                "WHERE c.customer_identity_id = ?",
            BigDecimal.class,
            principal.identityId()
        );
        if (subtotal == null || subtotal.compareTo(policy.minimumOrderAmount()) < 0) {
            throw OrderApiException.badRequest(
                "MINIMUM_ORDER_NOT_MET",
                "The cart does not meet the configured minimum order amount."
            );
        }

        CustomerAddress dropoff = customerAddressClient.getActiveOwnedAddress(
            principal.identityId(),
            request.deliveryAddressId()
        );
        requireCoordinates(dropoff.latitude(), dropoff.longitude(), "DELIVERY_ADDRESS_COORDINATES_REQUIRED");

        List<UUID> kitchenIds = jdbcTemplate.query(
            "SELECT DISTINCT ci.kitchen_id FROM order_schema.cart c " +
                "JOIN order_schema.cart_item ci ON ci.cart_id = c.id " +
                "WHERE c.customer_identity_id = ?",
            (rs, rowNum) -> rs.getObject("kitchen_id", UUID.class),
            principal.identityId()
        );
        for (UUID kitchenId : kitchenIds) {
            CatalogKitchen kitchen = catalogClient.getKitchen(kitchenId);
            requireCoordinates(kitchen.latitude(), kitchen.longitude(), "KITCHEN_COORDINATES_REQUIRED");
            double distance = haversineMeters(
                kitchen.latitude().doubleValue(),
                kitchen.longitude().doubleValue(),
                dropoff.latitude().doubleValue(),
                dropoff.longitude().doubleValue()
            );
            if (distance > policy.maximumServiceabilityRadiusMeters()) {
                throw OrderApiException.badRequest(
                    "DELIVERY_ADDRESS_OUTSIDE_SERVICE_AREA",
                    "The selected delivery address is outside the configured launch service area."
                );
            }
        }
        return joinPoint.proceed();
    }

    private static void requireCoordinates(BigDecimal latitude, BigDecimal longitude, String code) {
        if (latitude == null || longitude == null) {
            throw OrderApiException.badRequest(code, "Complete coordinates are required for launch serviceability validation.");
        }
    }

    static double haversineMeters(double lat1, double lon1, double lat2, double lon2) {
        double latDistance = Math.toRadians(lat2 - lat1);
        double lonDistance = Math.toRadians(lon2 - lon1);
        double a = Math.sin(latDistance / 2) * Math.sin(latDistance / 2)
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
            * Math.sin(lonDistance / 2) * Math.sin(lonDistance / 2);
        return EARTH_RADIUS_METERS * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    }
}
