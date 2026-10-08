package com.finndog.justenoughstructures.capture;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.Nbt;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * What a trial spawner makes and drops, from its configs. Up to 1.21.4 a config is written into the
 * spawner; from 1.21.5 it's named, and read here from its own file, which the client may not have.
 * Either way a snapshot carries them on the spawner: its normal mobs as a list of {entity, weight},
 * and the loot tables it can drop from when beaten, normal and ominous.
 */
public final class TrialSpawners {
    /** On a trial spawner's block entity in a snapshot: a list of {entity, weight}. */
    public static final String TAG = "jes:trial";
    /** On a trial spawner's block entity in a snapshot: the loot tables it can drop from. */
    public static final String LOOT_TAG = "jes:trial_loot";
    private static final String ID = "minecraft:trial_spawner";
    /** What a config that doesn't say drops from, as the game has it. */
    private static final List<String> DEFAULT_LOOT = List.of("minecraft:spawners/trial_chamber/consumables", "minecraft:spawners/trial_chamber/key");

    private TrialSpawners() {
    }

    private record Mob(String entity, int weight) {
    }

    /** A config's mobs, and its loot tables, or null when it doesn't name any. */
    private record Config(List<Mob> mobs, List<String> loot) {
    }

    /** Puts the mobs and loot on a trial spawner's tag. Anything else is left as it is. */
    static void describe(CompoundTag tag, ResourceManager resources) {
        if (!Nbt.string(tag, "id").equals(ID)) {
            return;
        }
        Config normal = config(tag, "normal_config", resources);
        Config ominous = config(tag, "ominous_config", resources);
        ListTag mobs = new ListTag();
        if (normal != null) {
            for (Mob mob : normal.mobs()) {
                CompoundTag entry = new CompoundTag();
                entry.putString("entity", mob.entity());
                entry.putInt("weight", mob.weight());
                mobs.add(entry);
            }
        }
        tag.put(TAG, mobs);
        Set<String> loot = new LinkedHashSet<>(normal == null || normal.loot() == null ? DEFAULT_LOOT : normal.loot());
        if (ominous != null && ominous.loot() != null) {
            loot.addAll(ominous.loot());
        }
        ListTag tables = new ListTag();
        loot.forEach(table -> tables.add(StringTag.valueOf(table)));
        tag.put(LOOT_TAG, tables);
    }

    /** The loot tables a trial spawner in a snapshot can drop from. */
    public static List<String> loot(CompoundTag tag) {
        List<String> out = new ArrayList<>();
        for (Tag t : Nbt.list(tag, LOOT_TAG, Tag.TAG_STRING)) {
            out.add(Nbt.value((StringTag) t));
        }
        return out;
    }

    /** A config written into the spawner or named by it, or null if it has neither. */
    private static Config config(CompoundTag tag, String key, ResourceManager resources) {
        if (Nbt.hasCompound(tag, key)) {
            CompoundTag config = Nbt.compound(tag, key);
            List<Mob> mobs = new ArrayList<>();
            for (Tag t : Nbt.list(config, "spawn_potentials", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) t;
                add(mobs, Nbt.string(Nbt.compound(Nbt.compound(entry, "data"), "entity"), "id"),
                        Nbt.hasNumber(entry, "weight") ? Nbt.getInt(entry, "weight") : 1);
            }
            List<String> loot = null;
            if (Nbt.hasList(config, "loot_tables_to_eject")) {
                loot = new ArrayList<>();
                for (Tag t : Nbt.list(config, "loot_tables_to_eject", Tag.TAG_COMPOUND)) {
                    String table = Nbt.string((CompoundTag) t, "data");
                    if (!table.isEmpty()) {
                        loot.add(table);
                    }
                }
            }
            return new Config(mobs, loot);
        }
        ResourceLocation named = Nbt.hasString(tag, key) ? ResourceLocation.tryParse(Nbt.string(tag, key)) : null;
        return named == null ? null : read(resources, named);
    }

    private static Config read(ResourceManager resources, ResourceLocation config) {
        Optional<Resource> resource = resources.getResource(Ids.of(config.getNamespace(), "trial_spawner/" + config.getPath() + ".json"));
        if (resource.isEmpty()) {
            return null;
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            List<Mob> mobs = new ArrayList<>();
            if (json.has("spawn_potentials")) {
                for (JsonElement e : json.getAsJsonArray("spawn_potentials")) {
                    JsonObject entry = e.getAsJsonObject();
                    add(mobs, entry.getAsJsonObject("data").getAsJsonObject("entity").get("id").getAsString(),
                            entry.has("weight") ? entry.get("weight").getAsInt() : 1);
                }
            }
            List<String> loot = null;
            if (json.has("loot_tables_to_eject")) {
                loot = new ArrayList<>();
                for (JsonElement e : json.getAsJsonArray("loot_tables_to_eject")) {
                    JsonElement data = e.isJsonObject() ? e.getAsJsonObject().get("data") : e;
                    if (data != null && data.isJsonPrimitive()) {
                        loot.add(data.getAsString());
                    }
                }
            }
            return new Config(mobs, loot);
        } catch (Exception e) {
            JesLog.debug("Couldn't read trial spawner config {}", config, e);
            return null;
        }
    }

    private static void add(List<Mob> out, String entity, int weight) {
        if (!entity.isEmpty() && weight > 0) {
            out.add(new Mob(entity, weight));
        }
    }
}
