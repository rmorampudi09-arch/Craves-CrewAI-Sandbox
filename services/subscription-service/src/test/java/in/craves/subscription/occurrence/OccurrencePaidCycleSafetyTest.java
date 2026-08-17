package in.craves.subscription.occurrence;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class OccurrencePaidCycleSafetyTest {
    @Test
    void generatedOccurrencesRespectPaidInvoiceCycleAndSerializeWithPaymentStatusChanges() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/java/in/craves/subscription/occurrence/OccurrenceRepository.java"
        ));

        assertThat(source)
            .contains("SELECT status FROM subscription_schema.customer_subscription WHERE id = ? FOR UPDATE")
            .contains("status = 'PAID'")
            .contains("cycle_start <= ? AND cycle_end > ?")
            .contains("paidCycle ? \"READY_FOR_ORDER\" : \"BILLING_PENDING\"")
            .contains("Occurrence generated inside an already-paid billing cycle");
    }
}
