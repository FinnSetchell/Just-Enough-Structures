package com.finndog.justenoughstructures.capture;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.Nbt;
import com.finndog.justenoughstructures.Regs;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.Reader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.block.state.BlockState;
//? if >=1.21 {
/*import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.TrialSpawnerBlock;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
*///?}

/**
 * What a trial spawner makes and drops, from its configs, and giving one another mob. Up to 1.21.4
 * a config is written into the spawner; from 1.21.5 it's named, and read here from its own file,
 * which the client may not have. Either way a snapshot carries them on the spawner: its mobs as a
 * list of {entity, weight}, the same for once it's ominous, and the loot tables it can drop from when
 * beaten, normal and ominous.
 */
public final class TrialSpawners {
    /** On a trial spawner's block entity in a snapshot: a list of {entity, weight}, with gear: true for a mob given equipment. */
    public static final String TAG = "jes:trial";
    /** The same for what it makes once it's ominous. */
    public static final String OMINOUS_TAG = "jes:trial_ominous";
    /** On a trial spawner's block entity in a snapshot: the loot tables it can drop from. */
    public static final String LOOT_TAG = "jes:trial_loot";
    public static final ResourceLocation BLOCK = Ids.of("minecraft", "trial_spawner");
    private static final String ID = "minecraft:trial_spawner";
    private static final String NORMAL = "normal_config";
    private static final String OMINOUS = "ominous_config";
    /** What a config that doesn't say drops from, as the game has it. */
    private static final List<String> DEFAULT_LOOT = List.of("minecraft:spawners/trial_chamber/consumables", "minecraft:spawners/trial_chamber/key");
    /** The armour the trial chambers' ominous mobs wear, which fits any mob, unlike the weapons they're given as well. */
    private static final String OMINOUS_GEAR = "minecraft:equipment/trial_chamber";

    private TrialSpawners() {
    }

    private record Mob(String entity, int weight, boolean gear) {
    }

    /** A config's mobs, and its loot tables, or null when it doesn't name any. */
    private record Config(List<Mob> mobs, List<String> loot) {
    }

    /** Whether this version of the game has trial spawners, which came in 1.21. */
    public static boolean exist() {
        //? if >=1.21 {
        /*return true;
        *///?} else {
        return false;
        //?}
    }

    public static boolean is(BlockState state) {
        //? if >=1.21 {
        /*return state.getBlock() instanceof TrialSpawnerBlock;
        *///?} else {
        return false;
        //?}
    }

    /** Whether the block with this id is a trial spawner. */
    public static boolean isBlock(ResourceLocation block) {
        return BuiltInRegistries.BLOCK.containsKey(block) && is(Regs.value(BuiltInRegistries.BLOCK, block).defaultBlockState());
    }

    /** A trial spawner waiting for players, as the trial chambers place them. Only call it where they {@link #exist()}. */
    public static BlockState waiting() {
        //? if >=1.21 {
        /*return Blocks.TRIAL_SPAWNER.defaultBlockState().setValue(TrialSpawnerBlock.STATE, TrialSpawnerState.WAITING_FOR_PLAYERS);
        *///?} else {
        throw new UnsupportedOperationException("there are no trial spawners before 1.21");
        //?}
    }

    /** Puts the mobs, normal and ominous, and the loot on a trial spawner's tag. Anything else is left as it is. */
    static void describe(CompoundTag tag, ResourceManager resources) {
        if (!Nbt.string(tag, "id").equals(ID)) {
            return;
        }
        Config normal = config(tag, NORMAL, resources);
        Config ominous = config(tag, OMINOUS, resources);
        List<Mob> mobs = normal == null ? List.of() : normal.mobs();
        tag.put(TAG, list(mobs));
        // Once ominous, one with no mobs of its own keeps making the one it was about to.
        tag.put(OMINOUS_TAG, list(ominous == null || ominous.mobs().isEmpty() ? mobs : ominous.mobs()));
        ListTag tables = new ListTag();
        loot(normal, ominous).forEach(table -> tables.add(StringTag.valueOf(table)));
        tag.put(LOOT_TAG, tables);
    }

    /**
     * The loot tables a trial spawner with this data, from a template or the world, can drop when it's
     * beaten, normal and ominous.
     */
    public static Set<String> lootOf(CompoundTag spawner, ResourceManager resources) {
        return loot(config(spawner, NORMAL, resources), config(spawner, OMINOUS, resources));
    }

