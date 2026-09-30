package com.finndog.justenoughstructures.capture;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * A real {@link WorldGenRegion} over chunks that belong to no world, so structure code sees exactly
 * what it sees during normal generation. It remembers every position written to.
 */
final class CaptureRegion extends WorldGenRegion {
    private final LongSet written = new LongOpenHashSet();
    private final Map<Long, StructureTemplate> filledBy = new HashMap<>();
    private ChunkPos placing;

    CaptureRegion(ServerLevel level, List<ChunkAccess> chunks, int writeRadius) {
        super(level, chunks, ChunkStatus.FEATURES, writeRadius);
    }

    LongSet written() {
        return written;
    }

    /** Which template last filled the block entity at each position, and nothing for ones placed by structure code. */
    Map<Long, StructureTemplate> filledBy() {
        return filledBy;
    }

    // A template fills a block entity by fetching it right after setting the block, so the template
    // placing at that moment is the one whose loot table it ends up with.
    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        BlockEntity blockEntity = super.getBlockEntity(pos);
        StructureTemplate template = TemplatePlacements.current();
        if (blockEntity != null && template != null) {
            filledBy.put(pos.asLong(), template);
        }
        return blockEntity;
    }

    /**
     * In a real world every chunk is decorated by a region centred on it, and some structure code
     * relies on that: processors that add pillars, carve air or flood with water skip any block
     * outside {@link #getCenter()}'s chunk. So while a chunk is being placed, it's the centre.
     */
    void placing(ChunkPos chunk) {
        placing = chunk;
    }

    @Override
    public ChunkPos getCenter() {
        return placing != null ? placing : super.getCenter();
    }

    // Same as the vanilla method minus the call to ServerLevel.onBlockStateChange, which would
    // register beds, bells and workstations as points of interest in the real world.
    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLeft) {
        if (!ensureCanWrite(pos)) {
            return false;
        }
        ChunkAccess chunk = getChunk(pos);
        BlockState old = chunk.setBlockState(pos, state, false);
        written.add(pos.asLong());
        if (TemplatePlacements.current() == null) {
            filledBy.remove(pos.asLong());
        }

        if (state.hasBlockEntity()) {
            CompoundTag placeholder = new CompoundTag();
            placeholder.putInt("x", pos.getX());
            placeholder.putInt("y", pos.getY());
            placeholder.putInt("z", pos.getZ());
            placeholder.putString("id", "DUMMY");
            chunk.setBlockEntityNbt(placeholder);
        } else if (old != null && old.hasBlockEntity()) {
            chunk.removeBlockEntity(pos);
        }

        if (state.hasPostProcess(this, pos)) {
            chunk.markPosForPostprocessing(pos);
        }
        return true;
    }
}
