package in.craves.catalog.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "craves.storage")
public class CatalogStorageProperties {
    private String endpointValue;
    private String mediaContainer = "media";
    private String publicMediaBaseUrl;
    private long maxImageFileSizeBytes = 8 * 1024 * 1024;

    public String getEndpointValue() {
        return endpointValue;
    }

    public void setEndpointValue(String endpointValue) {
        this.endpointValue = endpointValue;
    }

    public String getMediaContainer() {
        return mediaContainer;
    }

    public void setMediaContainer(String mediaContainer) {
        this.mediaContainer = mediaContainer;
    }

    public String getPublicMediaBaseUrl() {
        return publicMediaBaseUrl;
    }

    public void setPublicMediaBaseUrl(String publicMediaBaseUrl) {
        this.publicMediaBaseUrl = publicMediaBaseUrl;
    }

    public long getMaxImageFileSizeBytes() {
        return maxImageFileSizeBytes;
    }

    public void setMaxImageFileSizeBytes(long maxImageFileSizeBytes) {
        this.maxImageFileSizeBytes = maxImageFileSizeBytes;
    }
}
