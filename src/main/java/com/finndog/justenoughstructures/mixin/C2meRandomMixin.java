package com.finndog.justenoughstructures.mixin;

import com.finndog.justenoughstructures.capture.LevelRandom;
import com.finndog.justenoughstructures.capture.StructureCapture;
import net.minecraft.util.RandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * C2ME gives each world a random of its own, which refuses to be drawn from on any thread but the
 * server's, and its draws never reach {@link LegacyRandomSourceMixin}. So bees, and anything else
 * that draws from the world's random as it's made, went missing from previews. The same redirect
 * goes on its draws too: while a capture places a structure, they come from the capture's own random.
 */
@Pseudo
@Mixin(targets = "com.ishland.c2me.fixes.worldgen.threading_issues.common.CheckedThreadLocalRandom")
public abstract class C2meRandomMixin {
    // It's next in a development game and from 26.1, and goes by its intermediary name before that.
    @Inject(method = {"next(I)I", "method_43156(I)I"}, at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void justenoughstructures$drawFromTheCapture(int bits, CallbackInfoReturnable<Integer> cir) {
        if (((LevelRandom) (Object) this).justenoughstructures$isLevelRandom()) {
            RandomSource sandbox = StructureCapture.sandboxRandom();
            if (sandbox != null) {
                cir.setReturnValue(sandbox.nextInt() >>> (32 - bits));
            }
        }
    }
}
