package in.craves.integration.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import in.craves.integration.delivery.command.DeliveryCommandProperties;
import in.craves.integration.delivery.command.DeliveryProviderCatalogRepository;
import in.craves.integration.delivery.command.DeliveryProviderRouter;
import java.time.Clock;
import java.util.concurrent.ExecutorService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

class DeliverySchedulingConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
        .withUserConfiguration(TestConfiguration.class);

    @Test
    void wiresDeliveryProviderRouterWithQuoteExecutorAndUtcClock() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(DeliveryProviderRouter.class);
            assertThat(context).hasBean("deliveryQuoteExecutor");
            assertThat(context).hasSingleBean(Clock.class);

            ExecutorService executor = context.getBean("deliveryQuoteExecutor", ExecutorService.class);
            assertThat(executor.isShutdown()).isFalse();

            Clock clock = context.getBean(Clock.class);
            assertThat(clock.getZone()).isEqualTo(Clock.systemUTC().getZone());
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({DeliverySchedulingConfiguration.class, DeliveryProviderRouter.class})
    static class TestConfiguration {

        @Bean
        DeliveryCommandProperties deliveryCommandProperties() {
            return new DeliveryCommandProperties();
        }

        @Bean
        DeliveryProviderCatalogRepository deliveryProviderCatalogRepository() {
            return mock(DeliveryProviderCatalogRepository.class);
        }

        @Bean
        DeliveryIntelligenceService deliveryIntelligenceService() {
            return mock(DeliveryIntelligenceService.class);
        }

        @Bean
        DeliveryAssignmentRepository deliveryAssignmentRepository() {
            return mock(DeliveryAssignmentRepository.class);
        }
    }
}
