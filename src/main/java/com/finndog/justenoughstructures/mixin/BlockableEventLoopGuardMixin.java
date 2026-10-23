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
 * Drops tasks a capturing thread passes to the server's execute, since they would act on the real
 * world (see {@link RealWorldGuard}). Integrated API's Cobblemon trainer processor summons its
 * trainers this way. A {@link Future} still runs: {@code submit}, {@code executeBlocking} and
 * {@code CompletableFuture}'s async methods pass one, and the code may be waiting for it, so dropping
 * it would leave the capture waiting forever. Other threads and other event loops are unaffected.
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
