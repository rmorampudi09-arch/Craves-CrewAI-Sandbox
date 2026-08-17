package in.craves.auth.admin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "craves.internal-admin-rbac")
public class InternalAdminRbacProperties {
    private boolean apiEnabled = false;
    private int maximumListSize = 100;

    public boolean isApiEnabled() {
        return apiEnabled;
    }

    public void setApiEnabled(boolean apiEnabled) {
        this.apiEnabled = apiEnabled;
    }

    public int getMaximumListSize() {
        return maximumListSize;
    }

    public void setMaximumListSize(int maximumListSize) {
        if (maximumListSize < 1 || maximumListSize > 500) {
            throw new IllegalArgumentException("maximumListSize must be between 1 and 500");
        }
        this.maximumListSize = maximumListSize;
    }
}