    /** What a config that doesn't say, or that the spawner doesn't have, drops is the game's own. */
    private static Set<String> loot(Config normal, Config ominous) {
        Set<String> loot = new LinkedHashSet<>(normal == null || normal.loot() == null ? DEFAULT_LOOT : normal.loot());
        loot.addAll(ominous == null || ominous.loot() == null ? DEFAULT_LOOT : ominous.loot());
        return loot;
    }

    /** The loot tables a trial spawner in a snapshot can drop from. */
    public static List<String> loot(CompoundTag tag) {
        List<String> out = new ArrayList<>();
        for (Tag t : Nbt.list(tag, LOOT_TAG, Tag.TAG_STRING)) {
            out.add(Nbt.value((StringTag) t));
        }
        return out;
    }

    /** A trial spawner's mobs as a snapshot lists them, from its data in a template or in the world. */
    static ListTag mobs(CompoundTag spawner, ResourceManager resources) {
        Config normal = spawner == null ? null : config(spawner, NORMAL, resources);
        return list(normal == null ? List.of() : normal.mobs());
    }

    /**
     * The mob a trial spawner makes most, or "" for none. Null when its config is named but can't be
     * read, so what it makes isn't known.
     */
    public static String mainMob(CompoundTag spawner, ResourceManager resources) {
        List<Mob> mobs = knownMobs(spawner, resources);
        if (mobs == null) {
            return null;
        }
        Mob most = null;
        for (Mob mob : mobs) {
            if (most == null || mob.weight() > most.weight()) {
                most = mob;
            }
        }
        return most == null ? "" : most.entity();
    }

    /** How many other mobs a trial spawner makes besides its main one, or 0 when that isn't known. */
    public static int otherMobs(CompoundTag spawner, ResourceManager resources) {
        List<Mob> mobs = knownMobs(spawner, resources);
        if (mobs == null) {
            return 0;
        }
        Set<String> ids = new LinkedHashSet<>();
        mobs.forEach(mob -> ids.add(mob.entity()));
        ids.remove(mainMob(spawner, resources));
        return ids.size();
    }

    private static List<Mob> knownMobs(CompoundTag spawner, ResourceManager resources) {
        if (spawner == null) {
            return List.of();
        }
        Config normal = config(spawner, NORMAL, resources);
        if (normal == null) {
            // With no config at all it makes nothing; one that's named and can't be read could make anything.
            return Nbt.hasString(spawner, NORMAL) ? null : List.of();
        }
        return normal.mobs();
    }

    /**
     * Changes a trial spawner's data to make just {@code mob}, or nothing when it's "", normal and
     * ominous, with everything else about it kept, as a spawn egg does to one in the world. A named
     * config is written in, so it can be changed. Each config keeps its first mob's equipment and rules
     * about where it spawns, so an ominous one still arms what it makes. The mob it was about to make
     * next goes, or that would come first.
     */
    public static void setMob(CompoundTag spawner, String mob, ResourceManager resources) {
        for (String key : List.of(NORMAL, OMINOUS)) {
            CompoundTag config = inline(spawner, key, resources);
            if (config == null && key.equals(OMINOUS)) {
                // With none of its own, it makes the normal mob once ominous too.
                continue;
            }
            if (config == null) {
                config = new CompoundTag();
            }
            ListTag old = Nbt.list(config, "spawn_potentials", Tag.TAG_COMPOUND);
            config.put("spawn_potentials", potentials(mob, old.isEmpty() ? null : Nbt.compound(Nbt.compound(old, 0), "data")));
            spawner.put(key, config);
        }
        spawner.remove("spawn_data");
    }

    /**
     * A new trial spawner's data making {@code mob}, or nothing when it's "", set up like the trial
     * chambers' own: once ominous, what it makes wears armour and it drops ominous keys.
     */
    public static CompoundTag fresh(String mob) {
        CompoundTag out = new CompoundTag();
        out.putString("id", ID);
        CompoundTag normal = new CompoundTag();
        normal.put("spawn_potentials", potentials(mob, null));
        out.put(NORMAL, normal);
        CompoundTag gear = new CompoundTag();
        gear.putString("loot_table", OMINOUS_GEAR);
        gear.putFloat("slot_drop_chances", 0f);
        CompoundTag armed = new CompoundTag();
        armed.put("equipment", gear);
        CompoundTag ominous = new CompoundTag();
        ominous.put("spawn_potentials", potentials(mob, armed));
        ListTag loot = new ListTag();
        loot.add(weighted("minecraft:spawners/ominous/trial_chamber/key", 3));
        loot.add(weighted("minecraft:spawners/ominous/trial_chamber/consumables", 7));
        ominous.put("loot_tables_to_eject", loot);
        out.put(OMINOUS, ominous);
        return out;
    }

