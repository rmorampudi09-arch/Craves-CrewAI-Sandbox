package in.craves.subscription.plan;

import static org.assertj.core.api.Assertions.assertThat;

import in.craves.subscription.schedule.PlanScheduleController;
import in.craves.subscription.web.SubscriptionController;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;

class ChefPlanEndpointOwnershipTest {
    @Test
    void adminControllerCannotCreateSubscriptionPlans() {
        boolean adminCreateMapping = Arrays.stream(SubscriptionController.class.getDeclaredMethods())
            .map(method -> method.getAnnotation(PostMapping.class))
            .filter(annotation -> annotation != null)
            .flatMap(annotation -> Arrays.stream(annotation.value()))
            .anyMatch("/admin/subscription-plans"::equals);

        assertThat(adminCreateMapping).isFalse();
    }

    @Test
    void adminScheduleControllerIsReadOnly() {
        List<Method> methods = Arrays.asList(PlanScheduleController.class.getDeclaredMethods());
        assertThat(methods).anyMatch(method -> method.isAnnotationPresent(GetMapping.class));
        assertThat(methods).noneMatch(method -> method.isAnnotationPresent(PutMapping.class));
        assertThat(methods).noneMatch(method -> method.isAnnotationPresent(PostMapping.class));
    }

    @Test
    void chefWorkflowExposesCreateScheduleAndSubmitWhileAdminOnlyReviews() {
        List<Method> methods = Arrays.asList(ChefPlanWorkflowController.class.getDeclaredMethods());

        assertThat(methods).anyMatch(method -> mappingContains(method.getAnnotation(PostMapping.class), "/chef/subscription-plans"));
        assertThat(methods).anyMatch(method -> mappingContains(method.getAnnotation(PutMapping.class), "/chef/subscription-plans/{planId}/schedule"));
        assertThat(methods).anyMatch(method -> mappingContains(method.getAnnotation(PostMapping.class), "/chef/subscription-plans/{planId}/submit"));
        assertThat(methods).anyMatch(method -> mappingContains(method.getAnnotation(PostMapping.class), "/admin/subscription-plans/{planId}/review"));
    }

    private static boolean mappingContains(PostMapping mapping, String value) {
        return mapping != null && Arrays.asList(mapping.value()).contains(value);
    }

    private static boolean mappingContains(PutMapping mapping, String value) {
        return mapping != null && Arrays.asList(mapping.value()).contains(value);
    }
}
