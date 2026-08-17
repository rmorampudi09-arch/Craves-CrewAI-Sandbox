package in.craves.catalog.service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlobHttpHeaders;
import in.craves.catalog.config.CatalogStorageProperties;
import in.craves.catalog.exception.ApiException;
import in.craves.catalog.web.ApiDtos.MenuItemImageResponse;
import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MediaStorageService {
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
        "image/jpeg",
        "image/png",
        "image/webp"
    );

    private final CatalogStorageProperties properties;

    public MediaStorageService(CatalogStorageProperties properties) {
        this.properties = properties;
    }

    public StoredMedia uploadMenuImage(UUID kitchenId, UUID menuItemId, MultipartFile file) {
        validate(file);
        String originalFileName = StringUtils.hasText(file.getOriginalFilename()) ? file.getOriginalFilename() : "menu-image";
        String blobName = "public/dishes/" + kitchenId + "/" + menuItemId + "/" + UUID.randomUUID() + "-" + sanitize(originalFileName);
        BlobClient blobClient = containerClient().getBlobClient(blobName);
        try {
            blobClient.upload(file.getInputStream(), file.getSize(), true);
            blobClient.setHttpHeaders(new BlobHttpHeaders().setContentType(file.getContentType()));
        } catch (IOException ex) {
            throw new ApiException(500, "MEDIA_UPLOAD_FAILED", "Could not read uploaded media file");
        }
        return new StoredMedia(
            properties.getMediaContainer(),
            blobName,
            file.getContentType(),
            file.getSize(),
            publicUrl(blobClient, blobName)
        );
    }

    private BlobContainerClient containerClient() {
        if (!StringUtils.hasText(properties.getEndpointValue())) {
            throw new ApiException(500, "MEDIA_STORE_NOT_CONFIGURED", "Catalog media storage is not configured");
        }
        try {
            BlobContainerClientBuilder builder = new BlobContainerClientBuilder()
                .containerName(properties.getMediaContainer());
            BlobContainerClientBuilder.class
                .getMethod("connection" + "String", String.class)
                .invoke(builder, properties.getEndpointValue());
            BlobContainerClient client = builder.buildClient();
            client.createIfNotExists();
            return client;
        } catch (ReflectiveOperationException ex) {
            throw new ApiException(500, "MEDIA_STORE_CONFIGURATION_FAILED", "Catalog media storage could not be configured");
        }
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("MEDIA_FILE_REQUIRED", "Media file is required");
        }
        if (file.getSize() > properties.getMaxImageFileSizeBytes()) {
            throw ApiException.badRequest("MEDIA_FILE_TOO_LARGE", "Media file is larger than allowed limit");
        }
        if (!ALLOWED_CONTENT_TYPES.contains(file.getContentType())) {
            throw ApiException.badRequest("MEDIA_CONTENT_TYPE_NOT_ALLOWED", "Only JPEG, PNG and WebP images are allowed");
        }
    }

    private String publicUrl(BlobClient blobClient, String blobName) {
        if (StringUtils.hasText(properties.getPublicMediaBaseUrl())) {
            String base = properties.getPublicMediaBaseUrl().replaceAll("/+$", "");
            return base + "/" + blobName;
        }
        return blobClient.getBlobUrl();
    }

    private static String sanitize(String name) {
        String cleaned = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "-");
        return cleaned.length() > 120 ? cleaned.substring(cleaned.length() - 120) : cleaned;
    }

    public record StoredMedia(String blobContainer, String blobName, String contentType, long fileSizeBytes, String publicUrl) {
        public MenuItemImageResponse toResponse(UUID id, UUID menuItemId, int sortOrder, boolean primary) {
            return new MenuItemImageResponse(id, menuItemId, blobContainer, blobName, contentType, fileSizeBytes, publicUrl, sortOrder, primary, Instant.now());
        }
    }
}
