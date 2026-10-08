package com.finndog.justenoughstructures.mixin;

import com.finndog.justenoughstructures.capture.RealWorldGuard;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import java.util.concurrent.CompletableFuture;
import net.minecraft.server.level.ChunkHolder;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The last line of {@link RealWorldGuard}: a capturing thread never gets a real chunk loaded or
 * generated. Asking the chunk source for a chunk is answered from the sandbox instead: its own chunk
 * in the area being captured, and none anywhere else. Code that then insists on a chunk fails, which
 * fails the preview rather than touching the world. Other threads never see any of this.
 *
 * <p>It answers before the game's own code runs, as Lithium, Canary and C2ME replace or cut short
 * {@code getChunk}. A mixin can only add to a method another has replaced if it comes after it, so
 * this one goes after the usual priority. {@link StructureCapture#checkChunkGuard} checks at start-up
 * that it's in place with the mods installed.
 */
@Mixin(value = ServerChunkCache.class, priority = 1100)
public abstract class ServerChunkCacheGuardMixin {
    @Shadow
    @Final
    ServerLevel level;

    //? if >=1.21 {
    /*@Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/status/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At("HEAD"), cancellable = true, require = 0)
    *///?} else {
    @Inject(method = "getChunk(IILnet/minecraft/world/level/chunk/ChunkStatus;Z)Lnet/minecraft/world/level/chunk/ChunkAccess;",
            at = @At("HEAD"), cancellable = true, require = 0)
    //?}
    private void justenoughstructures$sandboxChunk(int chunkX, int chunkZ, ChunkStatus status, boolean load, CallbackInfoReturnable<ChunkAccess> cir) {
        RealWorldGuard.Sandbox sandbox = RealWorldGuard.current();
        if (sandbox != null) {
            cir.setReturnValue(sandbox.chunk(level, chunkX, chunkZ));
        }
    }

    @Inject(method = "getChunkFuture", at = @At("HEAD"), cancellable = true, require = 0)
    @SuppressWarnings({"rawtypes", "unchecked"})
    private void justenoughstructures$noChunkFutures(CallbackInfoReturnable<CompletableFuture> cir) {
        RealWorldGuard.Sandbox sandbox = RealWorldGuard.current();
        if (sandbox != null) {
            sandbox.refuseChunk();
            cir.setReturnValue(ChunkHolder.UNLOADED_CHUNK_FUTURE);
        }
    }

    /** What's loaded, as far as a capturing thread can tell, is the sandbox's area. */
    @ModifyReturnValue(method = "hasChunk", at = @At("RETURN"), require = 0)
    private boolean justenoughstructures$sandboxHasChunk(boolean loaded, @Local(argsOnly = true, ordinal = 0) int chunkX,
                                                         @Local(argsOnly = true, ordinal = 1) int chunkZ) {
        RealWorldGuard.Sandbox sandbox = RealWorldGuard.current();
        return sandbox != null ? sandbox.hasChunk(level, chunkX, chunkZ) : loaded;
    }
}
