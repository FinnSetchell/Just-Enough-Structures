package com.finndog.justenoughstructures.gametest.neoforge;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A game test on NeoForge from 26.1, which has no annotation for them. Its settings and their
 * defaults are Fabric's, so a test runs the same on both. {@link NeoForgeGameTests} registers each.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface GameTest {
    /** The structure the test runs in, with its namespace. */
    String structure();

    /** How many ticks it has to pass. */
    int maxTicks() default 20;

    /** The environment it runs in, with its namespace. The tests of one environment run together. */
    String environment() default "";
}
