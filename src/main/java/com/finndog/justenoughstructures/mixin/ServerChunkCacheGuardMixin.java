package com.finndog.justenoughstructures.mixin;

import com.finndog.justenoughstructures.capture.RealWorldGuard;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The last line of {@link RealWorldGuard}: a capturing thread never gets a real chunk loaded or
 * generated. A thread other than the server's asks for chunks by handing the server a task and
 * waiting, so that hand-off is answered from the sandbox instead: its own chunk in the area being
 * captured, and none anywhere else. Code that then insists on a chunk fails, which fails the
 * preview rather than touching the world. The server's own thread never takes this path, so it
 * costs it nothing.
 *
 * <p>Lithium replaces {@code getChunk} with its own, which hands chunks over somewhere else, so that
 * one goes unguarded here, though code asking a level for a chunk is still guarded by
 * {@link ServerLevelGuardMixin}. A mixin can't touch a method another has replaced unless it comes
 * after it, and Mixin stops the game rather than skip it, so this one goes after the usual priority.
 */
@Mixin(value = ServerChunkCache.class, priority = 1100)
public abstract class ServerChunkCacheGuardMixin {
    @Shadow
    @Final
    ServerLevel level;

    @WrapOperation(method = "getChunk(IILnet/minecraft/world/level/chunk/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"),
            require = 0)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private CompletableFuture justenoughstructures$sandboxChunk(Supplier task, Executor executor, Operation<CompletableFuture> original,
                                                               @Local(argsOnly = true, ordinal = 0) int chunkX,
                                                               @Local(argsOnly = true, ordinal = 1) int chunkZ) {
        RealWorldGuard.Sandbox sandbox = RealWorldGuard.current();
        if (sandbox != null) {
            return CompletableFuture.completedFuture(sandbox.chunk(level, chunkX, chunkZ));
        }
        return original.call(task, executor);
    }

    @WrapOperation(method = "getChunkFuture",
            at = @At(value = "INVOKE", target = "Ljava/util/concurrent/CompletableFuture;supplyAsync(Ljava/util/function/Supplier;Ljava/util/concurrent/Executor;)Ljava/util/concurrent/CompletableFuture;"),
            require = 0)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private CompletableFuture justenoughstructures$noChunkFutures(Supplier task, Executor executor, Operation<CompletableFuture> original) {
        RealWorldGuard.Sandbox sandbox = RealWorldGuard.current();
        if (sandbox != null) {
            sandbox.refuseChunk();
            return CompletableFuture.completedFuture(ChunkHolder.UNLOADED_CHUNK_FUTURE);
        }
        return original.call(task, executor);
    }

    /** What's loaded, as far as a capturing thread can tell, is the sandbox's area. */
    @ModifyReturnValue(method = "hasChunk", at = @At("RETURN"), require = 0)
    private boolean justenoughstructures$sandboxHasChunk(boolean loaded, @Local(argsOnly = true, ordinal = 0) int chunkX,
                                                         @Local(argsOnly = true, ordinal = 1) int chunkZ) {
        RealWorldGuard.Sandbox sandbox = RealWorldGuard.current();
        return sandbox != null ? sandbox.hasChunk(level, chunkX, chunkZ) : loaded;
    }
}
