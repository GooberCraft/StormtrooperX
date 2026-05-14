package com.goobercraft.stormtrooperx;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.function.Supplier;

import org.bukkit.util.Vector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Tests for {@link ProjectileNerf#perturb} — the speed-preserving direction
 * perturbation extracted from {@code StormtrooperX.onBowShoot}.
 *
 * <p>The random source is injected so every assertion is deterministic; no
 * Bukkit server or event mocking is required.</p>
 */
@DisplayName("ProjectileNerf — direction perturbation while preserving speed")
class ProjectileNerfTest {

    /** Tolerance for floating-point length comparisons. */
    private static final double EPSILON = 1e-10;

    /** Deterministic non-zero random source — fixed vector keeps tests reproducible. */
    private static final Supplier<Vector> FIXED_RANDOM = () -> new Vector(0.5, 0.5, 0.5);

    @Nested
    @DisplayName("speed preservation")
    class SpeedPreservation {

        @ParameterizedTest(name = "velocity ({0}, {1}, {2}) keeps its length after perturb")
        @CsvSource({
            "1.0, 0.0, 0.0",
            "0.0, 1.0, 0.0",
            "0.0, 0.0, 1.0",
            "1.0, 0.5, 0.5",
            "3.0, 4.0, 0.0",
            "0.1, 0.1, 0.1",
            "10.0, 10.0, 10.0",
        })
        void preservesOriginalSpeed(double x, double y, double z) {
            final Vector velocity = new Vector(x, y, z);
            final double originalSpeed = velocity.length();

            ProjectileNerf.perturb(velocity, 0.7, FIXED_RANDOM);

            assertThat(velocity.length()).isCloseTo(originalSpeed, within(EPSILON));
        }

        @ParameterizedTest(name = "accuracy={0} still preserves speed")
        @ValueSource(doubles = {0.0, 0.1, 0.25, 0.5, 0.7, 1.0})
        void preservesSpeedAcrossAccuracyRange(double accuracy) {
            final Vector velocity = new Vector(1.0, 0.5, 0.5);
            final double originalSpeed = velocity.length();

            ProjectileNerf.perturb(velocity, accuracy, FIXED_RANDOM);

            assertThat(velocity.length()).isCloseTo(originalSpeed, within(EPSILON));
        }
    }

    @Nested
    @DisplayName("zero-velocity guard")
    class ZeroVelocityGuard {

        @Test
        @DisplayName("zero-length input is returned unchanged")
        void zeroLengthIsUnchanged() {
            final Vector velocity = new Vector(0, 0, 0);

            final Vector result = ProjectileNerf.perturb(velocity, 0.7, FIXED_RANDOM);

            assertThat(result).isSameAs(velocity);
            assertThat(result.getX()).isZero();
            assertThat(result.getY()).isZero();
            assertThat(result.getZ()).isZero();
        }

        @Test
        @DisplayName("zero-length input does not call the random source")
        void zeroLengthDoesNotCallRandomSource() {
            final Vector velocity = new Vector(0, 0, 0);
            final int[] callCount = {0};
            final Supplier<Vector> counting = () -> {
                callCount[0]++;
                return new Vector(1, 1, 1);
            };

            ProjectileNerf.perturb(velocity, 0.7, counting);

            assertThat(callCount[0]).as("random source must be skipped on zero-length input").isZero();
        }
    }

    @Nested
    @DisplayName("accuracy=0 short-circuit")
    class ZeroAccuracy {

        @Test
        @DisplayName("zero accuracy yields a vector still pointing along the input direction")
        void zeroAccuracyPreservesDirection() {
            final Vector velocity = new Vector(1.0, 0.0, 0.0);

            ProjectileNerf.perturb(velocity, 0.0, FIXED_RANDOM);

            assertThat(velocity.getX()).isCloseTo(1.0, within(EPSILON));
            assertThat(velocity.getY()).isCloseTo(0.0, within(EPSILON));
            assertThat(velocity.getZ()).isCloseTo(0.0, within(EPSILON));
        }
    }

    @Nested
    @DisplayName("mutation semantics")
    class MutationSemantics {

        @Test
        @DisplayName("input vector is mutated in place and the same reference is returned")
        void returnsSameInstance() {
            final Vector velocity = new Vector(1.0, 1.0, 1.0);

            final Vector result = ProjectileNerf.perturb(velocity, 0.5, FIXED_RANDOM);

            assertThat(result).isSameAs(velocity);
        }

        @Test
        @DisplayName("calling perturb a second time still preserves speed")
        void repeatedPerturbStillPreservesSpeed() {
            final Vector velocity = new Vector(2.0, 0.0, 0.0);
            final double originalSpeed = velocity.length();

            ProjectileNerf.perturb(velocity, 0.5, FIXED_RANDOM);
            ProjectileNerf.perturb(velocity, 0.5, FIXED_RANDOM);

            assertThat(velocity.length()).isCloseTo(originalSpeed, within(EPSILON));
        }
    }
}
