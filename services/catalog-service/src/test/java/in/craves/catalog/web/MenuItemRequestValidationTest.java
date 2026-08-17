package in.craves.catalog.web;

import static org.assertj.core.api.Assertions.assertThat;

import in.craves.catalog.web.ApiDtos.FoodType;
import in.craves.catalog.web.ApiDtos.MenuItemRequest;
import in.craves.catalog.web.ApiDtos.MenuItemStatus;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class MenuItemRequestValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsExplicitWeightAndThermoboxDecision() {
        MenuItemRequest request = request(650, false);

        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    void rejectsMissingPackageWeight() {
        MenuItemRequest request = request(null, false);

        assertThat(validator.validate(request))
            .extracting(violation -> violation.getPropertyPath().toString())
            .contains("unitPackageWeightGrams");
    }

    @Test
    void rejectsMissingThermoboxDecision() {
        MenuItemRequest request = request(650, null);

        assertThat(validator.validate(request))
            .extracting(violation -> violation.getPropertyPath().toString())
            .contains("thermoboxRequired");
    }

    private static MenuItemRequest request(Integer weightGrams, Boolean thermoboxRequired) {
        return new MenuItemRequest(
            "Home-style meal",
            "Freshly prepared meal",
            "MEALS",
            FoodType.VEG,
            new BigDecimal("199.00"),
            "INR",
            1,
            30,
            null,
            weightGrams,
            thermoboxRequired,
            true,
            MenuItemStatus.ACTIVE
        );
    }
}