    /** Spawn potentials of just {@code mob}, with {@code data}'s fields besides the mob itself. */
    private static ListTag potentials(String mob, CompoundTag data) {
        ListTag out = new ListTag();
        if (mob.isEmpty()) {
            return out;
        }
        CompoundTag spawn = data == null ? new CompoundTag() : data.copy();
        CompoundTag entity = new CompoundTag();
        entity.putString("id", mob);
        spawn.put("entity", entity);
        CompoundTag entry = new CompoundTag();
        entry.put("data", spawn);
        entry.putInt("weight", 1);
        out.add(entry);
        return out;
    }

    private static CompoundTag weighted(String data, int weight) {
        CompoundTag entry = new CompoundTag();
        entry.putString("data", data);
        entry.putInt("weight", weight);
        return entry;
    }

    private static ListTag list(List<Mob> mobs) {
        ListTag out = new ListTag();
        for (Mob mob : mobs) {
            CompoundTag entry = new CompoundTag();
            entry.putString("entity", mob.entity());
            entry.putInt("weight", mob.weight());
            if (mob.gear()) {
                entry.putBoolean("gear", true);
            }
            out.add(entry);
        }
        return out;
    }

    /** A config written into the spawner or named by it, or null if it has neither or the named one can't be read. */
    private static Config config(CompoundTag tag, String key, ResourceManager resources) {
        if (Nbt.hasCompound(tag, key)) {
            CompoundTag config = Nbt.compound(tag, key);
            List<Mob> mobs = new ArrayList<>();
            for (Tag t : Nbt.list(config, "spawn_potentials", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) t;
                CompoundTag data = Nbt.compound(entry, "data");
                add(mobs, Nbt.string(Nbt.compound(data, "entity"), "id"), Nbt.hasNumber(entry, "weight") ? Nbt.getInt(entry, "weight") : 1,
                        Nbt.hasCompound(data, "equipment"));
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
        JsonObject json = named == null ? null : json(resources, named);
        return json == null ? null : read(named, json);
    }

    /**
     * A config as it would be written into the spawner: a copy of its own, or the named one's file
     * read in. A named one that can't be read comes back empty, and null means it has none.
     */
    private static CompoundTag inline(CompoundTag spawner, String key, ResourceManager resources) {
        if (Nbt.hasCompound(spawner, key)) {
            return Nbt.compound(spawner, key).copy();
        }
        if (!Nbt.hasString(spawner, key)) {
            return null;
        }
        ResourceLocation named = ResourceLocation.tryParse(Nbt.string(spawner, key));
        JsonObject json = named == null ? null : json(resources, named);
        if (json != null && JsonOps.INSTANCE.convertTo(NbtOps.INSTANCE, json) instanceof CompoundTag config) {
            return config;
        }
        JesLog.warnOnce("trial-config:" + named, "Couldn't read the trial spawner config {}, so a trial spawner changed in the browser gets the game's usual settings",
                Nbt.string(spawner, key));
        return new CompoundTag();
    }

    /** A named config's file, from 1.21.5 data/[namespace]/trial_spawner/[path].json, or null if there's none or it isn't an object. */
    private static JsonObject json(ResourceManager resources, ResourceLocation config) {
        if (resources == null) {
            return null;
        }
        Optional<Resource> resource = resources.getResource(Ids.of(config.getNamespace(), "trial_spawner/" + config.getPath() + ".json"));
        if (resource.isEmpty()) {
            return null;
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonElement json = JsonParser.parseReader(reader);
            return json.isJsonObject() ? json.getAsJsonObject() : null;
        } catch (Exception e) {
            JesLog.debug("Couldn't read trial spawner config {}", config, e);
            return null;
        }
    }

    private static Config read(ResourceLocation config, JsonObject json) {
        try {
            List<Mob> mobs = new ArrayList<>();
            if (json.has("spawn_potentials")) {
                for (JsonElement e : json.getAsJsonArray("spawn_potentials")) {
                    JsonObject entry = e.getAsJsonObject();
                    JsonObject data = entry.getAsJsonObject("data");
                    add(mobs, data.getAsJsonObject("entity").get("id").getAsString(), entry.has("weight") ? entry.get("weight").getAsInt() : 1,
                            data.has("equipment"));
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
        } catch (RuntimeException e) {
            JesLog.debug("Couldn't read trial spawner config {}", config, e);
            return null;
        }
    }

    private static void add(List<Mob> out, String entity, int weight, boolean gear) {
        if (!entity.isEmpty() && weight > 0) {
            out.add(new Mob(entity, weight, gear));
        }
    }
}
