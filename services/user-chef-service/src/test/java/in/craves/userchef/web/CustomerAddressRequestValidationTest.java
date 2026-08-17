package in.craves.userchef.web;

import static org.assertj.core.api.Assertions.assertThat;

import in.craves.userchef.web.ApiDtos.AddressLabel;
import in.craves.userchef.web.ApiDtos.CustomerAddressRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CustomerAddressRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsCompleteGeocodedAddress() {
        assertThat(validator.validate(request(
            "Customer Name",
            "Madhapur",
            "Hyderabad",
            "500081",
            new BigDecimal("17.4483"),
            new BigDecimal("78.3915")
        ))).isEmpty();
    }

    @Test
    void temporarilyAcceptsMissingDistrictForRollingDeploymentCompatibility() {
        assertThat(validator.validate(request(
            "Customer Name",
            "Madhapur",
            null,
            "500081",
            new BigDecimal("17.4483"),
            new BigDecimal("78.3915")
        ))).isEmpty();
    }

    @Test
    void rejectsMissingRecipientName() {
        assertThat(validator.validate(request(
            null,
            "Madhapur",
            "Hyderabad",
            "500081",
            new BigDecimal("17.4483"),
            new BigDecimal("78.3915")
        )))
            .extracting(violation -> violation.getPropertyPath().toString())
            .contains("recipientName");
    }

    @Test
    void rejectsMissingAreaName() {
        assertThat(validator.validate(request(
            "Customer Name",
            null,
            "Hyderabad",
            "500081",
            new BigDecimal("17.4483"),
            new BigDecimal("78.3915")
        )))
            .extracting(violation -> violation.getPropertyPath().toString())
            .contains("areaName");
    }

    @Test
    void rejectsMissingPostalCode() {
        assertThat(validator.validate(request(
            "Customer Name",
            "Madhapur",
            "Hyderabad",
            null,
            new BigDecimal("17.4483"),
            new BigDecimal("78.3915")
        )))
            .extracting(violation -> violation.getPropertyPath().toString())
            .contains("postalCode");
    }

    @Test
    void rejectsMissingLatitude() {
        assertThat(validator.validate(request(
            "Customer Name",
            "Madhapur",
            "Hyderabad",
            "500081",
            null,
            new BigDecimal("78.3915")
        )))
            .extracting(violation -> violation.getPropertyPath().toString())
            .contains("latitude");
    }

    @Test
    void rejectsOutOfRangeLongitude() {
        assertThat(validator.validate(request(
            "Customer Name",
            "Madhapur",
            "Hyderabad",
            "500081",
            new BigDecimal("17.4483"),
            new BigDecimal("181")
        )))
            .extracting(violation -> violation.getPropertyPath().toString())
            .contains("longitude");
    }

    private static CustomerAddressRequest request(
        String recipientName,
        String areaName,
        String districtName,
        String postalCode,
        BigDecimal latitude,
        BigDecimal longitude
    ) {
        return new CustomerAddressRequest(
            AddressLabel.HOME,
            recipientName,
            "+919876543210",
            "Flat 101, Test Residency",
            "Road No. 1",
            "Near Metro",
            areaName,
            districtName,
            "Hyderabad",
            "Telangana",
            postalCode,
            latitude,
            longitude,
            true
        );
    }
}
