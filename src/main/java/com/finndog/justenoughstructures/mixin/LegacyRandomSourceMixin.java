package com.finndog.justenoughstructures.mixin;

import com.finndog.justenoughstructures.capture.LevelRandom;
import com.finndog.justenoughstructures.capture.StructureCapture;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * While a capture places a structure, anything drawn from a real world's random comes from the
 * capture's own instead. Bees do it as they're created, for one. The server draws from the same
 * random on its own thread, and two threads drawing at once crashes the game.
 */
@Mixin(LegacyRandomSource.class)
public abstract class LegacyRandomSourceMixin implements LevelRandom {
    @Unique
    private boolean justenoughstructures$levelRandom;

    @Override
    public void justenoughstructures$markLevelRandom() {
        justenoughstructures$levelRandom = true;
    }

    @Override
    public boolean justenoughstructures$isLevelRandom() {
        return justenoughstructures$levelRandom;
    }

    @Inject(method = "next", at = @At("HEAD"), cancellable = true)
    private void justenoughstructures$drawFromTheCapture(int bits, CallbackInfoReturnable<Integer> cir) {
        if (justenoughstructures$levelRandom) {
            RandomSource sandbox = StructureCapture.sandboxRandom();
            if (sandbox != null) {
                cir.setReturnValue(sandbox.nextInt() >>> (32 - bits));
            }
        }
    }
}
