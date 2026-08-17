package in.craves.integration.delivery.borzo;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.integration.config.BorzoProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.web.client.RestClient;

class BorzoSpringWiringTest {

    @Test
    void springSelectsTheProductionApiClientConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(BorzoProperties.class, () -> new BorzoProperties());
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());
            context.registerBean(BorzoStatusMapper.class, () -> new BorzoStatusMapper());
            context.registerBean(RestClient.Builder.class, () -> RestClient.builder());
            context.registerBean(BorzoApiClient.class);

            context.refresh();

            assertThat(context.getBean(BorzoApiClient.class).providerId()).isEqualTo("borzo");
        }
    }
}
