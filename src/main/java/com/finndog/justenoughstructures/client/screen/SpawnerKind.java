package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.capture.SpawnerPools;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * What a spawner makes, which is how the Mobs tab groups spawners and how far a spawner's popup
 * steps: one mob (none for an empty spawner), a list it got one of as it generated, or a mix it
 * keeps making, each with every mob's weight.
 */
public record SpawnerKind(Type type, Map<String, Integer> mobs) {
    public enum Type { MOB, POOL, MIX }

    public static SpawnerKind of(CompoundTag tag) {
        Map<String, Integer> pool = weights(tag.getList(SpawnerPools.TAG, Tag.TAG_COMPOUND), "entity", null);
        if (pool.size() > 1) {
            return new SpawnerKind(Type.POOL, pool);
        }
        Map<String, Integer> potentials = weights(tag.getList("SpawnPotentials", Tag.TAG_COMPOUND), "data", "weight");
        if (potentials.size() > 1) {
            return new SpawnerKind(Type.MIX, potentials);
        }
        String mob = tag.getCompound("SpawnData").getCompound("entity").getString("id");
        return new SpawnerKind(Type.MOB, mob.isEmpty() ? Map.of() : Map.of(mob, 1));
    }

    /** The one mob of a spawner that makes only that, or "" for one that makes nothing. */
    String mob() {
        return mobs.size() == 1 ? mobs.keySet().iterator().next() : "";
    }

    /**
     * Mob ids to weights from a list of entries, merging repeats. With no {@code weightKey} the id is
     * under {@code key}; otherwise {@code key} holds SpawnPotentials' entity data.
     */
    static Map<String, Integer> weights(ListTag list, String key, String weightKey) {
        Map<String, Integer> out = new LinkedHashMap<>();
        for (Tag t : list) {
            CompoundTag entry = (CompoundTag) t;
            String mob = weightKey == null ? entry.getString(key) : entry.getCompound(key).getCompound("entity").getString("id");
            int weight = entry.getInt(weightKey == null ? "weight" : weightKey);
            if (!mob.isEmpty() && weight > 0) {
                out.merge(mob, weight, Integer::sum);
            }
        }
        return out;
    }

    /** The spawners' block entities in a snapshot, by where they are. */
    public static Map<BlockPos, CompoundTag> tags(StructureSnapshot s) {
        Map<BlockPos, CompoundTag> out = new HashMap<>();
        for (CompoundTag tag : s.blockEntities()) {
            if (tag.contains("SpawnData", Tag.TAG_COMPOUND)) {
                out.put(new BlockPos(tag.getInt("x"), tag.getInt("y"), tag.getInt("z")), tag);
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
