package com.finndog.justenoughstructures.gametest.mixin;

// The 1.21.11 rename would also catch the sound's getIdentifier, so it's off in this file.
//~ !identifier

import com.finndog.justenoughstructures.gametest.UiClicks;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
//? if >=26.1 {
/*import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
*///?} else {
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
//?}

/**
 * Tells {@link UiClicks} about every sound the game is asked to play, before the volume decides
 * whether it's heard: the test game runs with the sound off. From 26.1 playing a sound says how it went.
 */
@Mixin(SoundManager.class)
abstract class UiClicksMixin {
    //? if >=26.1 {
    /*@Inject(method = "play", at = @At("HEAD"))
    private void jesTest$play(SoundInstance sound, CallbackInfoReturnable<SoundEngine.PlayResult> cir) {
        UiClicks.played(sound.getIdentifier().getPath());
    }
    *///?} else {
    @Inject(method = "play", at = @At("HEAD"))
    private void jesTest$play(SoundInstance sound, CallbackInfo ci) {
        UiClicks.played(sound.getLocation().getPath());
    }
    //?}
}
