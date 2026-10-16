package com.finndog.justenoughstructures.gametest.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * While the scripted harness plays a test game, the game never takes the mouse. The harness counts
 * the game's window as the one in use, so screens hear its moves, and with no screen open the game
 * would then take the mouse: the PC's real cursor jumps to the middle of the game's window and is held
 * there, under the hand of whoever is using the PC. The harness sends its moves itself and needs none
 * of that.
 */
@Mixin(MouseHandler.class)
abstract class LeaveTheMouseMixin {
    @Inject(method = "grabMouse", at = @At("HEAD"), cancellable = true)
    private void jesTest$leaveTheMouse(CallbackInfo ci) {
        if (System.getProperty("jes.autoshot") != null) {
            ci.cancel();
        }
    }
}
