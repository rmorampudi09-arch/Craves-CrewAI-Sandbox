package in.craves.userchef.location;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AzureMapsReverseGeocoderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void normalizesAzureMapsAddressForIndianCustomerAddressForm() throws Exception {
        String json = """
            {
              "features": [
                {
                  "properties": {
                    "confidence": "High",
                    "address": {
                      "formattedAddress": "12 Lake Road, Indiranagar, Bengaluru 560038, Karnataka",
                      "streetNumber": "12",
                      "streetName": "Lake Road",
                      "neighborhood": "Indiranagar",
                      "locality": "Bengaluru",
                      "adminDistricts": [
                        {"name": "Karnataka", "shortName": "KA"},
                        {"name": "Bengaluru Urban"}
                      ],
                      "postalCode": "560038",
                      "countryRegion": {"name": "India"}
                    }
                  }
                }
              ]
            }
            """;

        var result = AzureMapsReverseGeocoder.parseResponse(objectMapper.readTree(json));

        assertEquals("12 Lake Road, Indiranagar, Bengaluru 560038, Karnataka", result.formattedAddress());
        assertEquals("12", result.houseNumber());
        assertEquals("Lake Road", result.street());
        assertEquals("Indiranagar", result.area());
        assertEquals("Bengaluru", result.city());
        assertEquals("Bengaluru Urban", result.district());
        assertEquals("Karnataka", result.state());
        assertEquals("560038", result.postalCode());
        assertEquals("India", result.country());
        assertEquals("High", result.confidence());
        assertTrue(result.preciseHouseNumber());
    }

    @Test
    void fallsBackWithoutInventingPrivateHouseNumber() throws Exception {
        String json = """
            {
              "features": [
                {
                  "properties": {
                    "confidence": "Medium",
                    "address": {
                      "formattedAddress": "Madhapur, Hyderabad, Telangana",
                      "locality": "Hyderabad",
                      "adminDistricts": [
                        {"name": "Telangana"},
                        {"name": "Ranga Reddy"}
                      ],
                      "countryRegion": {"name": "India"}
                    }
                  }
                }
              ]
            }
            """;

        var result = AzureMapsReverseGeocoder.parseResponse(objectMapper.readTree(json));

        assertNull(result.houseNumber());
        assertEquals("Hyderabad", result.area());
        assertEquals("Ranga Reddy", result.district());
        assertFalse(result.preciseHouseNumber());
    }

    @Test
    void rejectsUnusableProviderResponse() throws Exception {
        assertNull(AzureMapsReverseGeocoder.parseResponse(objectMapper.readTree("{\"features\":[]}")));
    }
}
