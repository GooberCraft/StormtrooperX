package com.goobercraft.stormtrooperx;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for StormtrooperX utility methods.
 * Note: Command handling tests would require MockBukkit integration testing.
 */
class StormtrooperXTest {

    @Test
    void testCapitalize_normalWord() throws Exception {
        Method method = StormtrooperX.class.getDeclaredMethod("capitalize", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, "skeleton");
        assertEquals("Skeleton", result);
    }

    @Test
    void testCapitalize_alreadyCapitalized() throws Exception {
        Method method = StormtrooperX.class.getDeclaredMethod("capitalize", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, "Skeleton");
        assertEquals("Skeleton", result);
    }

    @Test
    void testCapitalize_singleChar() throws Exception {
        Method method = StormtrooperX.class.getDeclaredMethod("capitalize", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, "a");
        assertEquals("A", result);
    }

    @Test
    void testCapitalize_emptyString() throws Exception {
        Method method = StormtrooperX.class.getDeclaredMethod("capitalize", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, "");
        assertEquals("", result);
    }

    @Test
    void testCapitalize_nullString() throws Exception {
        Method method = StormtrooperX.class.getDeclaredMethod("capitalize", String.class);
        method.setAccessible(true);

        String result = (String) method.invoke(null, (Object) null);
        assertNull(result);
    }

    @Test
    void testEntityConfig_enabled() throws Exception {
        // Get the inner class
        Class<?> entityConfigClass = null;
        for (Class<?> innerClass : StormtrooperX.class.getDeclaredClasses()) {
            if (innerClass.getSimpleName().equals("EntityConfig")) {
                entityConfigClass = innerClass;
                break;
            }
        }
        assertNotNull(entityConfigClass, "EntityConfig inner class should exist");

        // Create instance using constructor
        Constructor<?> constructor = entityConfigClass.getDeclaredConstructor(boolean.class, double.class);
        constructor.setAccessible(true);
        Object config = constructor.newInstance(true, 0.7);

        // Test isEnabled()
        Method isEnabledMethod = entityConfigClass.getDeclaredMethod("isEnabled");
        isEnabledMethod.setAccessible(true);
        boolean enabled = (boolean) isEnabledMethod.invoke(config);
        assertTrue(enabled);

        // Test getAccuracy()
        Method getAccuracyMethod = entityConfigClass.getDeclaredMethod("getAccuracy");
        getAccuracyMethod.setAccessible(true);
        double accuracy = (double) getAccuracyMethod.invoke(config);
        assertEquals(0.7, accuracy, 0.001);
    }

    @Test
    void testEntityConfig_disabled() throws Exception {
        // Get the inner class
        Class<?> entityConfigClass = null;
        for (Class<?> innerClass : StormtrooperX.class.getDeclaredClasses()) {
            if (innerClass.getSimpleName().equals("EntityConfig")) {
                entityConfigClass = innerClass;
                break;
            }
        }
        assertNotNull(entityConfigClass, "EntityConfig inner class should exist");

        // Create instance using constructor
        Constructor<?> constructor = entityConfigClass.getDeclaredConstructor(boolean.class, double.class);
        constructor.setAccessible(true);
        Object config = constructor.newInstance(false, 0.5);

        // Test isEnabled()
        Method isEnabledMethod = entityConfigClass.getDeclaredMethod("isEnabled");
        isEnabledMethod.setAccessible(true);
        boolean enabled = (boolean) isEnabledMethod.invoke(config);
        assertFalse(enabled);

        // Test getAccuracy()
        Method getAccuracyMethod = entityConfigClass.getDeclaredMethod("getAccuracy");
        getAccuracyMethod.setAccessible(true);
        double accuracy = (double) getAccuracyMethod.invoke(config);
        assertEquals(0.5, accuracy, 0.001);
    }

