package in.craves.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import in.craves.subscription.billing.SubscriptionBillingService;
import in.craves.subscription.capacity.CapacityProjectionService;
import in.craves.subscription.capacity.CapacityService;
import in.craves.subscription.lifecycle.SubscriptionLifecycleService;
import in.craves.subscription.occurrence.OccurrenceGeneratorService;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class SubscriptionSpringConstructorWiringTest {
    @Test
    void clockBackedSpringServicesDeclareOneExplicitProductionInjectionConstructor() {
        for (Class<?> type : List.of(
            SubscriptionBillingService.class,
            OccurrenceGeneratorService.class,
            CapacityService.class,
            CapacityProjectionService.class,
            SubscriptionLifecycleService.class
        )) {
            List<Constructor<?>> injectionConstructors = Arrays.stream(type.getDeclaredConstructors())
                .filter(constructor -> constructor.isAnnotationPresent(Autowired.class))
                .toList();

            assertThat(injectionConstructors)
                .as("%s must expose exactly one explicit Spring injection constructor", type.getSimpleName())
                .hasSize(1);

            Constructor<?> constructor = injectionConstructors.getFirst();
            assertThat(Modifier.isPublic(constructor.getModifiers()))
                .as("%s production injection constructor must remain public", type.getSimpleName())
                .isTrue();
            assertThat(Arrays.asList(constructor.getParameterTypes()))
                .as("%s production injection constructor must not require the test Clock", type.getSimpleName())
                .doesNotContain(Clock.class);
        }
    }
}
