package com.finndog.justenoughstructures.catalog;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;

/**
 * The list of structures a client can browse. Definitions are encoded with each structure's own
 * codec, so custom fields from any mod come along without us knowing what they mean.
 */
public final class StructureCatalog {
    private StructureCatalog() {
    }

    public static List<Entry> build(RegistryAccess access) {
        DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, access);
        Registry<Structure> structures = access.registryOrThrow(Registries.STRUCTURE);
        Registry<StructureSet> sets = access.registryOrThrow(Registries.STRUCTURE_SET);

        Map<ResourceLocation, List<SetInfo>> setsByStructure = new HashMap<>();
        for (Map.Entry<ResourceKey<StructureSet>, StructureSet> e : sets.entrySet()) {
            StructureSet set = e.getValue();
            JsonObject placement = encode(StructurePlacement.CODEC.encodeStart(ops, set.placement()).result());
            for (StructureSet.StructureSelectionEntry sel : set.structures()) {
                sel.structure().unwrapKey().ifPresent(key -> setsByStructure
                        .computeIfAbsent(key.location(), k -> new ArrayList<>())
                        .add(new SetInfo(e.getKey().location(), placement, sel.weight())));
            }
        }

        List<Entry> out = new ArrayList<>();
        for (Map.Entry<ResourceKey<Structure>, Structure> e : structures.entrySet()) {
            ResourceLocation id = e.getKey().location();
            Structure structure = e.getValue();
            ResourceLocation type = BuiltInRegistries.STRUCTURE_TYPE.getKey(structure.type());
            JsonObject definition = encode(Structure.DIRECT_CODEC.encodeStart(ops, structure).result());
            out.add(new Entry(id, type, definition, List.copyOf(setsByStructure.getOrDefault(id, List.of())), StructureInfo.forStructure(id)));
        }
        out.sort(Comparator.comparing(entry -> entry.id().toString()));
        return out;
    }

    private static JsonObject encode(Optional<JsonElement> json) {
        return json.filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject).orElse(null);
    }

    /**
     * One structure. {@code definition} is the structure's JSON as its codec writes it, or null when
     * the codec couldn't encode it. {@code info} is what its mod or a datapack says about it.
     */
    public record Entry(ResourceLocation id, ResourceLocation type, JsonObject definition, List<SetInfo> sets, StructureInfo info) {
        public Entry withInfo(StructureInfo newInfo) {
            return new Entry(id, type, definition, sets, newInfo);
        }
    }

    /** A structure set the structure belongs to, with its placement JSON (null if it didn't encode). */
    public record SetInfo(ResourceLocation setId, JsonObject placement, int weight) {
    }
}
