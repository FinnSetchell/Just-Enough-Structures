package com.finndog.justenoughstructures.mixin;

import com.finndog.justenoughstructures.capture.RealWorldGuard;
import java.util.concurrent.Future;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.thread.BlockableEventLoop;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Work a capturing thread hands the server to do, which would act on the real world, never runs:
 * see {@link RealWorldGuard}. Integrated API's Cobblemon trainer processor summons its trainers this
 * way. A {@link Future}, which is what {@code submit}, {@code executeBlocking} and
 * {@code CompletableFuture}'s async methods hand over, still runs: the code may be waiting for it, and
 * dropping it would leave the capture waiting forever. Every other thread, and every other event loop,
 * is left alone.
 */
@Mixin(BlockableEventLoop.class)
public abstract class BlockableEventLoopGuardMixin {
    @Inject(method = "execute", at = @At("HEAD"), cancellable = true, require = 0)
    private void justenoughstructures$noServerWork(Runnable task, CallbackInfo ci) {
        RealWorldGuard.Sandbox sandbox = RealWorldGuard.current();
        if (sandbox != null && (Object) this instanceof MinecraftServer && !(task instanceof Future<?>)) {
            sandbox.serverWork();
            ci.cancel();
        }
    }
}
