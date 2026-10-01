package com.finndog.justenoughstructures.mixin;

import com.finndog.justenoughstructures.capture.RealWorldGuard;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.WritableLevelData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A real level, answering a capturing thread from the sandbox: see {@link RealWorldGuard}. The block
 * methods are overrides rather than injections, as the server calls them constantly and this way
 * it only costs a check. Every other thread gets the level itself.
 */
@Mixin(ServerLevel.class)
public abstract class ServerLevelGuardMixin extends Level {
    protected ServerLevelGuardMixin(WritableLevelData data, ResourceKey<Level> dimension, RegistryAccess registries, Holder<DimensionType> type,
                                    Supplier<ProfilerFiller> profiler, boolean client, boolean debug, long seed, int maxChainedNeighborUpdates) {
        super(data, dimension, registries, type, profiler, client, debug, seed, maxChainedNeighborUpdates);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        RealWorldGuard.Sandbox sandbox = RealWorldGuard.current();
        return sandbox != null ? sandbox.blockState(this, pos) : super.getBlockState(pos);
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        RealWorldGuard.Sandbox sandbox = RealWorldGuard.current();
        return sandbox != null ? sandbox.fluidState(this, pos) : super.getFluidState(pos);
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLeft) {
        RealWorldGuard.Sandbox sandbox = RealWorldGuard.current();
        return sandbox != null ? sandbox.setBlock(this, pos, state, flags, recursionLeft) : super.setBlock(pos, state, flags, recursionLeft);
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        RealWorldGuard.Sandbox sandbox = RealWorldGuard.current();
        return sandbox != null ? sandbox.blockEntity(this, pos) : super.getBlockEntity(pos);
    }

    // Code asking for a chunk gets the sandbox's own, and none outside it, so nothing is loaded for
    // it on any thread. Asking for a whole loaded chunk then fails, rather than reaching the world.
    @Override
    public ChunkAccess getChunk(int chunkX, int chunkZ, ChunkStatus status, boolean load) {
        RealWorldGuard.Sandbox sandbox = RealWorldGuard.current();
        if (sandbox == null) {
            return super.getChunk(chunkX, chunkZ, status, load);
        }
        ChunkAccess chunk = sandbox.chunk(this, chunkX, chunkZ);
        if (chunk == null && load) {
            throw new IllegalStateException("A structure preview can't load chunk " + chunkX + ", " + chunkZ + " of the real world");
        }
        return chunk;
    }

    // Lithium answers this straight from the chunk source rather than through the method above, so
    // it's guarded too. There's no whole loaded chunk to give, so it fails.
    @Override
    public LevelChunk getChunk(int chunkX, int chunkZ) {
        RealWorldGuard.Sandbox sandbox = RealWorldGuard.current();
        if (sandbox != null) {
            sandbox.refuseChunk();
            throw new IllegalStateException("A structure preview can't load chunk " + chunkX + ", " + chunkZ + " of the real world");
        }
        return super.getChunk(chunkX, chunkZ);
    }

    @Override
    public int getHeight(Heightmap.Types type, int x, int z) {
        RealWorldGuard.Sandbox sandbox = RealWorldGuard.current();
        return sandbox != null ? sandbox.height(this, type, x, z) : super.getHeight(type, x, z);
    }

    @Override
    public List<Entity> getEntities(Entity except, AABB area, Predicate<? super Entity> filter) {
        RealWorldGuard.Sandbox sandbox = RealWorldGuard.current();
        if (sandbox != null) {
            sandbox.searchEntities();
            return new ArrayList<>();
        }
        return super.getEntities(except, area, filter);
    }

    @Override
    public <T extends Entity> void getEntities(EntityTypeTest<Entity, T> type, AABB area, Predicate<? super T> filter, List<? super T> found, int limit) {
        RealWorldGuard.Sandbox sandbox = RealWorldGuard.current();
        if (sandbox != null) {
            sandbox.searchEntities();
            return;
        }
        super.getEntities(type, area, filter, found, limit);
    }

    // Every way of adding an entity, players aside, ends up here.
    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true, require = 0)
    private void justenoughstructures$addToSandbox(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        RealWorldGuard.Sandbox sandbox = RealWorldGuard.current();
        if (sandbox != null) {
            cir.setReturnValue(sandbox.addEntity(this, entity));
        }
    }

    @Inject(method = "levelEvent", at = @At("HEAD"), cancellable = true, require = 0)
    private void justenoughstructures$noLevelEvents(Player player, int type, BlockPos pos, int data, CallbackInfo ci) {
        justenoughstructures$noEffect(ci);
    }

    @Inject(method = "globalLevelEvent", at = @At("HEAD"), cancellable = true, require = 0)
    private void justenoughstructures$noGlobalEvents(int type, BlockPos pos, int data, CallbackInfo ci) {
        justenoughstructures$noEffect(ci);
    }

    @Inject(method = "gameEvent", at = @At("HEAD"), cancellable = true, require = 0)
    private void justenoughstructures$noGameEvents(GameEvent event, Vec3 at, GameEvent.Context context, CallbackInfo ci) {
        justenoughstructures$noEffect(ci);
    }

    @Inject(method = "blockEvent", at = @At("HEAD"), cancellable = true, require = 0)
    private void justenoughstructures$noBlockEvents(BlockPos pos, Block block, int type, int data, CallbackInfo ci) {
        justenoughstructures$noEffect(ci);
    }

    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;DDDLnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void justenoughstructures$noSounds(Player player, double x, double y, double z, Holder<SoundEvent> sound, SoundSource source,
                                              float volume, float pitch, long seed, CallbackInfo ci) {
        justenoughstructures$noEffect(ci);
    }

    @Inject(method = "playSeededSound(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/core/Holder;Lnet/minecraft/sounds/SoundSource;FFJ)V",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void justenoughstructures$noEntitySounds(Player player, Entity entity, Holder<SoundEvent> sound, SoundSource source,
                                                    float volume, float pitch, long seed, CallbackInfo ci) {
        justenoughstructures$noEffect(ci);
    }

    // Both ways of sending particles end up here.
    @Inject(method = "sendParticles(Lnet/minecraft/server/level/ServerPlayer;ZDDDLnet/minecraft/network/protocol/Packet;)Z",
            at = @At("HEAD"), cancellable = true, require = 0)
    private void justenoughstructures$noParticles(ServerPlayer player, boolean force, double x, double y, double z, Packet<?> packet,
                                                 CallbackInfoReturnable<Boolean> cir) {
        RealWorldGuard.Sandbox sandbox = RealWorldGuard.current();
        if (sandbox != null) {
            sandbox.effect();
            cir.setReturnValue(false);
        }
    }

    private static void justenoughstructures$noEffect(CallbackInfo ci) {
        RealWorldGuard.Sandbox sandbox = RealWorldGuard.current();
        if (sandbox != null) {
            sandbox.effect();
            ci.cancel();
        }
    }
}
