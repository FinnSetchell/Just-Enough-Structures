package com.finndog.justenoughstructures.capture;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.Nbt;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicInteger;
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
 * Every mob a spawner could have been given, for spawners whose mob a processor chose from a
 * weighted list: Repurposed Structures' and Integrated API's spawner files, and the list inside Moog's
 * Structure Lib's processor. Recorded while a capture places templates, and kept only where the
 * spawner still has a mob from the list, since structure code can change it afterwards.
 */
public final class SpawnerPools {
    /** On a spawner's block entity in a snapshot: a list of {entity, weight}. */
    public static final String TAG = "jes:pool";
    // Processor fields naming a file of mobs, and the data folder those files are read from.
    private static final Map<String, String> FILE_FIELDS = Map.of(
            "rs_spawner_resourcelocation", "rs_spawners",
            "integrated_api_spawner_resourcelocation", "integrated_structure_spawners");
    private static final ThreadLocal<Recording> RECORDING = new ThreadLocal<>();
    /**
     * Number of captures recording, across all threads. The processor hooks run for every block placed
     * in any world, so they return straight away when this is zero.
     */
    private static final AtomicInteger RECORDINGS = new AtomicInteger();
    private static final Map<StructureProcessor, Optional<Pool>> KNOWN = Collections.synchronizedMap(new WeakHashMap<>());

    private SpawnerPools() {
    }

    /** A file of mobs, or the mobs written into the processor when {@code file} is null. */
    record Pool(ResourceLocation file, List<Entry> inline) {
    }

    record Entry(String entity, int weight) {
    }

    /**
     * What processors did to spawners during a capture: each spawner's pool by world position, and
     * every spawner a processor changed at all, with or without a pool, as the template no longer
     * decides its mob.
     */
    record Recorded(Map<Long, Pool> pools, LongSet touched) {
        static final Recorded NONE = new Recorded(Map.of(), LongSets.EMPTY_SET);
    }

    /**
     * Recording state for one thread: what it has seen, and a stack of the processors being called,
     * with the block each was given. Deeper than one only when a processor places a template itself.
     */
    private static final class Recording {
        private static final int DEEPEST = 16;
        final Recorded recorded = new Recorded(new HashMap<>(), new LongOpenHashSet());
        final StructureProcessor[] processors = new StructureProcessor[DEEPEST];
        final StructureTemplate.StructureBlockInfo[] blocks = new StructureTemplate.StructureBlockInfo[DEEPEST];
        int asking;
    }

    static void begin() {
        RECORDING.set(new Recording());
        RECORDINGS.incrementAndGet();
    }

    /** Stops recording and returns what was seen. */
    static Recorded end() {
        Recording recording = RECORDING.get();
        if (recording == null) {
            return Recorded.NONE;
        }
        RECORDING.remove();
        RECORDINGS.decrementAndGet();
        return recording.recorded;
    }

    /**
     * Called just before a processor processes a block of a template being placed, with the block it's
     * given. Does nothing outside a capture and allocates nothing inside one.
     */
    public static void asking(StructureProcessor processor, StructureTemplate.StructureBlockInfo block) {
        if (RECORDINGS.get() == 0) {
            return;
        }
        Recording recording = RECORDING.get();
        if (recording != null) {
            int depth = recording.asking++;
            if (depth < Recording.DEEPEST) {
                recording.processors[depth] = processor;
                recording.blocks[depth] = block;
            }
        }
    }

    /** Called with the processor's result for that block. */
    public static void answered(StructureTemplate.StructureBlockInfo after) {
        if (RECORDINGS.get() == 0) {
            return;
        }
        Recording recording = RECORDING.get();
        if (recording == null || recording.asking == 0) {
            return;
        }
        int depth = --recording.asking;
        if (depth < Recording.DEEPEST) {
            StructureProcessor processor = recording.processors[depth];
            StructureTemplate.StructureBlockInfo before = recording.blocks[depth];
            recording.processors[depth] = null;
            recording.blocks[depth] = null;
            processed(recording.recorded, processor, before, after);
        }
    }

    /**
     * A trial spawner a processor changes, like the ones Moog's Structure Lib gives configs, is only
     * recorded as changed: there's no list of mobs to choose from.
     */
    private static void processed(Recorded recorded, StructureProcessor processor, StructureTemplate.StructureBlockInfo before,
                                  StructureTemplate.StructureBlockInfo after) {
        if (after == null || before == null || Objects.equals(before.nbt(), after.nbt())) {
            return;
        }
        boolean trial = TrialSpawners.is(after.state());
        if (!trial && !(after.state().getBlock() instanceof SpawnerBlock)) {
            return;
        }
        recorded.touched().add(after.pos().asLong());
        if (trial) {
            return;
        }
        // The last processor to change a spawner decides its mob.
        Pool pool = KNOWN.computeIfAbsent(processor, p -> Optional.ofNullable(read(p))).orElse(null);
        if (pool != null) {
            recorded.pools().put(after.pos().asLong(), pool);
        } else {
            recorded.pools().remove(after.pos().asLong());
        }
    }

    /** Returns the pool a processor chooses spawner mobs from, read from its codec output, or null if it isn't one of these. */
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
                            : new Pool(Ids.of(named.getNamespace(), field.getValue() + "/" + named.getPath() + ".json"), List.of());
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
        String mob = Nbt.string(Nbt.compound(Nbt.compound(spawner, "SpawnData"), "entity"), "id");
        for (Tag entry : pool) {
            if (Nbt.string((CompoundTag) entry, "entity").equals(mob)) {
                return true;
            }
        }
        return false;
    }
}
