package com.finndog.justenoughstructures.capture;

import com.finndog.justenoughstructures.Nbt;
import com.finndog.justenoughstructures.mixin.ListPoolElementAccessor;
import com.finndog.justenoughstructures.mixin.SinglePoolElementAccessor;
import com.finndog.justenoughstructures.mixin.StructureTemplateAccessor;
import com.finndog.justenoughstructures.mixin.TemplateStructurePieceAccessor;
import com.finndog.justenoughstructures.overrides.ContainerPatches;
import com.finndog.justenoughstructures.overrides.SpawnerPatches;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pools.ListPoolElement;
import net.minecraft.world.level.levelgen.structure.pools.SinglePoolElement;
import net.minecraft.world.level.levelgen.structure.pools.StructurePoolElement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * Which template, and which spot in it, each container a structure placed came from, so a dev can
 * point that one container at a different loot table. Spawners are traced the same way, so one can
 * be given a different mob. Only those placed from a template's blocks can be traced; ones
 * structure code places itself have no spot to patch.
 */
final class ContainerSources {
    static final String TAG = "jes:source";
    /** On a spawner's block entity in a snapshot. Not {@link #TAG}, which goes with the loot when that's kept secret. */
    static final String SPAWNER_TAG = "jes:spawner";

    private ContainerSources() {
    }

    /** World position to a tag naming the template and the spot in it, for containers and for spawners. */
    record Found(Map<Long, CompoundTag> containers, Map<Long, CompoundTag> spawners) {
    }

    /**
     * For containers, the tag names the template, the spot, the block, its table there and, if
     * patched, its old table; for spawners, its mob there and, if patched, its old mob.
     * {@code filledBy} is the template that really filled each block entity.
     */
    static Found find(StructureStart start, StructureTemplateManager templates, Map<Long, StructureTemplate> filledBy) {
        Found out = new Found(new HashMap<>(), new HashMap<>());
        for (StructurePiece piece : start.getPieces()) {
            try {
                trace(piece, templates, filledBy, out);
            } catch (RuntimeException e) {
                // A piece that doesn't give up its template just isn't traced; its containers can't be patched.
            }
        }
        return out;
    }

    /**
     * Whether the container placed in the world still has the loot table the template gave it.
     * Processors can set another table as the template places it, and then patching the template
     * wouldn't change what players find, so the container isn't offered. One whose block was swapped
     * as it was placed, like Quark's wooden chests, still takes its table from the template, so it
     * is. Structure code that places its own container afterwards is left out before this, as the
     * template didn't fill it.
     */
    static boolean matches(CompoundTag source, CompoundTag blockEntity) {
        return Nbt.string(blockEntity, "LootTable").equals(Nbt.string(source, "table"));
    }

    /** Whether a spawner is still the block, with the mob, that the template gave it. */
    static boolean spawnerMatches(CompoundTag source, BlockState placed, CompoundTag blockEntity) {
        return BuiltInRegistries.BLOCK.getKey(placed.getBlock()).toString().equals(Nbt.string(source, "block"))
                && SpawnerPatches.mobOf(blockEntity).equals(Nbt.string(source, "mob"));
    }

    private static void trace(StructurePiece piece, StructureTemplateManager templates, Map<Long, StructureTemplate> filledBy,
                              Found out) {
        if (piece instanceof PoolElementStructurePiece pool) {
            StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(pool.getRotation());
            for (SinglePoolElement single : singles(pool.getElement())) {
                Optional<ResourceLocation> named = ((SinglePoolElementAccessor) single).justenoughstructures$template().left();
                Optional<StructureTemplate> template = named.flatMap(templates::get);
                if (template.isPresent()) {
                    trace(named.get(), template.get(), settings, pool.getPosition(), filledBy, out);
                }
            }
        } else if (piece instanceof TemplateStructurePiece templatePiece) {
            TemplateStructurePieceAccessor accessor = (TemplateStructurePieceAccessor) templatePiece;
            trace(accessor.justenoughstructures$templateLocation(), accessor.justenoughstructures$template(),
                    accessor.justenoughstructures$placeSettings(), accessor.justenoughstructures$templatePosition(), filledBy, out);
        }
    }

    /** The templates a pool element places, in order. A list element places each of its own at the same spot. */
    private static List<SinglePoolElement> singles(StructurePoolElement element) {
        if (element instanceof SinglePoolElement single) {
            return List.of(single);
        }
        List<SinglePoolElement> out = new ArrayList<>();
        if (element instanceof ListPoolElement list) {
            for (StructurePoolElement inner : ((ListPoolElementAccessor) list).justenoughstructures$elements()) {
                out.addAll(singles(inner));
            }
        }
        return out;
    }

    private static void trace(ResourceLocation id, StructureTemplate template, StructurePlaceSettings settings, BlockPos origin,
                              Map<Long, StructureTemplate> filledBy, Found out) {
        for (StructureTemplate.Palette palette : ((StructureTemplateAccessor) template).justenoughstructures$palettes()) {
            for (StructureTemplate.StructureBlockInfo info : palette.blocks()) {
                boolean container = info.nbt() != null && Nbt.hasString(info.nbt(), "LootTable");
                if (!container && !SpawnerPatches.isSpawner(info)) {
                    continue;
                }
                BlockPos world = StructureTemplate.calculateRelativePosition(settings, info.pos()).offset(origin);
                if (filledBy.get(world.asLong()) != template) {
                    // Another template, or structure code, filled the block entity there last.
                    continue;
                }
                CompoundTag source = new CompoundTag();
                source.putString("template", id.toString());
                source.putInt("x", info.pos().getX());
                source.putInt("y", info.pos().getY());
                source.putInt("z", info.pos().getZ());
                source.putString("block", BuiltInRegistries.BLOCK.getKey(info.state().getBlock()).toString());
                if (container) {
                    source.putString("table", Nbt.string(info.nbt(), "LootTable"));
                    ContainerPatches.Patch patch = ContainerPatches.find(id, info.pos());
                    if (patch != null) {
                        source.putString("patched_from", patch.original());
                    }
                    out.containers().put(world.asLong(), source);
                } else {
                    source.putString("mob", SpawnerPatches.mobOf(info.nbt()));
                    SpawnerPatches.Patch patch = SpawnerPatches.find(id, info.pos());
                    if (patch != null) {
                        source.putString("patched_from", patch.original());
                    }
                    out.spawners().put(world.asLong(), source);
                }
            }
        }
    }
}
