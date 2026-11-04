package com.finndog.justenoughstructures.gametest.mixin;

import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.gametest.LateTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Lets {@link LateTick} hold JES's client tick back. */
@Mixin(value = ClientRequests.class, remap = false)
abstract class LateTickMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true, remap = false)
    private static void jesTest$late(CallbackInfo ci) {
        if (LateTick.holds()) {
            ci.cancel();
        }
    }
}
