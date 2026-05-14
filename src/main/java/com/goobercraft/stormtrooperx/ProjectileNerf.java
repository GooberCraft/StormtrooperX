package com.goobercraft.stormtrooperx;

import java.util.function.Supplier;

import org.bukkit.util.Vector;

/**
 * Pure-function helper for the projectile-accuracy nerf.
 *
 * <p>Perturbs a projectile direction while preserving its original speed. The
 * random source is injected so the math is deterministically testable without
 * a live Bukkit server; production callers pass {@code Vector::getRandom}.</p>
 */
final class ProjectileNerf {

    private ProjectileNerf() {
    }

    /**
     * Apply an accuracy-scaled random deviation to {@code velocity}, then
     * renormalize so the resulting vector has the same length as the input.
     *
     * <p>The input vector is mutated in place to match Bukkit {@link Vector}
     * semantics, and the same instance is returned.</p>
     *
     * @param velocity     the projectile velocity (mutated in place)
     * @param accuracy     deviation factor; callers should clamp to [0.0, 1.0]
     * @param randomSource supplies a fresh random unit-cube vector per call
     * @return {@code velocity}, mutated
     */
    static Vector perturb(Vector velocity, double accuracy, Supplier<Vector> randomSource) {
        final double originalSpeed = velocity.length();
        if (originalSpeed == 0) {
            return velocity;
        }
        final Vector deviation = randomSource.get().multiply(accuracy);
        velocity.add(deviation).normalize().multiply(originalSpeed);
        return velocity;
    }
}
