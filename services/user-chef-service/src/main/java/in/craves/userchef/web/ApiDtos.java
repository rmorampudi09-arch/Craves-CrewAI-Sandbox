package in.craves.userchef.web;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ApiDtos {
    private ApiDtos() {
    }

    public enum AddressLabel {
        HOME,
        WORK,
        OTHER
    }

    public enum ActiveLocationType {
        SAVED_ADDRESS,
        LIVE_GPS
    }

    public enum ChefApplicationStatus {
        NOT_SUBMITTED,
        PENDING,
        APPROVED,
        REJECTED
    }

    public enum KycDocumentType {
        APPLICANT_PHOTO,
        GOVERNMENT_ID_FRONT,
        GOVERNMENT_ID_BACK,
        TAX_ID_CARD,
        AADHAAR_CARD,
        PAN_CARD
    }

    public record CustomerProfileRequest(
        @NotBlank String firstName,
        @NotBlank String lastName,
        @Email String email
    ) {
    }

    public record CustomerProfileResponse(
        UUID id,
        UUID identityId,
        String registeredPhoneNumber,
        String firstName,
        String lastName,
        String email,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record CustomerAddressRequest(
        AddressLabel addressLabel,
        @NotBlank String recipientName,
        @NotBlank @Pattern(regexp = "^\\+?[0-9]{10,15}$") String contactPhoneNumber,
        @NotBlank String addressLine1,
        String addressLine2,
        String landmark,
        @NotBlank String areaName,
        String districtName,
        @NotBlank String city,
        @NotBlank String state,
        @NotBlank String postalCode,
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        Boolean isDefault
    ) {
    }

    public record CustomerAddressResponse(
        UUID id,
        UUID identityId,
        AddressLabel addressLabel,
        String recipientName,
        String contactPhoneNumber,
        String addressLine1,
        String addressLine2,
        String landmark,
        String areaName,
        String districtName,
        String city,
        String state,
        String postalCode,
        BigDecimal latitude,
        BigDecimal longitude,
        boolean isDefault,
        boolean active,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record CustomerLocationRecommendationResponse(
        ActiveLocationType locationType,
        BigDecimal latitude,
        BigDecimal longitude,
        CustomerAddressResponse selectedSavedAddress,
        Long distanceMeters,
        int matchRadiusMeters
    ) {
    }

    public record ChefApplicationRequest(
        @NotBlank @Email String email,
        @NotBlank String firstName,
        @NotBlank String lastName,
        @NotBlank String addressLine1,
        String addressLine2,
        String landmark,
        @NotBlank String city,
        @NotBlank String state,
        String postalCode,
        BigDecimal latitude,
        BigDecimal longitude
    ) {
    }

    public record KycDocumentResponse(
        UUID id,
        KycDocumentType documentType,
        String originalFileName,
        String blobContainer,
        String blobName,
        String contentType,
        long fileSizeBytes,
        String status,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record ChefApplicationResponse(
        UUID id,
        UUID identityId,
        String phoneNumber,
        String email,
        String firstName,
        String lastName,
        String addressLine1,
        String addressLine2,
        String landmark,
        String city,
        String state,
        String postalCode,
        BigDecimal latitude,
        BigDecimal longitude,
        ChefApplicationStatus status,
        String rejectionReason,
        Instant submittedAt,
        Instant reviewedAt,
        UUID reviewedByIdentityId,
        List<KycDocumentResponse> documents
    ) {
    }

    public record AdminDecisionRequest(String reason) {
    }
}
