package in.craves.subscription;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;
import org.springframework.util.ClassUtils;

class SpringConstructorAmbiguitySafetyTest {

    @Test
    void everySpringComponentWithMultipleConstructorsHasAnUnambiguousInjectionPath() {
        ClassPathScanningCandidateComponentProvider scanner =
            new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class, true));

        List<String> offenders = scanner.findCandidateComponents("in.craves.subscription").stream()
            .map(definition -> definition.getBeanClassName())
            .filter(Objects::nonNull)
            .map(name -> ClassUtils.resolveClassName(name, getClass().getClassLoader()))
            .filter(type -> type.getDeclaredConstructors().length > 1)
            .filter(type -> Arrays.stream(type.getDeclaredConstructors())
                .noneMatch(constructor -> constructor.getParameterCount() == 0))
            .filter(type -> Arrays.stream(type.getDeclaredConstructors())
                .noneMatch(this::isExplicitInjectionConstructor))
            .map(Class::getName)
            .sorted()
            .toList();

        assertThat(offenders)
            .as("Spring components with multiple constructors must declare a no-arg constructor or an explicit @Autowired constructor")
            .isEmpty();
    }

    private boolean isExplicitInjectionConstructor(Constructor<?> constructor) {
        return constructor.isAnnotationPresent(Autowired.class);
    }
}
