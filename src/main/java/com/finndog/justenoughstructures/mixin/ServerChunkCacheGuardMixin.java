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
 * generated. Its requests to the chunk source are answered from the sandbox instead: the sandbox's
 * chunk inside the capture area, and none anywhere else. Code that then insists on a chunk fails,
 * which fails the preview instead of touching the world. Other threads are unaffected.
 *
 * <p>This answers before anything else in {@code getChunk} runs. Lithium and Canary overwrite the
 * method, and overwrites are applied before any mod's injections at its start, so this lands in
 * their version. C2ME also injects at the start and answers other threads itself. Injections there
 * run in mixin priority order, and a tie goes to the mod that loads first. C2ME's mixins are at 1100,
 * from its config, and a real Fabric instance loads mods by id, so at an equal 1100 C2ME's would run
 * first. At 900, below the default priority, this one runs first.
 * {@link com.finndog.justenoughstructures.capture.StructureCapture#checkChunkGuard} checks, once the
 * server is up, that it's in place with the installed mods.
 */
@Mixin(value = ServerChunkCache.class, priority = 900)
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

    /** To a capturing thread, only the sandbox's area is loaded. */
    @ModifyReturnValue(method = "hasChunk", at = @At("RETURN"), require = 0)
    private boolean justenoughstructures$sandboxHasChunk(boolean loaded, @Local(argsOnly = true, ordinal = 0) int chunkX,
                                                         @Local(argsOnly = true, ordinal = 1) int chunkZ) {
        RealWorldGuard.Sandbox sandbox = RealWorldGuard.current();
        return sandbox != null ? sandbox.hasChunk(level, chunkX, chunkZ) : loaded;
    }
}
