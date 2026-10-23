package com.finndog.justenoughstructures.gametest.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Stops the game grabbing the mouse during an autoshot run. The harness marks the game window as
 * focused so screens get mouse moves, and with no screen open the game would then lock the real
 * cursor to the middle of its window. The harness sends its moves itself and needs no grab.
 */
@Mixin(MouseHandler.class)
abstract class LeaveTheMouseMixin {
    // A real Forge 1.20.1 game knows the method by its SRG name, and the test jar has no refmap to say so.
    //? if forge && <1.21 {
    /*@Inject(method = {"grabMouse", "m_91601_"}, at = @At("HEAD"), cancellable = true)
    *///?} else {
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    //?}
    private void jesTest$leaveTheMouse(CallbackInfo ci) {
        if (System.getProperty("jes.autoshot") != null) {
            ci.cancel();
        }
    }
}
