package in.craves.subscription.schedule;

import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PlanCatalogClient {
    private final RestClient restClient;

    public PlanCatalogClient(
        @Value("${craves.catalog.base-url:https://api.craves.in/api/v1/catalog}") String baseUrl,
        RestClient.Builder builder
    ) {
        this.restClient = builder.baseUrl(baseUrl).build();
    }

    public MenuItem requireSellableOwnedItem(UUID menuItemId, UUID expectedChefIdentityId) {
        try {
            MenuItem item = restClient.get()
                .uri("/menu-items/{menuItemId}", menuItemId)
                .retrieve()
                .body(MenuItem.class);
            if (item == null || item.id() == null || item.kitchenId() == null || item.itemName() == null
                || item.itemName().isBlank() || item.price() == null || item.currency() == null || item.currency().isBlank()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Catalog returned an incomplete menu item");
            }
            if (!item.available() || !"ACTIVE".equalsIgnoreCase(item.status())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Menu item is not active and available");
            }
            Kitchen kitchen = restClient.get()
                .uri("/kitchens/{kitchenId}", item.kitchenId())
                .retrieve()
                .body(Kitchen.class);
            if (kitchen == null || kitchen.identityId() == null
                || !kitchen.identityId().equals(expectedChefIdentityId)
                || !"ACTIVE".equalsIgnoreCase(kitchen.status())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Menu item does not belong to the plan chef");
            }
            return item;
        } catch (HttpClientErrorException.NotFound exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Menu item or kitchen is unavailable");
        }
    }

    public record MenuItem(
        UUID id,
        UUID kitchenId,
        String itemName,
        String category,
        String foodType,
        BigDecimal price,
        String currency,
        boolean available,
        String status
    ) {
    }

    public record Kitchen(UUID id, UUID identityId, String status) {
    }
}
