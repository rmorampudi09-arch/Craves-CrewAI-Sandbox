package in.craves.catalog.web;

import in.craves.catalog.web.ApiDtos.FoodType;
import in.craves.catalog.web.ApiDtos.SpiceLevel;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public final class DiscoveryDtos {
    private DiscoveryDtos() {
    }

    public record PageMetadata(
        int page,
        int size,
        long totalElements,
        long totalPages,
        boolean hasNext
    ) {
    }

    public record NearbyKitchenSummaryResponse(
        UUID id,
        String kitchenName,
        String displayName,
        String description,
        String areaName,
        String city,
        String state,
        BigDecimal latitude,
        BigDecimal longitude,
        long distanceMeters,
        long activeMenuItemCount
    ) {
    }

    public record NearbyKitchenDiscoveryResponse(
        BigDecimal latitude,
        BigDecimal longitude,
        int radiusMeters,
        PageMetadata page,
        List<NearbyKitchenSummaryResponse> kitchens
    ) {
    }

    public record NearbyMenuItemSummaryResponse(
        UUID id,
        UUID kitchenId,
        String kitchenName,
        String kitchenDisplayName,
        String areaName,
        String city,
        String state,
        BigDecimal kitchenLatitude,
        BigDecimal kitchenLongitude,
        long distanceMeters,
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
        String primaryImageUrl
    ) {
    }

    public record NearbyMenuItemDiscoveryResponse(
        BigDecimal latitude,
        BigDecimal longitude,
        int radiusMeters,
        PageMetadata page,
        List<NearbyMenuItemSummaryResponse> menuItems
    ) {
    }
}
