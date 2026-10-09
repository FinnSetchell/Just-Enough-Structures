package com.finndog.justenoughstructures.overrides;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.server.ServerConfig;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The patches of one kind, containers or spawners, kept in their file: read once and then whenever
 * they change, saved and removed one spot at a time, and remembered once undone until the next
 * /reload, as templates loaded before then still have them.
 */
final class PatchStore<P extends PatchStore.Spot> {
    /** What every patch names: its template, and its spot in it. */
    interface Spot {
        ResourceLocation template();

        BlockPos pos();
    }

    private final PatchFile file;
    /** "container" or "spawner", for the log and the lang file. */
    private final String kind;
    private final Function<JsonElement, P> parse;
    private final Function<P, JsonObject> toJson;
    private volatile Map<ResourceLocation, List<P>> byTemplate;
    /** Patches undone, by template and spot. */
    private final Map<List<Object>, P> undone = new ConcurrentHashMap<>();

    /** {@code parse} gives null for an entry that doesn't make sense, which is then left out. */
    PatchStore(String fileName, String kind, Function<JsonElement, P> parse, Function<P, JsonObject> toJson) {
        this.file = new PatchFile(fileName);
        this.kind = kind;
        this.parse = parse;
        this.toJson = toJson;
    }

    /** The patches in use by template, or none while changes to containers and spawners are turned off. */
    Map<ResourceLocation, List<P>> patches() {
        if (!ServerConfig.get().containerChanges()) {
            return Map.of();
        }
        Map<ResourceLocation, List<P>> loaded = byTemplate;
        if (loaded == null) {
            load();
            loaded = byTemplate;
        }
        return loaded;
    }

    /** A template's patches, for when it's just been loaded, or null for none. Never throws. */
    List<P> forTemplate(ResourceLocation id) {
        try {
            return patches().get(id);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Reads the patches again. Templates loaded from then on use them; loaded ones are dropped on /reload. */
    synchronized void load() {
        Map<ResourceLocation, List<P>> out = new HashMap<>();
        try {
            for (JsonElement element : file.entries()) {
                P patch = parse.apply(element);
                if (patch != null) {
                    out.computeIfAbsent(patch.template(), id -> new ArrayList<>()).add(patch);
                }
            }
        } catch (IOException | RuntimeException e) {
            // Read on every /reload, so the same broken file is only worth one warning.
            Path path = file.path();
            JesLog.warnOnce(kind + "-patches:" + path + "|" + e.getMessage(), "Couldn't read the " + kind + " patches in {}, leaving " + kind + "s as they are: {}",
                    path, e.toString());
            JesLog.debug("Couldn't read the " + kind + " patches in {}", path, e);
        }
        byTemplate = out;
    }

    /** Every patch saved, in the order they were saved, whether or not changes are in use. */
    synchronized List<P> all() {
        List<P> out = new ArrayList<>();
        try {
            for (JsonElement element : file.entries()) {
                P patch = parse.apply(element);
                if (patch != null) {
                    out.add(patch);
                }
            }
        } catch (IOException | RuntimeException e) {
            JesLog.debug("Couldn't read the " + kind + " patches in {}", file.path(), e);
        }
        return out;
    }

    P find(ResourceLocation template, BlockPos pos) {
        for (P patch : patches().getOrDefault(template, List.of())) {
            if (patch.pos().equals(pos)) {
                return patch;
            }
        }
        return null;
    }

    /** Saves a patch, replacing any for the same spot. It applies from the next /reload. */
    synchronized Component save(P patch) {
        try {
            file.save(patch.template(), patch.pos(), toJson.apply(patch));
        } catch (IOException | RuntimeException e) {
            JustEnoughStructures.LOGGER.warn("Couldn't save the " + kind + " patch for {} in {}: {}", patch.pos(), patch.template(), e.toString());
            JesLog.debug("Couldn't save the " + kind + " patch for {} in {}", patch.pos(), patch.template(), e);
            return Component.translatable("screen.justenoughstructures.override.save_failed", String.valueOf(e.getMessage()));
        }
        load();
        return Component.translatable("screen.justenoughstructures." + kind + ".saved");
    }

    /** Stops patching a spot. The patch moves to the file's list of removed ones rather than going. */
    synchronized Component remove(ResourceLocation template, BlockPos pos) {
        P removing = null;
        for (P patch : all()) {
            if (patch.template().equals(template) && patch.pos().equals(pos)) {
                removing = patch;
            }
        }
        try {
            if (!file.remove(template, pos)) {
                return Component.translatable("screen.justenoughstructures." + kind + ".none");
            }
        } catch (IOException | RuntimeException e) {
            return Component.translatable("screen.justenoughstructures.override.save_failed", String.valueOf(e.getMessage()));
        }
        if (removing != null) {
            undone.put(List.of(template, pos), removing);
        }
        load();
        return Component.translatable("screen.justenoughstructures." + kind + ".removed");
    }

    /** The patch undone at a spot since the last /reload, or null. */
    P undone(ResourceLocation template, BlockPos pos) {
        return undone.get(List.of(template, pos));
    }
}
