package com.finndog.justenoughstructures.gametest.mixin;

import com.finndog.justenoughstructures.gametest.RealRandoms;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reports world-random draws during a capture to {@link RealRandoms}. It hooks after JES's HEAD hook, so it only sees draws that got past it. */
@Mixin(LegacyRandomSource.class)
abstract class LegacyRandomSourceMixin {
    @Inject(method = "next", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/atomic/AtomicLong;get()J"))
    private void jesTest$check(int bits, CallbackInfoReturnable<Integer> cir) {
        RealRandoms.check((RandomSource) this);
    }
}
