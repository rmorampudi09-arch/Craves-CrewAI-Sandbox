package in.craves.catalog.web;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ApiDtos {
    private ApiDtos() {
    }

    public enum KitchenStatus {
        DRAFT, ACTIVE, INACTIVE, SUSPENDED
    }

    public enum MenuItemStatus {
        DRAFT, ACTIVE, INACTIVE
    }

    public enum FoodType {
        VEG, NON_VEG, EGG
    }

    public enum SpiceLevel {
        MILD, MEDIUM, SPICY
    }

    public record KitchenProfileRequest(
        @NotBlank String kitchenName,
        String displayName,
        String description,
        String phoneNumber,
        String email,
        @NotBlank String addressLine1,
        String addressLine2,
        String landmark,
        String areaName,
        @NotBlank String city,
        @NotBlank String state,
        String postalCode,
        BigDecimal latitude,
        BigDecimal longitude,
        KitchenStatus status
    ) {
    }

    public record KitchenProfileResponse(
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
        KitchenStatus status,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record MenuItemRequest(
        @NotBlank String itemName,
        String description,
        @NotBlank String category,
        @NotNull FoodType foodType,
        @NotNull @DecimalMin("0.01") BigDecimal price,
        String currency,
        @Positive Integer servesCount,
        @Positive Integer preparationTimeMinutes,
        SpiceLevel spiceLevel,
        @NotNull @Positive Integer unitPackageWeightGrams,
        @NotNull Boolean thermoboxRequired,
        Boolean available,
        MenuItemStatus status
    ) {
    }

    public record MenuItemResponse(
        UUID id,
        UUID kitchenId,
        String itemName,
        String description,
        String category,
        FoodType foodType,
        BigDecimal price,
        String currency,
        Integer servesCount,
        Integer preparationTimeMinutes,
        SpiceLevel spiceLevel,
        Integer unitPackageWeightGrams,
        Boolean thermoboxRequired,
        boolean available,
        MenuItemStatus status,
        List<MenuItemImageResponse> images,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record MenuItemImageResponse(
        UUID id,
        UUID menuItemId,
        String blobContainer,
        String blobName,
        String contentType,
        long fileSizeBytes,
        String publicUrl,
        int sortOrder,
        boolean primary,
        Instant createdAt
    ) {
    }

    public record AvailabilityRequest(
        boolean available,
        String reason
    ) {
    }

    public record PublicKitchenSummaryResponse(
        UUID id,
        String kitchenName,
        String displayName,
        String description,
        String areaName,
        String city,
        BigDecimal latitude,
        BigDecimal longitude,
        BigDecimal distanceKm,
        long activeMenuItemCount
    ) {
    }

    public record DiscoveryRadiusResponse(
        String city,
        String areaName,
        BigDecimal radiusKm,
        BigDecimal maxRadiusKm
    ) {
    }

    public record PublicKitchenDiscoveryResponse(
        DiscoveryRadiusResponse radius,
        List<PublicKitchenSummaryResponse> kitchens
    ) {
    }
}
