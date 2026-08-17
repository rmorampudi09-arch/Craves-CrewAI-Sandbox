package in.craves.order.service;

import in.craves.order.config.CatalogClientProperties;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CatalogClient {
    private final RestClient restClient;

    public CatalogClient(CatalogClientProperties properties, RestClient.Builder builder) {
        this.restClient = builder.baseUrl(properties.getBaseUrl()).build();
    }

    public CatalogMenuItem getActiveMenuItem(UUID menuItemId) {
        try {
            CatalogMenuItem item = restClient.get()
                .uri("/menu-items/{menuItemId}", menuItemId)
                .retrieve()
                .body(CatalogMenuItem.class);
            if (item == null || item.id() == null || item.kitchenId() == null || item.price() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Catalog item response is incomplete");
            }
            if (item.unitPackageWeightGrams() == null || item.unitPackageWeightGrams() <= 0
                || item.thermoboxRequired() == null) {
                throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Menu item delivery metadata is incomplete"
                );
            }
            return item;
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Menu item is not active or available");
        }
    }

    public CatalogKitchen getKitchen(UUID kitchenId) {
        try {
            CatalogKitchen kitchen = restClient.get()
                .uri("/kitchens/{kitchenId}", kitchenId)
                .retrieve()
                .body(CatalogKitchen.class);
            if (kitchen == null || kitchen.id() == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Catalog kitchen response is incomplete");
            }
            return kitchen;
        } catch (HttpClientErrorException.NotFound ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Kitchen is not active");
        }
    }

    public record CatalogMenuItem(
        UUID id,
        UUID kitchenId,
        String itemName,
        String description,
        String category,
        String foodType,
        BigDecimal price,
        String currency,
        Integer servesCount,
        Integer preparationTimeMinutes,
        String spiceLevel,
        Integer unitPackageWeightGrams,
        Boolean thermoboxRequired,
        boolean available,
        String status
    ) {
        public CatalogMenuItem(
            UUID id,
            UUID kitchenId,
            String itemName,
            String description,
            String category,
            String foodType,
            BigDecimal price,
            String currency,
            Integer servesCount,
            Integer preparationTimeMinutes,
            String spiceLevel,
            boolean available,
            String status
        ) {
            this(
                id,
                kitchenId,
                itemName,
                description,
                category,
                foodType,
                price,
                currency,
                servesCount,
                preparationTimeMinutes,
                spiceLevel,
                null,
                null,
                available,
                status
            );
        }
    }

    public record CatalogKitchen(
        UUID id,
        UUID identityId,
        String kitchenName,
        String displayName,
        String description,
        String phoneNumber,
        String email,
        String addressLine1,
        String addressLine2,
        String landmark,
        String areaName,
        String city,
        String state,
        String postalCode,
        BigDecimal latitude,
        BigDecimal longitude,
        String status
    ) {
        public CatalogKitchen(
            UUID id,
            UUID identityId,
            String kitchenName,
            String displayName,
            String areaName,
            String city,
            String status
        ) {
            this(
                id,
                identityId,
                kitchenName,
                displayName,
                null,
                null,
                null,
                null,
                null,
                null,
                areaName,
                city,
                null,
                null,
                null,
                null,
                status
            );
        }
    }
}
