package com.finndog.justenoughstructures.gametest.scripted;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Reading what the harness doesn't otherwise expose. */
final class Reflect {
    private Reflect() {
    }

    /** A field of {@code target}, its own or one it inherits. */
    static Object get(Object target, String name) {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f.get(target);
            } catch (NoSuchFieldException e) {
                // Look further up.
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("No field " + name + " on " + target.getClass());
    }

    static int getInt(Object target, String name) {
        return ((Number) get(target, name)).intValue();
    }

    /** What a method of {@code target}'s own class that takes nothing returns. */
    static Object call(Object target, String name) {
        try {
            Method m = target.getClass().getDeclaredMethod(name);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }
}
