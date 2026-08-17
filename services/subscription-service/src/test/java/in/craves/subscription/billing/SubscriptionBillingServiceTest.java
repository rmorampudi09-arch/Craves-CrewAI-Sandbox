package in.craves.subscription.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

class SubscriptionBillingServiceTest {
    @Test
    void weeklyCycleAdvancesSevenDays() {
        LocalDate start = LocalDate.of(2026, 8, 3);
        assertThat(SubscriptionBillingService.cycleEnd(start, "WEEKLY"))
            .isEqualTo(LocalDate.of(2026, 8, 10));
    }

    @Test
    void monthlyCycleUsesCalendarMonth() {
        LocalDate start = LocalDate.of(2026, 1, 31);
        assertThat(SubscriptionBillingService.cycleEnd(start, "MONTHLY"))
            .isEqualTo(LocalDate.of(2026, 2, 28));
    }

    @Test
    void springCanInstantiateBillingServiceWithProductionConstructor() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton(
                "subscriptionBillingProperties",
                mock(SubscriptionBillingProperties.class)
            );
            context.getBeanFactory().registerSingleton(
                "subscriptionBillingRepository",
                mock(SubscriptionBillingRepository.class)
            );
            context.getBeanFactory().registerSingleton("objectMapper", new ObjectMapper());
            context.register(SubscriptionBillingService.class);
            context.refresh();

            assertThat(context.getBean(SubscriptionBillingService.class)).isNotNull();
        }
    }
}
