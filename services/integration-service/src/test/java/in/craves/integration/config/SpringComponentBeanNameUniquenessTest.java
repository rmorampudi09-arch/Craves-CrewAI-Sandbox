package in.craves.integration.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.SimpleBeanDefinitionRegistry;
import org.springframework.context.annotation.AnnotationBeanNameGenerator;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.stereotype.Component;

class SpringComponentBeanNameUniquenessTest {

    @Test
    void componentScanDoesNotProduceConflictingDefaultBeanNames() {
        ClassPathScanningCandidateComponentProvider scanner =
            new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(Component.class));

        SimpleBeanDefinitionRegistry registry = new SimpleBeanDefinitionRegistry();
        Map<String, List<String>> classesByBeanName = new LinkedHashMap<>();

        for (BeanDefinition candidate : scanner.findCandidateComponents("in.craves.integration")) {
            String beanName = AnnotationBeanNameGenerator.INSTANCE.generateBeanName(candidate, registry);
            classesByBeanName.computeIfAbsent(beanName, ignored -> new ArrayList<>())
                .add(candidate.getBeanClassName());
        }

        Map<String, List<String>> conflicts = new LinkedHashMap<>();
        classesByBeanName.forEach((beanName, classNames) -> {
            if (classNames.size() > 1) {
                conflicts.put(beanName, List.copyOf(classNames));
            }
        });

        assertThat(conflicts)
            .as("Spring component bean names must be unique: %s", conflicts)
            .isEmpty();
    }
}
