package com.finndog.justenoughstructures.loot;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.Nbt;
import com.finndog.justenoughstructures.Regs;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorList;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Finds the loot tables a jigsaw structure can place by reading its template pools instead of
 * generating it: every piece in every pool it can reach, the chests and minecarts saved in those
 * pieces, and loot added by their processors. That covers rare pieces a few test generations
 * would miss. Structures build their pools in their own ways, so it works from each structure's
 * and pool's own codec output rather than their classes, which covers modded jigsaw types too.
 * Loot that structure code sets as it generates still needs a real generation to find.
 */
final class PoolScan {
    private record Pool(Set<ResourceLocation> tables, Set<ResourceLocation> pools, Set<ResourceLocation> templates) {
    }

    /** The loot tables a structure's pieces can hold and the templates those pieces are. */
    record Reach(Set<ResourceLocation> tables, Set<ResourceLocation> templates) {
    }

    private final MinecraftServer server;
    private final DynamicOps<JsonElement> ops;
    private final Map<ResourceLocation, Pool> pools = new HashMap<>();
    private final Map<ResourceLocation, Pool> templates = new HashMap<>();
    private final Map<ResourceLocation, Set<ResourceLocation>> processorLists = new HashMap<>();

    PoolScan(MinecraftServer server) {
        this.server = server;
        this.ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
    }

    /** The loot tables the structure's pieces can hold and its templates, or null if it isn't built from template pools. */
    Reach scan(Structure structure) {
        JsonElement json = Structure.DIRECT_CODEC.encodeStart(ops, structure).result().orElse(null);
        if (json == null) {
            return null;
        }
        Set<ResourceLocation> starts = new HashSet<>();
        strings(json, "start_pool", starts::add);
        if (starts.isEmpty()) {
            return null;
        }
        Set<ResourceLocation> tables = new HashSet<>();
        Set<ResourceLocation> templates = new HashSet<>();
        Set<ResourceLocation> seen = new HashSet<>();
        Deque<ResourceLocation> queue = new ArrayDeque<>(starts);
        while (!queue.isEmpty()) {
            ResourceLocation id = queue.pop();
            if (!seen.add(id)) {
                continue;
            }
            Pool pool = pool(id);
            tables.addAll(pool.tables());
            templates.addAll(pool.templates());
            queue.addAll(pool.pools());
        }
        return new Reach(tables, templates);
    }

    private Pool pool(ResourceLocation id) {
        Pool cached = pools.get(id);
        if (cached != null) {
            return cached;
        }
        Set<ResourceLocation> tables = new HashSet<>();
        Set<ResourceLocation> next = new HashSet<>();
        Set<ResourceLocation> locations = new HashSet<>();
        Registry<StructureTemplatePool> registry = server.registryAccess().registryOrThrow(Registries.TEMPLATE_POOL);
        StructureTemplatePool pool = Regs.value(registry, id);
        JsonElement json = pool == null ? null : StructureTemplatePool.DIRECT_CODEC.encodeStart(ops, pool).result().orElse(null);
        if (json != null) {
            strings(json, "fallback", next::add);
            strings(json, "loot_table", tables::add);
            strings(json, "processors", list -> tables.addAll(processorList(list)));
            strings(json, "location", locations::add);
            for (ResourceLocation location : locations) {
                Pool template = template(location);
                tables.addAll(template.tables());
                next.addAll(template.pools());
            }
        }
        next.remove(Ids.parse("empty"));
        Pool result = new Pool(tables, next, locations);
        pools.put(id, result);
        return result;
    }

    /** Loot tables in a saved piece's blocks and entities, and the pools its jigsaw blocks lead to. */
    private Pool template(ResourceLocation id) {
        Pool cached = templates.get(id);
        if (cached != null) {
            return cached;
        }
        Set<ResourceLocation> tables = new HashSet<>();
        Set<ResourceLocation> next = new HashSet<>();
        try {
            StructureTemplate template = server.getStructureManager().get(id).orElse(null);
            if (template != null) {
                nbt(template.save(new CompoundTag()), "", tables, next);
            }
        } catch (RuntimeException e) {
            JesLog.debug("Couldn't read structure piece {}", id, e);
        }
        Pool result = new Pool(tables, next, Set.of());
        templates.put(id, result);
        return result;
    }

    /** Tables added by a processor list's rules, such as the archaeology loot in trail ruins. */
    private Set<ResourceLocation> processorList(ResourceLocation id) {
        return processorLists.computeIfAbsent(id, key -> {
            Set<ResourceLocation> tables = new HashSet<>();
            StructureProcessorList list = Regs.value(server.registryAccess().registryOrThrow(Registries.PROCESSOR_LIST), key);
            if (list != null) {
                StructureProcessorType.DIRECT_CODEC.encodeStart(ops, list).result()
                        .ifPresent(json -> strings(json, "loot_table", tables::add));
            }
            return tables;
        });
    }

    /** Tables and pools named anywhere in a piece's blocks and entities. {@code parent} is the key {@code tag} sits under. */
    private static void nbt(Tag tag, String parent, Set<ResourceLocation> tables, Set<ResourceLocation> pools) {
        if (tag instanceof CompoundTag compound) {
            for (String key : Nbt.keys(compound)) {
                Tag value = compound.get(key);
                if (value instanceof StringTag string) {
                    // A container's own table, a vault's, which sits in its config, and what a trial
                    // spawner drops when it's beaten. Mobs' equipment tables aren't loot.
                    if (key.equals("LootTable") || key.equals("loot_table") && parent.equals("config")
                            || key.equals("data") && parent.equals("loot_tables_to_eject")) {
                        add(Nbt.value(string), tables::add);
                    } else if (key.equals("pool")) {
                        add(Nbt.value(string), pools::add);
                    }
                } else {
                    nbt(value, key, tables, pools);
                }
            }
        } else if (tag instanceof ListTag list) {
            for (Tag element : list) {
                nbt(element, parent, tables, pools);
            }
        }
    }

    /** Every string value under the given key anywhere in the JSON, read as an id. */
    private static void strings(JsonElement json, String key, Consumer<ResourceLocation> out) {
        if (json instanceof JsonArray array) {
            array.forEach(e -> strings(e, key, out));
        } else if (json instanceof JsonObject object) {
            for (Map.Entry<String, JsonElement> e : object.entrySet()) {
                if (e.getKey().equals(key) && e.getValue().isJsonPrimitive()) {
                    add(e.getValue().getAsString(), out);
                } else {
                    strings(e.getValue(), key, out);
                }
            }
        }
    }

    private static void add(String value, Consumer<ResourceLocation> out) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id != null && !value.isEmpty()) {
            out.accept(id);
        }
    }
}
