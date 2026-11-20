package com.finndog.justenoughstructures.gametest.mixin;

import com.finndog.justenoughstructures.gametest.FlyInputs;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * During an autoshot run, keeps the browser's fly camera off the real cursor and hands it the
 * scenario's mouse and keys instead (see {@link FlyInputs}). The camera hides and holds the cursor
 * itself, which would hold the PC's own cursor in the test game's window, and reads the mouse and
 * keys straight from the window, which the harness's input doesn't reach.
 */
@Mixin(targets = "com.finndog.justenoughstructures.client.screen.FlyInput", remap = false)
abstract class FlyInputMixin {
    @Inject(method = "cursor", at = @At("HEAD"), cancellable = true, remap = false)
    private static void jesTest$cursor(boolean grab, boolean warp, double x, double y, CallbackInfo ci) {
        if (System.getProperty("jes.autoshot") != null) {
            FlyInputs.cursor(grab, warp);
            ci.cancel();
        }
    }

    @Inject(method = "moved", at = @At("HEAD"), cancellable = true, remap = false)
    private static void jesTest$moved(CallbackInfoReturnable<double[]> cir) {
        if (System.getProperty("jes.autoshot") != null) {
            cir.setReturnValue(FlyInputs.moved());
        }
    }

    @Inject(method = "down", at = @At("HEAD"), cancellable = true, remap = false)
    private static void jesTest$down(KeyMapping mapping, CallbackInfoReturnable<Boolean> cir) {
        if (System.getProperty("jes.autoshot") != null) {
            cir.setReturnValue(FlyInputs.held(mapping));
        }
    }
}
