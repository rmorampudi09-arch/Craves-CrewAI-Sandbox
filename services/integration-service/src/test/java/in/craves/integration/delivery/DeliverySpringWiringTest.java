package in.craves.integration.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.craves.integration.config.DeliveryIntelligenceProperties;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class DeliverySpringWiringTest {

    @Test
    void deliveryIntelligenceServiceUsesTheProductionConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(DeliveryProviderRepository.class,
                () -> mock(DeliveryProviderRepository.class));
            context.registerBean(DeliveryAssignmentRepository.class,
                () -> mock(DeliveryAssignmentRepository.class));
            context.registerBean(DeliveryMetricsRepository.class,
                () -> mock(DeliveryMetricsRepository.class));
            context.registerBean(DeliveryOutcomeScorer.class,
                () -> mock(DeliveryOutcomeScorer.class));
            context.registerBean(DeliverySuccessPredictor.class,
                () -> mock(DeliverySuccessPredictor.class));
            context.registerBean(BetaSampler.class,
                () -> mock(BetaSampler.class));
            context.registerBean(DeliveryIntelligenceProperties.class,
                () -> new DeliveryIntelligenceProperties());
            context.registerBean(ObjectMapper.class,
                () -> new ObjectMapper());
            context.registerBean(DeliveryIntelligenceService.class);

            context.refresh();

            assertThat(context.getBean(DeliveryIntelligenceService.class)).isNotNull();
        }
    }

    @Test
    void deliveryMaintenanceServiceUsesTheProductionConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(DeliveryMetricsRepository.class,
                () -> mock(DeliveryMetricsRepository.class));
            context.registerBean(DeliveryIntelligenceProperties.class,
                () -> new DeliveryIntelligenceProperties());
            context.registerBean(DeliveryMetricsMaintenanceService.class);

            context.refresh();

            assertThat(context.getBean(DeliveryMetricsMaintenanceService.class)).isNotNull();
        }
    }
}
