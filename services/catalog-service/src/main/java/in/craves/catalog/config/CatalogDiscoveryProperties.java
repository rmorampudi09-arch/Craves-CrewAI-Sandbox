package in.craves.catalog.config;

import java.math.BigDecimal;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "craves.discovery")
public class CatalogDiscoveryProperties {
    private BigDecimal defaultRadiusKm = BigDecimal.valueOf(10);
    private BigDecimal maxRadiusKm = BigDecimal.valueOf(15);
    private int maxQueryRadiusMeters = 50_000;
    private int maxPageSize = 100;

    public BigDecimal getDefaultRadiusKm() {
        return defaultRadiusKm;
    }

    public void setDefaultRadiusKm(BigDecimal defaultRadiusKm) {
        this.defaultRadiusKm = defaultRadiusKm;
    }

    public BigDecimal getMaxRadiusKm() {
        return maxRadiusKm;
    }

    public void setMaxRadiusKm(BigDecimal maxRadiusKm) {
        this.maxRadiusKm = maxRadiusKm;
    }

    public int getMaxQueryRadiusMeters() {
        return maxQueryRadiusMeters;
    }

    public void setMaxQueryRadiusMeters(int maxQueryRadiusMeters) {
        this.maxQueryRadiusMeters = maxQueryRadiusMeters;
    }

    public int getMaxPageSize() {
        return maxPageSize;
    }

    public void setMaxPageSize(int maxPageSize) {
        this.maxPageSize = maxPageSize;
    }
}
