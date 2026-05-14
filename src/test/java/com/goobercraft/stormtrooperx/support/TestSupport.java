package com.goobercraft.stormtrooperx.support;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Reflection helpers used by tests that need to reach genuinely-private
 * production members. Centralized here so the unsafe ceremony lives in one
 * place rather than being repeated across every test class.
 *
 * <p>Prefer widening a member to package-private over calling these helpers.
 * They exist for state and behavior that should stay private in production
 * but still needs verification &mdash; for example, the migration helpers on
 * {@code StormtrooperX}.</p>
 */
public final class TestSupport {

    private TestSupport() {
    }

    /**
     * Set a private field on {@code target} (or {@code null} target for a
     * static field on {@code targetClass}) to {@code value}. Walks the class
     * hierarchy so inherited fields are reachable too.
     */
    public static void inject(Object target, String fieldName, Object value) {
        Class<?> cls = (target instanceof Class<?>) ? (Class<?>) target : target.getClass();
        Object receiver = (target instanceof Class<?>) ? null : target;
        while (cls != null) {
            try {
                final Field field = cls.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(receiver, value);
                return;
            } catch (NoSuchFieldException ignored) {
                cls = cls.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new AssertionError("Failed to inject '" + fieldName + "'", e);
            }
        }
        throw new AssertionError("Field '" + fieldName + "' not found on "
            + (target instanceof Class<?> ? target : target.getClass()));
    }

    /**
     * Invoke a private method on {@code target} (static if {@code target} is a
     * {@link Class}). Argument types are inferred from the runtime classes of
     * {@code args}; pass {@code null} for an argument's class to skip a single
     * parameter's auto-detection (rare).
     */
    @SuppressWarnings("unchecked")
    public static <T> T invokePrivate(Object target, String methodName, Object... args) {
        final Class<?> cls = (target instanceof Class<?>) ? (Class<?>) target : target.getClass();
        final Object receiver = (target instanceof Class<?>) ? null : target;
        try {
            // Pick the first matching method by name + arity; tests don't need overload resolution here.
            for (Method method : cls.getDeclaredMethods()) {
                if (method.getName().equals(methodName) && method.getParameterCount() == args.length) {
                    method.setAccessible(true);
                    return (T) method.invoke(receiver, args);
                }
            }
            throw new AssertionError("Method '" + methodName + "' with " + args.length
                + " params not found on " + cls.getName());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("Failed to invoke '" + methodName + "'", e);
        }
    }

}
