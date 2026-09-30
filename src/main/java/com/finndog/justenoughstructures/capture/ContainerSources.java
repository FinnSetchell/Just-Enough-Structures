package com.finndog.justenoughstructures.capture;

import com.finndog.justenoughstructures.mixin.ListPoolElementAccessor;
import com.finndog.justenoughstructures.mixin.SinglePoolElementAccessor;
import com.finndog.justenoughstructures.mixin.StructureTemplateAccessor;
import com.finndog.justenoughstructures.mixin.TemplateStructurePieceAccessor;
import com.finndog.justenoughstructures.overrides.ContainerPatches;
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
 * point that one container at a different loot table. Only containers placed from a template's
 * blocks can be traced; ones structure code places itself have no spot to patch.
 */
final class ContainerSources {
    static final String TAG = "jes:source";

    private ContainerSources() {
    }

    /**
     * World position to a tag naming the template, the spot, the block, its table there and, if
     * patched, its old table. {@code filledBy} is the template that really filled each container.
     */
    static Map<Long, CompoundTag> find(StructureStart start, StructureTemplateManager templates, Map<Long, StructureTemplate> filledBy) {
        Map<Long, CompoundTag> out = new HashMap<>();
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
     * Whether the container placed in the world is still the one from the template. Processors and
     * structure code can swap the block or set another table after the template places it, and then
     * patching the template wouldn't change what players find, so the container isn't offered.
     */
    static boolean matches(CompoundTag source, BlockState placed, CompoundTag blockEntity) {
        return BuiltInRegistries.BLOCK.getKey(placed.getBlock()).toString().equals(source.getString("block"))
                && blockEntity.getString("LootTable").equals(source.getString("table"));
    }

    private static void trace(StructurePiece piece, StructureTemplateManager templates, Map<Long, StructureTemplate> filledBy,
                              Map<Long, CompoundTag> out) {
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
                              Map<Long, StructureTemplate> filledBy, Map<Long, CompoundTag> out) {
        for (StructureTemplate.Palette palette : ((StructureTemplateAccessor) template).justenoughstructures$palettes()) {
            for (StructureTemplate.StructureBlockInfo info : palette.blocks()) {
                if (info.nbt() == null || !info.nbt().contains("LootTable", Tag.TAG_STRING)) {
                    continue;
                }
                BlockPos world = StructureTemplate.calculateRelativePosition(settings, info.pos()).offset(origin);
                if (filledBy.get(world.asLong()) != template) {
                    // Another template, or structure code, filled the container there last.
                    continue;
                }
                CompoundTag source = new CompoundTag();
                source.putString("template", id.toString());
                source.putInt("x", info.pos().getX());
                source.putInt("y", info.pos().getY());
                source.putInt("z", info.pos().getZ());
                source.putString("block", BuiltInRegistries.BLOCK.getKey(info.state().getBlock()).toString());
                source.putString("table", info.nbt().getString("LootTable"));
                ContainerPatches.Patch patch = ContainerPatches.find(id, info.pos());
                if (patch != null) {
                    source.putString("patched_from", patch.original());
                }
                out.put(world.asLong(), source);
            }
        }
    }
}
