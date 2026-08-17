package in.craves.integration;

import static org.assertj.core.api.Assertions.assertThat;

import in.craves.integration.delivery.command.DeliverySchedulingConfiguration;
import in.craves.integration.refund.RefundStatusPublisherSchedulingConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.scheduling.annotation.EnableScheduling;

class IntegrationServiceSchedulingTest {

    @Test
    void schedulingIsNotEnabledGlobally() {
        assertThat(
            AnnotatedElementUtils.hasAnnotation(
                IntegrationServiceApplication.class,
                EnableScheduling.class
            )
        ).isFalse();
    }

    @Test
    void refundStatusSchedulingRemainsScopedToItsActivationFlag() {
        assertThat(
            AnnotatedElementUtils.hasAnnotation(
                RefundStatusPublisherSchedulingConfiguration.class,
                EnableScheduling.class
            )
        ).isTrue();

        ConditionalOnProperty condition = AnnotatedElementUtils.findMergedAnnotation(
            RefundStatusPublisherSchedulingConfiguration.class,
            ConditionalOnProperty.class
        );

        assertThat(condition).isNotNull();
        assertThat(condition.prefix()).isEqualTo("craves.refund");
        assertThat(condition.name()).containsExactly("status-publisher-enabled");
        assertThat(condition.havingValue()).isEqualTo("true");
    }

    @Test
    void deliverySchedulingIsScopedToDeliveryWorkerFlags() {
        assertThat(
            AnnotatedElementUtils.hasAnnotation(
                DeliverySchedulingConfiguration.class,
                EnableScheduling.class
            )
        ).isTrue();

        ConditionalOnExpression condition = AnnotatedElementUtils.findMergedAnnotation(
            DeliverySchedulingConfiguration.class,
            ConditionalOnExpression.class
        );

        assertThat(condition).isNotNull();
        assertThat(condition.value())
            .contains("craves.delivery-command.enabled")
            .contains("craves.delivery-command.reconciliation-enabled")
            .contains("craves.delivery-command.webhook-processing-enabled")
            .contains("craves.delivery-command.tracking-reconciliation-enabled")
            .contains("craves.delivery-command.status-publisher-enabled");
    }
}
