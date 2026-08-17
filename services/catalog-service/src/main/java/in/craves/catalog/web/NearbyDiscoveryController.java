package in.craves.catalog.web;

import in.craves.catalog.service.NearbyDiscoveryService;
import in.craves.catalog.web.DiscoveryDtos.NearbyKitchenDiscoveryResponse;
import in.craves.catalog.web.DiscoveryDtos.NearbyMenuItemDiscoveryResponse;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/discovery")
public class NearbyDiscoveryController {
    private final NearbyDiscoveryService nearbyDiscoveryService;

    public NearbyDiscoveryController(NearbyDiscoveryService nearbyDiscoveryService) {
        this.nearbyDiscoveryService = nearbyDiscoveryService;
    }

    @GetMapping("/kitchens")
    public NearbyKitchenDiscoveryResponse discoverKitchens(
        @RequestParam BigDecimal latitude,
        @RequestParam BigDecimal longitude,
        @RequestParam int radiusMeters,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return nearbyDiscoveryService.discoverKitchens(
            latitude,
            longitude,
            radiusMeters,
            page,
            size
        );
    }

    @GetMapping("/menu-items")
    public NearbyMenuItemDiscoveryResponse discoverMenuItems(
        @RequestParam BigDecimal latitude,
        @RequestParam BigDecimal longitude,
        @RequestParam int radiusMeters,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size
    ) {
        return nearbyDiscoveryService.discoverMenuItems(
            latitude,
            longitude,
            radiusMeters,
            page,
            size
        );
    }
}
