package com.finndog.justenoughstructures.gametest.scripted;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

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

    /** Gives a field of {@code target}, its own or one it inherits, another value. */
    static void set(Object target, String name, Object value) {
        for (Class<?> c = target.getClass(); c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                f.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                // Look further up.
            } catch (IllegalAccessException e) {
                throw new IllegalStateException(e);
            }
        }
        throw new IllegalStateException("No field " + name + " on " + target.getClass());
    }

    /**
     * The one field {@code owner} declares of {@code type}, whatever it's called. For the game's own
     * classes, whose fields a real game knows by other names than these sources on Fabric and on Forge
     * 1.20.1. Throws if there's none, or more than one.
     */
    static Field only(Class<?> owner, Class<?> type) {
        Field found = null;
        for (Field f : owner.getDeclaredFields()) {
            if (!Modifier.isStatic(f.getModifiers()) && type.isAssignableFrom(f.getType())) {
                if (found != null) {
                    throw new IllegalStateException(owner + " has more than one " + type.getSimpleName() + ": " + found.getName() + " and " + f.getName());
                }
                found = f;
            }
        }
        if (found == null) {
            throw new IllegalStateException("No " + type.getSimpleName() + " on " + owner);
        }
        found.setAccessible(true);
        return found;
    }

    static int getInt(Object target, String name) {
        return ((Number) get(target, name)).intValue();
    }

    /** Calls a no-argument method declared on {@code target}'s class and returns the result. */
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
