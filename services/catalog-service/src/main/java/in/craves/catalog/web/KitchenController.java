package in.craves.catalog.web;

import in.craves.catalog.security.CravesPrincipal;
import in.craves.catalog.service.CatalogService;
import in.craves.catalog.web.ApiDtos.AvailabilityRequest;
import in.craves.catalog.web.ApiDtos.KitchenProfileRequest;
import in.craves.catalog.web.ApiDtos.KitchenProfileResponse;
import in.craves.catalog.web.ApiDtos.MenuItemImageResponse;
import in.craves.catalog.web.ApiDtos.MenuItemRequest;
import in.craves.catalog.web.ApiDtos.MenuItemResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/kitchens/me")
public class KitchenController {
    private final CatalogService catalogService;

    public KitchenController(CatalogService catalogService) {
        this.catalogService = catalogService;
    }

    @GetMapping
    public KitchenProfileResponse getMyKitchen(@AuthenticationPrincipal CravesPrincipal principal) {
        return catalogService.getMyKitchen(principal);
    }

    @PutMapping
    public KitchenProfileResponse upsertMyKitchen(
        @AuthenticationPrincipal CravesPrincipal principal,
        @Valid @RequestBody KitchenProfileRequest request
    ) {
        return catalogService.upsertMyKitchen(principal, request);
    }

    @GetMapping("/menu-items")
    public List<MenuItemResponse> listMyMenuItems(@AuthenticationPrincipal CravesPrincipal principal) {
        return catalogService.listMyMenuItems(principal);
    }

    @PostMapping("/menu-items")
    public MenuItemResponse createMenuItem(
        @AuthenticationPrincipal CravesPrincipal principal,
        @Valid @RequestBody MenuItemRequest request
    ) {
        return catalogService.createMenuItem(principal, request);
    }

    @PutMapping("/menu-items/{menuItemId}")
    public MenuItemResponse updateMenuItem(
        @AuthenticationPrincipal CravesPrincipal principal,
        @PathVariable UUID menuItemId,
        @Valid @RequestBody MenuItemRequest request
    ) {
        return catalogService.updateMenuItem(principal, menuItemId, request);
    }

    @PatchMapping("/menu-items/{menuItemId}/availability")
    public MenuItemResponse updateAvailability(
        @AuthenticationPrincipal CravesPrincipal principal,
        @PathVariable UUID menuItemId,
        @Valid @RequestBody AvailabilityRequest request
    ) {
        return catalogService.updateAvailability(principal, menuItemId, request);
    }

    @PostMapping("/menu-items/{menuItemId}/images")
    public MenuItemImageResponse uploadMenuItemImage(
        @AuthenticationPrincipal CravesPrincipal principal,
        @PathVariable UUID menuItemId,
        @RequestParam MultipartFile file,
        @RequestParam(defaultValue = "false") boolean primary
    ) {
        return catalogService.uploadMenuItemImage(principal, menuItemId, file, primary);
    }
}
