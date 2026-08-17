package in.craves.userchef.web;

import in.craves.userchef.location.AzureMapsReverseGeocoder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/addresses")
public class CustomerAddressGeocodingController {
    private final AzureMapsReverseGeocoder geocoder;

    public CustomerAddressGeocodingController(AzureMapsReverseGeocoder geocoder) {
        this.geocoder = geocoder;
    }

    @PostMapping("/reverse-geocode")
    public AzureMapsReverseGeocoder.ReverseGeocodedAddress reverseGeocode(
        @Valid @RequestBody ReverseGeocodeRequest request
    ) {
        return geocoder.reverseGeocode(request.latitude(), request.longitude());
    }

    public record ReverseGeocodeRequest(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude
    ) {
    }
}
