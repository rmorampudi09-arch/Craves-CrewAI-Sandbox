package in.craves.userchef.web;

import in.craves.userchef.security.CurrentUser;
import in.craves.userchef.service.CustomerFavoriteService;
import in.craves.userchef.service.CustomerFavoriteService.CustomerFavorite;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customer/favorites")
public class CustomerFavoriteController {
    private final CustomerFavoriteService service;

    public CustomerFavoriteController(CustomerFavoriteService service) {
        this.service = service;
    }

    @GetMapping
    public List<CustomerFavorite> list(@AuthenticationPrincipal CurrentUser user) {
        return service.list(user);
    }

    @PutMapping("/{menuItemId}")
    public CustomerFavorite save(
        @AuthenticationPrincipal CurrentUser user,
        @PathVariable UUID menuItemId
    ) {
        return service.save(user, menuItemId);
    }

    @DeleteMapping("/{menuItemId}")
    public ResponseEntity<Void> remove(
        @AuthenticationPrincipal CurrentUser user,
        @PathVariable UUID menuItemId
    ) {
        service.remove(user, menuItemId);
        return ResponseEntity.noContent().build();
    }
}
