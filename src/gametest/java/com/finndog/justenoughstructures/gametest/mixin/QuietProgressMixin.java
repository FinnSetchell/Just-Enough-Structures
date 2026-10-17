package com.finndog.justenoughstructures.gametest.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import java.util.concurrent.TimeUnit;
import net.minecraft.gametest.framework.GameTestServer;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The test server logs its progress bar every 20 game ticks, which is once a second at the game's
 * pace. But it never waits for the next tick, only runs what's queued, so while it has nothing to do
 * but wait, most often for the chunks of a batch's test to load, it ticks tens of thousands of times
 * a second. On the NUC that logged the same bar 5,700 times a second. This keeps a bar only when it
 * differs from the last one logged, or when a second has gone by since that one. The ticks are left
 * as they are, and so is the bar logged at the end with every test's result.
 */
@Mixin(GameTestServer.class)
abstract class QuietProgressMixin {
    @Unique
    private String jesTest$lastBar;

    @Unique
    private long jesTest$lastLogged;

    //? if >=26.3 {
    /*// From 26.3 the bar is passed to the log as an argument.
    @WrapWithCondition(method = "tickServer", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;info(Ljava/lang/String;Ljava/lang/Object;)V", ordinal = 0))
    private boolean jesTest$onlyNewProgress(Logger log, String format, Object bar) {
        return jesTest$worthLogging(String.valueOf(bar));
    }
    *///?} else {
    @WrapWithCondition(method = "tickServer", at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;info(Ljava/lang/String;)V", ordinal = 0))
    private boolean jesTest$onlyNewProgress(Logger log, String bar) {
        return jesTest$worthLogging(bar);
    }
    //?}

    @Unique
    private boolean jesTest$worthLogging(String bar) {
        long now = System.nanoTime();
        if (bar.equals(jesTest$lastBar) && now - jesTest$lastLogged < TimeUnit.SECONDS.toNanos(1)) {
            return false;
        }
        jesTest$lastBar = bar;
        jesTest$lastLogged = now;
        return true;
    }
}
