package com.finndog.justenoughstructures.client.render;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.Nbt;
import com.finndog.justenoughstructures.Regs;
import com.finndog.justenoughstructures.capture.SandboxTerrain;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
//? if >=26.1 {
/*import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.level.CardinalLighting;
import net.minecraft.world.level.storage.TagValueInput;
*///?} else {
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockAndTintGetter;
//?}

/**
 * A snapshot laid out so the block renderer can read it by position. Anything at or above
 * {@link #sliceY()} reads as air, which is how the layer slider cuts the structure open.
 *
 * <p>Laying out a big structure takes a while, so it can be made on any thread, but
 * {@link #createRenderables} has to be called on the render thread before it's drawn.
 */
public final class SnapshotView implements BlockAndTintGetter {
    //? if >=26.2 {
    /*// Ids for the entities, below the -1 vanilla gives the mob spinning in a spawner, so they never
    // share one with an entity in the world.
    private static final java.util.concurrent.atomic.AtomicInteger NEXT_ENTITY_ID = new java.util.concurrent.atomic.AtomicInteger(-1);
    *///?}
    private final StructureSnapshot snapshot;
    private final Vec3i size;
    private final BlockState[] palette;
    private final BlockGrid blocks;
    private final Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();
    private final List<Entity> entities = new ArrayList<>();
    private final float[] fitPoints;
    private Holder<Biome> biome;
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
        this.fitPoints = findFitPoints(snapshot, size);
    }

    /**
     * Turns the saved NBT into client-side block entities and entities so their renderers can draw
     * them. They're given the client level so renderers that insist on one work, but they're never
     * added to it.
     */
    public void createRenderables(ClientLevel level) {
        biome = biomeFor(level, snapshot.terrain());
        // Loading other mods' entities makes vanilla warn about things like attributes it doesn't know.
        JesLog.quietly(() -> {
            for (CompoundTag tag : snapshot.blockEntities()) {
                BlockPos pos = new BlockPos(Nbt.getInt(tag, "x"), Nbt.getInt(tag, "y"), Nbt.getInt(tag, "z"));
                try {
                    //? if >=1.21 {
                    /*BlockEntity be = BlockEntity.loadStatic(pos, getBlockState(pos), tag, level.registryAccess());
                    *///?} else {
                    BlockEntity be = BlockEntity.loadStatic(pos, getBlockState(pos), tag);
                    //?}
                    if (be != null) {
                        be.setLevel(level);
                        blockEntities.put(pos, be);
                    }
                } catch (RuntimeException e) {
                    JesLog.debug("Couldn't recreate block entity {} at {}", Nbt.string(tag, "id"), pos, e);
                }
            }
            for (CompoundTag tag : snapshot.entities()) {
                try {
                    //? if >=26.1 {
                    /*EntityType.create(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag), level, EntitySpawnReason.LOAD)
                            .ifPresent(entity -> {
                    *///?} else {
                    EntityType.create(tag, level).ifPresent(entity -> {
                    //?}
                        ListTag pos = Nbt.list(tag, "Pos", Tag.TAG_DOUBLE);
                        entity.setPos(Nbt.getDouble(pos, 0), Nbt.getDouble(pos, 1), Nbt.getDouble(pos, 2));
                        //? if >=26.2 {
                        /*// From 26.2 an entity only gets an id when it joins a world, which these never do,
                        // and telling two apart reads it.
                        entity.setId(NEXT_ENTITY_ID.decrementAndGet());
                        *///?}
                        entities.add(entity);
                    });
                } catch (RuntimeException e) {
                    JesLog.debug("Couldn't recreate entity {}", Nbt.string(tag, "id"), e);
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

    /** Corners of the structure's outline, x y z after each other, to fit the camera around. */
    public float[] fitPoints() {
        return fitPoints;
    }

    /** Directions spread evenly over a sphere, for {@link #findFitPoints}. */
    private static final float[][] FIT_DIRECTIONS = sphere(256);

    private static float[][] sphere(int count) {
        float[][] out = new float[count][];
        double turn = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < count; i++) {
            double y = 1 - (2 * i + 1) / (double) count;
            double r = Math.sqrt(1 - y * y);
            out[i] = new float[]{(float) (Math.cos(turn * i) * r), (float) y, (float) (Math.sin(turn * i) * r)};
        }
        return out;
    }

    /**
     * The block corners furthest out in each of {@link #FIT_DIRECTIONS}. Together they outline the
     * structure from any side, however thin a part of it is, like a mast or a spire, which a sample of
     * its blocks could miss. A block can only be furthest along a direction that tilts up if it's the
     * top of its column, or down if it's the bottom, so only those are looked at.
     */
    private static float[] findFitPoints(StructureSnapshot s, Vec3i size) {
        int count = s.blockCount();
        int sx = size.getX(), sz = size.getZ();
        if (count == 0 || sx <= 0 || sz <= 0) {
            return new float[0];
        }
        int[] top = new int[sx * sz];
        int[] bottom = new int[sx * sz];
        Arrays.fill(top, -1);
        Arrays.fill(bottom, Integer.MAX_VALUE);
        for (int i = 0; i < count; i++) {
            int packed = s.packedPosition(i);
            int column = StructureSnapshot.unpackX(packed) * sz + StructureSnapshot.unpackZ(packed);
            int y = StructureSnapshot.unpackY(packed);
            top[column] = Math.max(top[column], y);
            bottom[column] = Math.min(bottom[column], y);
        }
        int columns = 0;
        for (int t : top) {
            if (t >= 0) {
                columns++;
            }
        }
        int[] xs = new int[columns], zs = new int[columns], tops = new int[columns], bottoms = new int[columns];
        for (int c = 0, n = 0; c < top.length; c++) {
            if (top[c] >= 0) {
                xs[n] = c / sz;
                zs[n] = c % sz;
                tops[n] = top[c];
                bottoms[n++] = bottom[c];
            }
        }
        Set<List<Integer>> corners = new LinkedHashSet<>();
        for (float[] d : FIT_DIRECTIONS) {
            // The corner of a block furthest along d.
            int ox = d[0] > 0 ? 1 : 0, oy = d[1] > 0 ? 1 : 0, oz = d[2] > 0 ? 1 : 0;
            int[] ys = d[1] > 0 ? tops : bottoms;
            float best = Float.NEGATIVE_INFINITY;
            int at = 0;
            for (int n = 0; n < columns; n++) {
                float reach = d[0] * (xs[n] + ox) + d[1] * (ys[n] + oy) + d[2] * (zs[n] + oz);
                if (reach > best) {
                    best = reach;
                    at = n;
                }
            }
            corners.add(List.of(xs[at] + ox, ys[at] + oy, zs[at] + oz));
        }
        float[] points = new float[corners.size() * 3];
        int i = 0;
        for (List<Integer> corner : corners) {
            points[i++] = corner.get(0);
            points[i++] = corner.get(1);
            points[i++] = corner.get(2);
        }
        return points;
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

    //? if >=26.1 {
    /*// How much darker each side of a block is drawn, as in the overworld.
    @Override
    public CardinalLighting cardinalLighting() {
        return CardinalLighting.DEFAULT;
    }
    *///?} else {
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
    //?}

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
        return biome == null ? 0xFFFFFF : resolver.getColor(biome.value(), pos.getX(), pos.getZ());
    }

    @Override
    public int getHeight() {
        return size.getY();
    }

    //? if >=1.21.2 {
    /*@Override
    public int getMinY() {
        return 0;
    }
    *///?} else {
    @Override
    public int getMinBuildHeight() {
        return 0;
    }
    //?}

    private static Holder<Biome> biomeFor(ClientLevel level, SandboxTerrain terrain) {
        Registry<Biome> biomes = level.registryAccess().registryOrThrow(Registries.BIOME);
        ResourceKey<Biome> key = switch (terrain.kind()) {
            case NETHER -> Biomes.NETHER_WASTES;
            case END -> Biomes.THE_END;
            case OCEAN -> Biomes.OCEAN;
            default -> Biomes.PLAINS;
        };
        return Regs.holderOrThrow(biomes, key);
    }
}
