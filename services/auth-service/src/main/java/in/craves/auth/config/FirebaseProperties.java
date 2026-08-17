package in.craves.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "craves.firebase")
public class FirebaseProperties {
    private String projectId;
    private String credentialsJsonBase64;
    private boolean checkRevoked = true;

    public String getProjectId() {
        return projectId;
    }

    public void setProjectId(String projectId) {
        this.projectId = projectId;
    }

    public String getCredentialsJsonBase64() {
        return credentialsJsonBase64;
    }

    public void setCredentialsJsonBase64(String credentialsJsonBase64) {
        this.credentialsJsonBase64 = credentialsJsonBase64;
    }

    public boolean isCheckRevoked() {
        return checkRevoked;
    }

    public void setCheckRevoked(boolean checkRevoked) {
        this.checkRevoked = checkRevoked;
    }
}
