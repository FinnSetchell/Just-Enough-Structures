package com.finndog.justenoughstructures.client.render;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.capture.SandboxTerrain;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;

/**
 * A snapshot laid out so the block renderer can read it by position. Anything at or above
 * {@link #sliceY()} reads as air, which is how the layer slider cuts the structure open.
 */
public final class SnapshotView implements BlockAndTintGetter {
    private final StructureSnapshot snapshot;
    private final Vec3i size;
    private final BlockState[] palette;
    private final BlockGrid blocks;
    private final Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();
    private final List<Entity> entities = new ArrayList<>();
    private final Holder<Biome> biome;
    private int sliceY;

    public SnapshotView(StructureSnapshot snapshot) {
        this.snapshot = snapshot;
        this.size = snapshot.size();
        this.sliceY = size.getY();
        List<BlockState> states = snapshot.palette();
        this.palette = new BlockState[states.size() + 1];
        palette[0] = Blocks.AIR.defaultBlockState();
        for (int i = 0; i < states.size(); i++) {
            palette[i + 1] = states.get(i);
        }
        this.blocks = new BlockGrid(snapshot);
        JesLog.debug("Preview of {}: {} blocks in a {}x{}x{} box take {} KB (a grid of the whole box would take {} KB)", snapshot.structureId(),
                snapshot.blockCount(), size.getX(), size.getY(), size.getZ(), blocks.bytes() / 1024, (long) size.getX() * size.getY() * size.getZ() * 2 / 1024);
        this.biome = biomeFor(snapshot.terrain());
    }

    /**
     * Turns the saved NBT into client-side block entities and entities so their renderers can draw
     * them. They're given the client level so renderers that insist on one work, but they're never
     * added to it.
     */
    public void createRenderables(ClientLevel level) {
        // Loading other mods' entities makes vanilla warn about things like attributes it doesn't know.
        JesLog.quietly(() -> {
            for (CompoundTag tag : snapshot.blockEntities()) {
                BlockPos pos = new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z"));
                try {
                    BlockEntity be = BlockEntity.loadStatic(pos, getBlockState(pos), tag);
                    if (be != null) {
                        be.setLevel(level);
                        blockEntities.put(pos, be);
                    }
                } catch (RuntimeException e) {
                    JesLog.debug("Couldn't recreate block entity {} at {}", tag.getString("id"), pos, e);
                }
            }
            for (CompoundTag tag : snapshot.entities()) {
                try {
                    EntityType.create(tag, level).ifPresent(entity -> {
                        ListTag pos = tag.getList("Pos", Tag.TAG_DOUBLE);
                        entity.setPos(pos.getDouble(0), pos.getDouble(1), pos.getDouble(2));
                        entities.add(entity);
                    });
                } catch (RuntimeException e) {
                    JesLog.debug("Couldn't recreate entity {}", tag.getString("id"), e);
                }
            }
        });
    }

    public StructureSnapshot snapshot() {
        return snapshot;
    }

    public Vec3i size() {
        return size;
    }

    public int sliceY() {
        return sliceY;
    }

    public void setSliceY(int sliceY) {
        this.sliceY = Math.max(1, Math.min(size.getY(), sliceY));
    }

    public Map<BlockPos, BlockEntity> blockEntities() {
        return blockEntities;
    }

    public List<Entity> entities() {
        return entities;
    }

    public boolean contains(int x, int y, int z) {
        return x >= 0 && y >= 0 && z >= 0 && x < size.getX() && y < size.getY() && z < size.getZ();
    }

    /** The block regardless of the slice. */
    public BlockState rawState(int x, int y, int z) {
        return contains(x, y, z) ? palette[blocks.get(x, y, z)] : Blocks.AIR.defaultBlockState();
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        if (pos.getY() >= sliceY) {
            return Blocks.AIR.defaultBlockState();
        }
        return rawState(pos.getX(), pos.getY(), pos.getZ());
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return getBlockState(pos).getFluidState();
    }

    @Override
    public BlockEntity getBlockEntity(BlockPos pos) {
        return pos.getY() >= sliceY ? null : blockEntities.get(pos);
    }

    @Override
    public float getShade(Direction direction, boolean shade) {
        if (!shade) {
            return 1.0f;
        }
        return switch (direction) {
            case DOWN -> 0.5f;
            case UP -> 1.0f;
            case NORTH, SOUTH -> 0.8f;
            case WEST, EAST -> 0.6f;
        };
    }

    @Override
    public LevelLightEngine getLightEngine() {
        // Unused: brightness is answered directly below.
        return null;
    }

    @Override
    public int getBrightness(LightLayer layer, BlockPos pos) {
        return 15;
    }

    @Override
    public int getRawBrightness(BlockPos pos, int darkening) {
        return 15;
    }

    @Override
    public int getBlockTint(BlockPos pos, ColorResolver resolver) {
        return resolver.getColor(biome.value(), pos.getX(), pos.getZ());
    }

    @Override
    public int getHeight() {
        return size.getY();
    }

    @Override
    public int getMinBuildHeight() {
        return 0;
    }

    private static Holder<Biome> biomeFor(SandboxTerrain terrain) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            throw new IllegalStateException("No client level to borrow biomes from");
        }
        Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        ResourceKey<Biome> key = switch (terrain) {
            case NETHER -> Biomes.NETHER_WASTES;
            case END -> Biomes.THE_END;
            case OCEAN -> Biomes.OCEAN;
            case LAND, VOID -> Biomes.PLAINS;
        };
        return biomes.getHolderOrThrow(key);
    }
}
