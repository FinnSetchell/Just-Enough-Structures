package com.finndog.justenoughstructures.capture;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.Nbt;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.Reader;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/**
 * The mobs a trial spawner makes, from its normal config. Up to 1.21.4 the config is written into
 * the spawner; from 1.21.5 it's named, and read here from its own file, which the client may not
 * have. Either way a snapshot carries them on the spawner as a list of {entity, weight}.
 */
public final class TrialSpawners {
    /** On a trial spawner's block entity in a snapshot: a list of {entity, weight}. */
    public static final String TAG = "jes:trial";
    private static final String ID = "minecraft:trial_spawner";

    private TrialSpawners() {
    }

    /** Puts the mobs on a trial spawner's tag. Anything else is left as it is. */
    static void describe(CompoundTag tag, ResourceManager resources) {
        if (!Nbt.string(tag, "id").equals(ID)) {
            return;
        }
        ListTag out = new ListTag();
        if (Nbt.hasCompound(tag, "normal_config")) {
            for (Tag t : Nbt.list(Nbt.compound(tag, "normal_config"), "spawn_potentials", Tag.TAG_COMPOUND)) {
                CompoundTag entry = (CompoundTag) t;
                add(out, Nbt.string(Nbt.compound(Nbt.compound(entry, "data"), "entity"), "id"),
                        Nbt.hasNumber(entry, "weight") ? Nbt.getInt(entry, "weight") : 1);
            }
        } else if (Nbt.hasString(tag, "normal_config")) {
            ResourceLocation config = ResourceLocation.tryParse(Nbt.string(tag, "normal_config"));
            if (config != null) {
                readConfig(resources, config, out);
            }
        }
        tag.put(TAG, out);
    }

    private static void readConfig(ResourceManager resources, ResourceLocation config, ListTag out) {
        Optional<Resource> resource = resources.getResource(Ids.of(config.getNamespace(), "trial_spawner/" + config.getPath() + ".json"));
        if (resource.isEmpty()) {
            return;
        }
        try (Reader reader = resource.get().openAsReader()) {
            JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
            if (!json.has("spawn_potentials")) {
                return;
            }
            for (JsonElement e : json.getAsJsonArray("spawn_potentials")) {
                JsonObject entry = e.getAsJsonObject();
                JsonObject entity = entry.getAsJsonObject("data").getAsJsonObject("entity");
                add(out, entity.get("id").getAsString(), entry.has("weight") ? entry.get("weight").getAsInt() : 1);
            }
        } catch (Exception e) {
            JesLog.debug("Couldn't read trial spawner config {}", config, e);
        }
    }

    private static void add(ListTag out, String entity, int weight) {
        if (!entity.isEmpty() && weight > 0) {
            CompoundTag tag = new CompoundTag();
            tag.putString("entity", entity);
            tag.putInt("weight", weight);
            out.add(tag);
        }
    }
}
