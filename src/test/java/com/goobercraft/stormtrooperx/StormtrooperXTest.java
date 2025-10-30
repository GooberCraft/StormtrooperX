package com.goobercraft.stormtrooperx;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StormtrooperX utility methods.
 * Note: Command handling tests would require MockBukkit integration testing.
 */
class StormtrooperXTest {

    @Test
    void testClamp_withinRange() throws Exception {
        Method method = StormtrooperX.class.getDeclaredMethod("clamp", double.class, double.class, double.class);
        method.setAccessible(true);

        double result = (double) method.invoke(null, 5.0, 0.0, 10.0);
        assertEquals(5.0, result, 0.001);
    }

    @Test
    void testClamp_belowMin() throws Exception {
        Method method = StormtrooperX.class.getDeclaredMethod("clamp", double.class, double.class, double.class);
        method.setAccessible(true);

        double result = (double) method.invoke(null, -5.0, 0.0, 10.0);
        assertEquals(0.0, result, 0.001);
    }

    @Test
    void testClamp_aboveMax() throws Exception {
        Method method = StormtrooperX.class.getDeclaredMethod("clamp", double.class, double.class, double.class);
        method.setAccessible(true);

        double result = (double) method.invoke(null, 15.0, 0.0, 10.0);
        assertEquals(10.0, result, 0.001);
    }

    @Test
    void testClamp_atMin() throws Exception {
        Method method = StormtrooperX.class.getDeclaredMethod("clamp", double.class, double.class, double.class);
        method.setAccessible(true);

        double result = (double) method.invoke(null, 0.0, 0.0, 10.0);
        assertEquals(0.0, result, 0.001);
    }

    @Test
    void testClamp_atMax() throws Exception {
        Method method = StormtrooperX.class.getDeclaredMethod("clamp", double.class, double.class, double.class);
        method.setAccessible(true);

        double result = (double) method.invoke(null, 10.0, 0.0, 10.0);
        assertEquals(10.0, result, 0.001);
    }

    @Test
    void testClamp_negativeRange() throws Exception {
        Method method = StormtrooperX.class.getDeclaredMethod("clamp", double.class, double.class, double.class);
        method.setAccessible(true);

        double result = (double) method.invoke(null, -5.0, -10.0, -1.0);
        assertEquals(-5.0, result, 0.001);
    }

    @Test
    void testClamp_fractionalValues() throws Exception {
        Method method = StormtrooperX.class.getDeclaredMethod("clamp", double.class, double.class, double.class);
        method.setAccessible(true);

        double result = (double) method.invoke(null, 0.5, 0.0, 1.0);
        assertEquals(0.5, result, 0.001);
    }

    @Test
    void testClamp_zeroToOneRange() throws Exception {
        Method method = StormtrooperX.class.getDeclaredMethod("clamp", double.class, double.class, double.class);
        method.setAccessible(true);

        // Test common use case for accuracy values
        double result1 = (double) method.invoke(null, 0.7, 0.0, 1.0);
        assertEquals(0.7, result1, 0.001);

        double result2 = (double) method.invoke(null, 1.5, 0.0, 1.0);
        assertEquals(1.0, result2, 0.001);

        double result3 = (double) method.invoke(null, -0.5, 0.0, 1.0);
        assertEquals(0.0, result3, 0.001);
    }
}
