package in.craves.integration.delivery;

import java.util.random.RandomGenerator;
import org.springframework.stereotype.Component;

@Component
public class BetaSampler {
    public double sample(double alpha, double beta, RandomGenerator random) {
        if (alpha <= 0 || beta <= 0) throw new IllegalArgumentException("Beta parameters must be positive");
        double x = gamma(alpha, random);
        double y = gamma(beta, random);
        return x / (x + y);
    }

    private double gamma(double shape, RandomGenerator random) {
        if (shape < 1.0) {
            double u = Math.max(random.nextDouble(), Double.MIN_VALUE);
            return gamma(shape + 1.0, random) * Math.pow(u, 1.0 / shape);
        }
        double d = shape - 1.0 / 3.0;
        double c = 1.0 / Math.sqrt(9.0 * d);
        while (true) {
            double x = random.nextGaussian();
            double v = 1.0 + c * x;
            if (v <= 0) continue;
            v = v * v * v;
            double u = random.nextDouble();
            if (u < 1.0 - 0.0331 * x * x * x * x) return d * v;
            if (Math.log(u) < 0.5 * x * x + d * (1.0 - v + Math.log(v))) return d * v;
        }
    }
}
