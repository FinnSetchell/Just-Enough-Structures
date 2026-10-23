package com.finndog.justenoughstructures.capture;

import com.finndog.justenoughstructures.JesLog;
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
import net.minecraft.server.packs.resources.ResourceManager;
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
 * point that one container at a different loot table. Spawners and trial spawners are traced the
 * same way, so one can be given a different mob. Only those placed from a template's blocks can be
 * traced; ones structure code places itself have no spot to patch.
 */
final class ContainerSources {
    static final String TAG = "jes:source";
    /** Tag on a spawner's block entity in a snapshot. Separate from {@link #TAG}, which {@link StructureSnapshot#withoutLoot()} strips. */
    static final String SPAWNER_TAG = "jes:spawner";

    private ContainerSources() {
    }

    /** World position to a tag naming the template and the spot in it, for containers and for spawners. */
    record Found(Map<Long, CompoundTag> containers, Map<Long, CompoundTag> spawners) {
    }

    /**
     * For containers, the tag names the template, the spot, the block, its table there and, if
     * patched, its old table; for spawners, its mob there (a trial spawner's mobs) and, if patched,
     * its old mob and the block it was before any patch made it the other kind. {@code filledBy} is
     * the template that really filled each block entity, and {@code resources} holds the trial spawner
     * configs that are named, not written inline.
     */
    static Found find(StructureStart start, StructureTemplateManager templates, Map<Long, StructureTemplate> filledBy, ResourceManager resources) {
        Found out = new Found(new HashMap<>(), new HashMap<>());
        for (StructurePiece piece : start.getPieces()) {
            try {
                trace(piece, templates, filledBy, resources, out);
            } catch (RuntimeException e) {
                // A piece whose template can't be read isn't traced, so its containers can't be patched.
                JesLog.debug("Couldn't trace the containers of {} back to its template", piece, e);
            }
        }
        return out;
    }

    /**
     * Whether the placed container still has the loot table the template gave it. Processors can set
     * another table as the template places it; patching the template then wouldn't change what players
     * find, so the container isn't offered. One whose block was swapped as it was placed, like Quark's
     * wooden chests, keeps the template's table, so it is still offered. Containers that structure code
     * places afterwards are already left out, as the template didn't fill them.
     */
    static boolean matches(CompoundTag source, CompoundTag blockEntity) {
        return Nbt.string(blockEntity, "LootTable").equals(Nbt.string(source, "table"));
    }

    /**
     * Whether a spawner is still the block, with the mob, that the template gave it. A trial spawner's
     * mobs are already on its tag, added by {@link TrialSpawners#describe}.
     */
    static boolean spawnerMatches(CompoundTag source, BlockState placed, CompoundTag blockEntity) {
        String block = Nbt.hasString(source, "placed") ? Nbt.string(source, "placed") : Nbt.string(source, "block");
        if (!BuiltInRegistries.BLOCK.getKey(placed.getBlock()).toString().equals(block)) {
            return false;
        }
        return TrialSpawners.is(placed) ? Nbt.list(source, "mobs", Tag.TAG_COMPOUND).equals(Nbt.list(blockEntity, TrialSpawners.TAG, Tag.TAG_COMPOUND))
                : SpawnerPatches.mobOf(blockEntity).equals(Nbt.string(source, "mob"));
    }

    private static void trace(StructurePiece piece, StructureTemplateManager templates, Map<Long, StructureTemplate> filledBy,
                              ResourceManager resources, Found out) {
        if (piece instanceof PoolElementStructurePiece pool) {
            StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(pool.getRotation());
            for (SinglePoolElement single : singles(pool.getElement())) {
                Optional<ResourceLocation> named = ((SinglePoolElementAccessor) single).justenoughstructures$template().left();
                Optional<StructureTemplate> template = named.flatMap(templates::get);
                if (template.isPresent()) {
                    trace(named.get(), template.get(), settings, pool.getPosition(), filledBy, resources, out);
                }
            }
        } else if (piece instanceof TemplateStructurePiece templatePiece) {
            TemplateStructurePieceAccessor accessor = (TemplateStructurePieceAccessor) templatePiece;
            trace(accessor.justenoughstructures$templateLocation(), accessor.justenoughstructures$template(),
                    accessor.justenoughstructures$placeSettings(), accessor.justenoughstructures$templatePosition(), filledBy, resources, out);
        }
    }

    /** The templates a pool element places, in order. A list element places all of its elements at the same spot. */
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
                              Map<Long, StructureTemplate> filledBy, ResourceManager resources, Found out) {
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
                    if (TrialSpawners.is(info.state())) {
                        source.put("mobs", TrialSpawners.mobs(info.nbt(), resources));
                    } else {
                        source.putString("mob", SpawnerPatches.mobOf(info.nbt()));
                    }
                    SpawnerPatches.Patch patch = SpawnerPatches.find(id, info.pos());
                    if (patch != null) {
                        source.putString("patched_from", patch.original());
                        if (!patch.block().equals(patch.target())) {
                            // Switched to the other kind of spawner: the patch is stored under the original block.
                            source.putString("placed", Nbt.string(source, "block"));
                            source.putString("block", patch.block().toString());
                        }
                    }
                    out.spawners().put(world.asLong(), source);
                }
            }
        }
    }
}
