package com.finndog.justenoughstructures.gametest.mixin;

import com.finndog.justenoughstructures.gametest.RealRandoms;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Past the point where the mod sends capture draws elsewhere, so this only sees the ones that got through. */
@Mixin(LegacyRandomSource.class)
abstract class LegacyRandomSourceMixin {
    @Inject(method = "next", at = @At(value = "INVOKE", target = "Ljava/util/concurrent/atomic/AtomicLong;get()J"))
    private void jesTest$check(int bits, CallbackInfoReturnable<Integer> cir) {
        RealRandoms.check((RandomSource) this);
    }
}
