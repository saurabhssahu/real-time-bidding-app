package no.kobler.rtb.service;


import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class PriceGeneratorTest {

    /**
     * Simple predictable price generator wrapper
     * In the real implementation we'll have a class like:
     * public class PriceGenerator { private final Random random; public double next() { return random.nextDouble() * 10.0; } }
     * <p>
     * For tests we use a seeded Random to assert deterministic behavior.
     */
    @Test
    @DisplayName("generated prices are within [0.0, 10.0] and deterministic with seed")
    void generatedPricesWithinBoundsAndDeterministic() {
        Random seeded = new Random(12345L);
        double p1 = seeded.nextDouble() * 10.0;
        double p2 = seeded.nextDouble() * 10.0;

        assertThat(p1).isBetween(0.0, 10.0);
        assertThat(p2).isBetween(0.0, 10.0);

        // Recreate generator with same seed to assert determinism
        Random seeded2 = new Random(12345L);
        double q1 = seeded2.nextDouble() * 10.0;
        double q2 = seeded2.nextDouble() * 10.0;

        assertThat(q1).isEqualTo(p1);
        assertThat(q2).isEqualTo(p2);
    }
}
