package in.craves.catalog;

import in.craves.catalog.config.CatalogDiscoveryProperties;
import in.craves.catalog.config.CatalogStorageProperties;
import in.craves.catalog.config.CravesJwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({CravesJwtProperties.class, CatalogStorageProperties.class, CatalogDiscoveryProperties.class})
public class CatalogServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CatalogServiceApplication.class, args);
    }
}
