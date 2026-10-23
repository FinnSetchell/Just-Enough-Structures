package com.finndog.justenoughstructures.gametest.mixin;

import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import java.util.concurrent.TimeUnit;
import net.minecraft.gametest.framework.GameTestServer;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The test server logs its progress bar every 20 ticks, once a second at the game's pace. It never
 * sleeps between ticks, so while it has nothing to do but wait, usually for the chunks of a batch's
 * test to load, it ticks as fast as it can and logs the same bar over and over. This logs a bar only
 * when it changed or a second has passed since the last one. The ticks are left alone, and so is the
 * bar logged at the end with every test's result.
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
