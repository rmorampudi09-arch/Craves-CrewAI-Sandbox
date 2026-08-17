package in.craves.integration.delivery;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class BetaSamplerTest {
    @Test
    void samplesStayWithinProbabilityRange() {
        BetaSampler sampler = new BetaSampler();
        SplittableRandom random = new SplittableRandom(42);
        for (int i = 0; i < 1000; i++) {
            assertThat(sampler.sample(2.0, 3.0, random)).isBetween(0.0, 1.0);
        }
    }
}