    @Test
    void testEntityConfig_variousAccuracyValues() throws Exception {
        // Get the inner class
        Class<?> entityConfigClass = null;
        for (Class<?> innerClass : StormtrooperX.class.getDeclaredClasses()) {
            if (innerClass.getSimpleName().equals("EntityConfig")) {
                entityConfigClass = innerClass;
                break;
            }
        }
        assertNotNull(entityConfigClass, "EntityConfig inner class should exist");

        Constructor<?> constructor = entityConfigClass.getDeclaredConstructor(boolean.class, double.class);
        constructor.setAccessible(true);
        Method getAccuracyMethod = entityConfigClass.getDeclaredMethod("getAccuracy");
        getAccuracyMethod.setAccessible(true);

        // Test minimum accuracy (0.0)
        Object config1 = constructor.newInstance(true, 0.0);
        double accuracy1 = (double) getAccuracyMethod.invoke(config1);
        assertEquals(0.0, accuracy1, 0.001);

        // Test maximum accuracy (1.0)
        Object config2 = constructor.newInstance(true, 1.0);
        double accuracy2 = (double) getAccuracyMethod.invoke(config2);
        assertEquals(1.0, accuracy2, 0.001);

        // Test mid-range accuracy (0.5)
        Object config3 = constructor.newInstance(true, 0.5);
        double accuracy3 = (double) getAccuracyMethod.invoke(config3);
        assertEquals(0.5, accuracy3, 0.001);
    }

    @Test
    void testEntityConfig_clampAccuracyAboveMax() throws Exception {
        // Get the inner class
        Class<?> entityConfigClass = null;
        for (Class<?> innerClass : StormtrooperX.class.getDeclaredClasses()) {
            if (innerClass.getSimpleName().equals("EntityConfig")) {
                entityConfigClass = innerClass;
                break;
            }
        }
        assertNotNull(entityConfigClass, "EntityConfig inner class should exist");

        Constructor<?> constructor = entityConfigClass.getDeclaredConstructor(boolean.class, double.class);
        constructor.setAccessible(true);
        Method getAccuracyMethod = entityConfigClass.getDeclaredMethod("getAccuracy");
        getAccuracyMethod.setAccessible(true);

        // Test accuracy > 1.0 should be clamped to 1.0
        Object config1 = constructor.newInstance(true, 1.5);
        double accuracy1 = (double) getAccuracyMethod.invoke(config1);
        assertEquals(1.0, accuracy1, 0.001, "Accuracy 1.5 should be clamped to 1.0");

        Object config2 = constructor.newInstance(true, 999.9);
        double accuracy2 = (double) getAccuracyMethod.invoke(config2);
        assertEquals(1.0, accuracy2, 0.001, "Accuracy 999.9 should be clamped to 1.0");
    }

    @Test
    void testEntityConfig_clampAccuracyBelowMin() throws Exception {
        // Get the inner class
        Class<?> entityConfigClass = null;
        for (Class<?> innerClass : StormtrooperX.class.getDeclaredClasses()) {
            if (innerClass.getSimpleName().equals("EntityConfig")) {
                entityConfigClass = innerClass;
                break;
            }
        }
        assertNotNull(entityConfigClass, "EntityConfig inner class should exist");

        Constructor<?> constructor = entityConfigClass.getDeclaredConstructor(boolean.class, double.class);
        constructor.setAccessible(true);
        Method getAccuracyMethod = entityConfigClass.getDeclaredMethod("getAccuracy");
        getAccuracyMethod.setAccessible(true);

        // Test accuracy < 0.0 should be clamped to 0.0
        Object config1 = constructor.newInstance(true, -0.5);
        double accuracy1 = (double) getAccuracyMethod.invoke(config1);
        assertEquals(0.0, accuracy1, 0.001, "Accuracy -0.5 should be clamped to 0.0");

        Object config2 = constructor.newInstance(true, -999.9);
        double accuracy2 = (double) getAccuracyMethod.invoke(config2);
        assertEquals(0.0, accuracy2, 0.001, "Accuracy -999.9 should be clamped to 0.0");
    }
}
