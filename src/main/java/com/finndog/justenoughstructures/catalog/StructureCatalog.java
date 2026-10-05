package com.finndog.justenoughstructures.catalog;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.Regs;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
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
            JsonObject placement = encode(StructurePlacement.CODEC, ops, set.placement(), Ids.of(e.getKey()));
            for (StructureSet.StructureSelectionEntry sel : set.structures()) {
                sel.structure().unwrapKey().ifPresent(key -> setsByStructure
                        .computeIfAbsent(Ids.of(key), k -> new ArrayList<>())
                        .add(new SetInfo(Ids.of(e.getKey()), placement, sel.weight())));
            }
        }

        List<Entry> out = new ArrayList<>();
        for (Map.Entry<ResourceKey<Structure>, Structure> e : structures.entrySet()) {
            ResourceLocation id = Ids.of(e.getKey());
            try {
                Structure structure = e.getValue();
                ResourceLocation type = BuiltInRegistries.STRUCTURE_TYPE.getKey(structure.type());
                JsonObject definition = encode(Structure.DIRECT_CODEC, ops, structure, id);
                List<SetInfo> inSets = List.copyOf(setsByStructure.getOrDefault(id, List.of()));
                out.add(new Entry(id, type, definition, inSets, StructureInfo.forStructure(id), availability(structures, e.getKey(), inSets)));
            } catch (RuntimeException | LinkageError ex) {
                // A structure some mod left broken is left out, rather than taking the whole list with it.
                JesLog.warnOnce("catalog:" + id, "Left {} out of the structure list, as it couldn't be read: {}", id, ex.toString());
            }
        }
        out.sort(Comparator.comparing(entry -> entry.id().toString()));
        return out;
    }

    private static final TagKey<Structure> INTEGRATED_API_DISABLED = TagKey.create(Registries.STRUCTURE,
            Ids.of("integrated_api", "disabled_structures"));

    /** Whether the structure turns up in new worlds on its own, and if not, why. */
    private static Availability availability(Registry<Structure> structures, ResourceKey<Structure> key, List<SetInfo> sets) {
        Availability byMod = StructureDisables.check(Ids.of(key));
        if (byMod != null) {
            // A replacement that isn't there to look at isn't named.
            return byMod.replacedBy() != null && !structures.containsKey(byMod.replacedBy())
                    ? new Availability(byMod.reason(), byMod.by(), null) : byMod;
        }
        if (Regs.holder(structures, key).map(holder -> holder.is(INTEGRATED_API_DISABLED)).orElse(false)) {
            return new Availability(Availability.Reason.TAGGED_OFF, "integrated_api", null);
        }
        if (sets.isEmpty()) {
            return new Availability(Availability.Reason.NO_SET, null, null);
        }
        return sets.stream().anyMatch(set -> frequency(set.placement()) > 0) ? Availability.GENERATES
                : new Availability(Availability.Reason.NEVER, null, null);
    }

    /** How often a set's placement tries at all: 1 unless it says otherwise. */
    private static float frequency(JsonObject placement) {
        try {
            return placement != null && placement.has("frequency") ? placement.get("frequency").getAsFloat() : 1f;
        } catch (RuntimeException e) {
            return 1f;
        }
    }

    /**
     * What {@code codec} writes for {@code value}, or null if it can't. Some mods' codecs throw rather
     * than say so, which would otherwise take the whole list down with them.
     */
    private static <T> JsonObject encode(Codec<T> codec, DynamicOps<JsonElement> ops, T value, ResourceLocation id) {
        try {
            return codec.encodeStart(ops, value).result().filter(JsonElement::isJsonObject).map(JsonElement::getAsJsonObject).orElse(null);
        } catch (RuntimeException | LinkageError e) {
            JesLog.debug("Couldn't write out {}: {}", id, e.toString());
            return null;
        }
    }

    /**
     * One structure. {@code definition} is the structure's JSON as its codec writes it, or null when
     * the codec couldn't encode it. {@code info} is what its mod or a datapack says about it, and
     * {@code availability} whether it turns up in new worlds.
     */
    public record Entry(ResourceLocation id, ResourceLocation type, JsonObject definition, List<SetInfo> sets, StructureInfo info,
                        Availability availability) {
        public Entry withInfo(StructureInfo newInfo) {
            return new Entry(id, type, definition, sets, newInfo, availability);
        }
    }

    /** A structure set the structure belongs to, with its placement JSON (null if it didn't encode). */
    public record SetInfo(ResourceLocation setId, JsonObject placement, int weight) {
    }
}
