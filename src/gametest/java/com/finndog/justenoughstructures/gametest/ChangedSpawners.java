package com.finndog.justenoughstructures.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
//? if >=26.2 {
/*import com.mojang.serialization.MapCodec;
*///?} else {
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
//?}

/**
 * A processor that changes every spawner it's asked about, as a mod's own does to pick a spawner's
 * mob as a piece is placed, for the test that this mod sees such changes. What it changes is nothing
 * the spawner reads, so the spawner still has its template's mob and only the change itself can
 * tell it from one left alone. It's slipped in beside a piece's own processors on the thread that
 * asks for it.
 */
//? if >=26.2 {
/*public final class ChangedSpawners implements StructureProcessor {
*///?} else {
public final class ChangedSpawners extends StructureProcessor {
//?}
    public static final ChangedSpawners PROCESSOR = new ChangedSpawners();
    private static final ThreadLocal<Boolean> JOINED = ThreadLocal.withInitial(() -> false);
    private static final AtomicInteger CHANGED = new AtomicInteger();

    private ChangedSpawners() {
    }

    /** Whether this thread's pieces get the processor too. */
    public static void join(boolean joined) {
        JOINED.set(joined);
    }

    /** How many spawners it has changed so far. */
    public static int changed() {
        return CHANGED.get();
    }

    /** A piece's own processors, and this one after them where it's been asked for. */
    public static List<StructureProcessor> with(List<StructureProcessor> own) {
        if (!JOINED.get()) {
            return own;
        }
        List<StructureProcessor> all = new ArrayList<>(own);
        all.add(PROCESSOR);
        return all;
    }

    //? if >=26.2 {
    /*@Override
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos offset, BlockPos pos, BlockPos templatePos,
                                                             StructureTemplate.StructureBlockInfo current, StructurePlaceSettings settings) {
        return change(current);
    }

    @Override
    public MapCodec<? extends StructureProcessor> codec() {
        return MapCodec.unit(PROCESSOR);
    }
    *///?} else {
    @Override
    public StructureTemplate.StructureBlockInfo processBlock(LevelReader level, BlockPos offset, BlockPos pos, StructureTemplate.StructureBlockInfo original,
                                                             StructureTemplate.StructureBlockInfo current, StructurePlaceSettings settings) {
        return change(current);
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return StructureProcessorType.NOP;
    }
    //?}

    private static StructureTemplate.StructureBlockInfo change(StructureTemplate.StructureBlockInfo block) {
        if (block.nbt() == null || !(block.state().getBlock() instanceof SpawnerBlock)) {
            return block;
        }
        CompoundTag data = block.nbt().copy();
        data.putBoolean("jes_test_changed", true);
        CHANGED.incrementAndGet();
        return new StructureTemplate.StructureBlockInfo(block.pos(), block.state(), data);
    }
}
