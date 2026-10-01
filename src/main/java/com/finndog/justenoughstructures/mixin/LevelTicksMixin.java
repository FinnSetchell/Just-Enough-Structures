package com.finndog.justenoughstructures.mixin;

import com.finndog.justenoughstructures.capture.RealWorldGuard;
import net.minecraft.world.ticks.LevelTicks;
import net.minecraft.world.ticks.ScheduledTick;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A tick a capturing thread schedules on a real level goes to the sandbox: see {@link RealWorldGuard}. */
@Mixin(LevelTicks.class)
public abstract class LevelTicksMixin {
    @Inject(method = "schedule", at = @At("HEAD"), cancellable = true, require = 0)
    private void justenoughstructures$scheduleInSandbox(ScheduledTick<?> tick, CallbackInfo ci) {
        RealWorldGuard.Sandbox sandbox = RealWorldGuard.current();
        if (sandbox != null) {
            sandbox.schedule((LevelTicks<?>) (Object) this, tick);
            ci.cancel();
        }
    }
}
