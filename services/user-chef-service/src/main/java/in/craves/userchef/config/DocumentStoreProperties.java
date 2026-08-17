package in.craves.userchef.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "craves.storage")
public class DocumentStoreProperties {
    private String endpointValue;
    private String documentsContainer = "documents";
    private long kycMaxFileSizeBytes = 10 * 1024 * 1024;

    public String getEndpointValue() {
        return endpointValue;
    }

    public void setEndpointValue(String endpointValue) {
        this.endpointValue = endpointValue;
    }

    public String getDocumentsContainer() {
        return documentsContainer;
    }

    public void setDocumentsContainer(String documentsContainer) {
        this.documentsContainer = documentsContainer;
    }

    public long getKycMaxFileSizeBytes() {
        return kycMaxFileSizeBytes;
    }

    public void setKycMaxFileSizeBytes(long kycMaxFileSizeBytes) {
        this.kycMaxFileSizeBytes = kycMaxFileSizeBytes;
    }
}
