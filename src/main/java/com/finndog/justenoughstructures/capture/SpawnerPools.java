package com.finndog.justenoughstructures.capture;

import com.finndog.justenoughstructures.JesLog;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.SpawnerBlock;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Every mob a spawner could have been given, for spawners whose mob a processor picked from a
 * weighted list: Repurposed Structures' and Integrated API's spawner files, and the list Moog's
 * Structure Lib's processor carries itself. Recorded while a capture places templates, and kept
 * only where the spawner still has a mob from the list, since structure code can change it after.
 */
public final class SpawnerPools {
    /** On a spawner's block entity in a snapshot: a list of {entity, weight}. */
    public static final String TAG = "jes:pool";
    // Processor fields naming a file of mobs, and the data folder those files are read from.
    private static final Map<String, String> FILE_FIELDS = Map.of(
            "rs_spawner_resourcelocation", "rs_spawners",
            "integrated_api_spawner_resourcelocation", "integrated_structure_spawners");
    private static final ThreadLocal<Map<Long, Pool>> RECORDED = new ThreadLocal<>();
    private static final Map<StructureProcessor, Optional<Pool>> KNOWN = Collections.synchronizedMap(new WeakHashMap<>());

    private SpawnerPools() {
    }

    /** A file of mobs, or the mobs written into the processor when {@code file} is null. */
    record Pool(ResourceLocation file, List<Entry> inline) {
    }

    record Entry(String entity, int weight) {
    }

    static void begin() {
        RECORDED.set(new HashMap<>());
    }

    /** Stops recording, and gives back each spawner's pool by world position. */
    static Map<Long, Pool> end() {
        Map<Long, Pool> recorded = RECORDED.get();
        RECORDED.remove();
        return recorded == null ? Map.of() : recorded;
    }

    /** Called after each processor handles a block of a template being placed. Does nothing outside a capture. */
    public static void processed(StructureProcessor processor, StructureTemplate.StructureBlockInfo before, StructureTemplate.StructureBlockInfo after) {
        Map<Long, Pool> recorded = RECORDED.get();
        if (recorded == null || after == null || !(after.state().getBlock() instanceof SpawnerBlock) || Objects.equals(before.nbt(), after.nbt())) {
            return;
        }
        // The last processor to change a spawner decides its mob.
        Pool pool = KNOWN.computeIfAbsent(processor, p -> Optional.ofNullable(read(p))).orElse(null);
        if (pool != null) {
            recorded.put(after.pos().asLong(), pool);
        } else {
            recorded.remove(after.pos().asLong());
        }
    }

    /** The pool a processor picks spawner mobs from, read from what it saves as, or null if it's not one of these. */
    private static Pool read(StructureProcessor processor) {
        try {
            JsonElement json = StructureProcessorType.SINGLE_CODEC.encodeStart(JsonOps.INSTANCE, processor).result().orElse(null);
            if (!(json instanceof JsonObject object)) {
                return null;
            }
            for (Map.Entry<String, String> field : FILE_FIELDS.entrySet()) {
                if (object.has(field.getKey())) {
                    ResourceLocation named = ResourceLocation.tryParse(object.get(field.getKey()).getAsString());
                    return named == null ? null
                            : new Pool(new ResourceLocation(named.getNamespace(), field.getValue() + "/" + named.getPath() + ".json"), List.of());
                }
            }
            if (object.has("weighted_entities") && object.get("weighted_entities").isJsonArray()) {
                List<Entry> entries = new ArrayList<>();
                for (JsonElement e : object.getAsJsonArray("weighted_entities")) {
                    if (e.isJsonObject() && e.getAsJsonObject().has("entity") && e.getAsJsonObject().has("weight")) {
                        entries.add(new Entry(e.getAsJsonObject().get("entity").getAsString(), e.getAsJsonObject().get("weight").getAsInt()));
                    }
                }
                return new Pool(null, entries);
            }
        } catch (RuntimeException e) {
            JesLog.debug("Couldn't read processor {}", processor, e);
        }
        return null;
    }

    /** Each pool as the list a snapshot carries, with files read and mobs that don't exist left out. Empty lists are dropped. */
    static Map<Long, ListTag> resolve(Map<Long, Pool> recorded, ResourceManager resources) {
        Map<ResourceLocation, List<Entry>> files = new HashMap<>();
        Map<Long, ListTag> out = new HashMap<>();
        recorded.forEach((pos, pool) -> {
            List<Entry> entries = pool.file() == null ? pool.inline() : files.computeIfAbsent(pool.file(), file -> readFile(resources, file));
            ListTag list = new ListTag();
            for (Entry entry : entries) {
                ResourceLocation id = ResourceLocation.tryParse(entry.entity());
                if (entry.weight() > 0 && id != null && BuiltInRegistries.ENTITY_TYPE.containsKey(id)) {
                    CompoundTag tag = new CompoundTag();
                    tag.putString("entity", id.toString());
                    tag.putInt("weight", entry.weight());
                    list.add(tag);
                }
            }
            if (!list.isEmpty()) {
                out.put(pos, list);
            }
        });
        return out;
    }

    /** A spawner file's mobs: {"mobs": [{"name": ..., "weight": ...}]}, the format both mods use. */
    private static List<Entry> readFile(ResourceManager resources, ResourceLocation file) {
        Optional<Resource> resource = resources.getResource(file);
        if (resource.isEmpty()) {
            return List.of();
        }
        List<Entry> entries = new ArrayList<>();
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            for (JsonElement e : json.getAsJsonArray("mobs")) {
                JsonObject mob = e.getAsJsonObject();
                entries.add(new Entry(mob.get("name").getAsString(), mob.get("weight").getAsInt()));
            }
        } catch (Exception e) {
            JesLog.debug("Couldn't read spawner file {}", file, e);
            return List.of();
        }
        return entries;
    }

    /** Whether the mob a spawner ended up with is one of its pool's, so the pool is still what decided it. */
    static boolean matches(ListTag pool, CompoundTag spawner) {
        String mob = spawner.getCompound("SpawnData").getCompound("entity").getString("id");
        for (Tag entry : pool) {
            if (((CompoundTag) entry).getString("entity").equals(mob)) {
                return true;
            }
        }
        return false;
    }
}
