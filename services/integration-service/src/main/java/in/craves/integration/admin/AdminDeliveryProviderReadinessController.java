package in.craves.integration.admin;

import in.craves.integration.delivery.production.DeliveryProviderReadinessService;
import in.craves.integration.delivery.production.DeliveryProviderReadinessService.ReadinessMatrix;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/operations/delivery-providers")
public class AdminDeliveryProviderReadinessController {
    private final DeliveryProviderReadinessService readinessService;

    public AdminDeliveryProviderReadinessController(DeliveryProviderReadinessService readinessService) {
        this.readinessService = readinessService;
    }

    @GetMapping("/readiness")
    public ResponseEntity<ReadinessMatrix> readiness() {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .body(readinessService.matrix());
    }
}
