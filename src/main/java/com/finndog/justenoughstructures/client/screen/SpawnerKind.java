package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.Nbt;
import com.finndog.justenoughstructures.capture.SpawnerPools;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.capture.TrialSpawners;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * What a spawner makes. The Mobs tab groups spawners by it, and a spawner's popup steps through those
 * that share it. MOB is one mob (none for an empty spawner), POOL one chosen from a list when it
 * generated, MIX a weighted mix it keeps spawning; {@code mobs} holds each mob's weight. Trial
 * spawners never match other spawners, and also compare {@code ominous}, what they make when ominous,
 * and {@code gear}, the ominous mobs given equipment.
 */
public record SpawnerKind(Type type, Map<String, Integer> mobs, boolean trial, Map<String, Integer> ominous, Set<String> gear) {
    public enum Type { MOB, POOL, MIX }

    public SpawnerKind(Type type, Map<String, Integer> mobs) {
        this(type, mobs, false, Map.of(), Set.of());
    }

    public static SpawnerKind of(CompoundTag tag) {
        if (Nbt.hasList(tag, TrialSpawners.TAG)) {
            Map<String, Integer> mobs = weights(Nbt.list(tag, TrialSpawners.TAG, Tag.TAG_COMPOUND), "entity", null);
            ListTag ominous = Nbt.list(tag, TrialSpawners.OMINOUS_TAG, Tag.TAG_COMPOUND);
            Set<String> gear = new LinkedHashSet<>();
            for (Tag t : ominous) {
                if (Nbt.getByte((CompoundTag) t, "gear") != 0) {
                    gear.add(Nbt.string((CompoundTag) t, "entity"));
                }
            }
            return new SpawnerKind(mobs.size() > 1 ? Type.MIX : Type.MOB, mobs, true,
                    Nbt.hasList(tag, TrialSpawners.OMINOUS_TAG) ? weights(ominous, "entity", null) : mobs, gear);
        }
        Map<String, Integer> pool = weights(Nbt.list(tag, SpawnerPools.TAG, Tag.TAG_COMPOUND), "entity", null);
        if (pool.size() > 1) {
            return new SpawnerKind(Type.POOL, pool);
        }
        Map<String, Integer> potentials = weights(Nbt.list(tag, "SpawnPotentials", Tag.TAG_COMPOUND), "data", "weight");
        if (potentials.size() > 1) {
            return new SpawnerKind(Type.MIX, potentials);
        }
        String mob = Nbt.string(Nbt.compound(Nbt.compound(tag, "SpawnData"), "entity"), "id");
        return new SpawnerKind(Type.MOB, mob.isEmpty() ? Map.of() : Map.of(mob, 1));
    }

    /** The one mob of a spawner that makes only that, or "" for one that makes nothing. */
    String mob() {
        return mobs.size() == 1 ? mobs.keySet().iterator().next() : "";
    }

    /** Whether a trial spawner makes the same mobs, with the same weights, when it's ominous. */
    boolean sameOminous() {
        return ominous.equals(mobs);
    }

    /**
     * Mob ids to weights from a list of entries, merging repeats. With no {@code weightKey} the id is
     * under {@code key}; otherwise {@code key} holds SpawnPotentials' entity data.
     */
    static Map<String, Integer> weights(ListTag list, String key, String weightKey) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Tag t : list) {
            CompoundTag entry = (CompoundTag) t;
            String mob = weightKey == null ? Nbt.string(entry, key) : Nbt.string(Nbt.compound(Nbt.compound(entry, key), "entity"), "id");
            int weight = Nbt.getInt(entry, weightKey == null ? "weight" : weightKey);
            if (!mob.isEmpty() && weight > 0) {
                out.merge(mob, weight, Integer::sum);
            }
        }
        return out;
    }

    /** The spawners' block entities in a snapshot, by position. */
    public static Map<BlockPos, CompoundTag> tags(StructureSnapshot s) {
        Map<BlockPos, CompoundTag> out = new HashMap<>();
        for (CompoundTag tag : s.blockEntities()) {
            if (Nbt.hasCompound(tag, "SpawnData") || Nbt.hasList(tag, TrialSpawners.TAG)) {
                out.put(new BlockPos(Nbt.getInt(tag, "x"), Nbt.getInt(tag, "y"), Nbt.getInt(tag, "z")), tag);
            }
        }
        return out;
    }

    /** Every spawner in the snapshot that makes what this one does, in the snapshot's order. */
    public static List<StructureSnapshot.Spawner> same(StructureSnapshot s, StructureSnapshot.Spawner spawner) {
        Map<BlockPos, CompoundTag> tags = tags(s);
        CompoundTag own = tags.get(spawner.pos());
        List<StructureSnapshot.Spawner> out = new ArrayList<>();
        if (own == null) {
            out.add(spawner);
            return out;
        }
        SpawnerKind kind = of(own);
        for (StructureSnapshot.Spawner other : s.spawners()) {
            CompoundTag tag = tags.get(other.pos());
            if (tag != null && of(tag).equals(kind)) {
                out.add(other);
            }
        }
        return out;
    }
}
