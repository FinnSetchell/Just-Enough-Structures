package com.finndog.justenoughstructures.overrides;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.mixin.StructureTemplateAccessor;
import com.finndog.justenoughstructures.server.ServerConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Containers in structure templates that a dev pointed at a different loot table. Each patch names
 * the template, the container's spot in it, the block that should be there and the table it had,
 * and is applied as the template loads rather than by editing the template's file. So it keeps
 * working when the structure mod updates its files, as long as that container is still there.
 *
 * <p>A patch that no longer fits its template is skipped and logged, never forced, and one that's
 * removed is kept in the file's list of removed patches. Nothing here can stop a template loading.
 */
public final class ContainerPatches {
    private static final String FILE = "containers.json";
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** Points the container at {@code pos} in {@code template}, a {@code block} with the table {@code original}, at {@code table}. */
    public record Patch(ResourceLocation template, BlockPos pos, ResourceLocation block, String original, ResourceLocation table) {
    }

    private static volatile Map<ResourceLocation, List<Patch>> byTemplate;

    private ContainerPatches() {
    }

    private static Map<ResourceLocation, List<Patch>> patches() {
        if (!ServerConfig.get().containerChanges()) {
            return Map.of();
        }
        Map<ResourceLocation, List<Patch>> loaded = byTemplate;
        if (loaded == null) {
            load();
            loaded = byTemplate;
        }
        return loaded;
    }

    /** Reads the patches again. Templates loaded from then on use them; loaded ones are dropped on /reload. */
    public static synchronized void load() {
        Map<ResourceLocation, List<Patch>> out = new HashMap<>();
        try {
            JsonObject json = read(file());
            for (JsonElement element : json.getAsJsonArray("patches")) {
                Patch patch = parse(element);
                if (patch != null) {
                    out.computeIfAbsent(patch.template(), id -> new ArrayList<>()).add(patch);
                }
            }
        } catch (IOException | RuntimeException e) {
            // Read on every /reload, so the same broken file is only worth one warning.
            Path file = file();
            JesLog.warnOnce("patches:" + file + "|" + e.getMessage(), "Couldn't read the container patches in {}, leaving containers as they are: {}",
                    file, e.toString());
            JesLog.debug("Couldn't read the container patches in {}", file, e);
        }
        byTemplate = out;
    }

    /**
     * Each patched template and its patches in use, as text that changes whenever they do, so the
     * loot index can tell which templates' containers changed.
     */
    public static Map<ResourceLocation, String> byTemplate() {
        Map<ResourceLocation, String> out = new TreeMap<>();
        patches().forEach((template, list) -> {
            List<String> lines = new ArrayList<>();
            list.forEach(patch -> lines.add(patch.pos().toShortString() + " " + patch.block() + " " + patch.table()));
            lines.sort(null);
            out.put(template, String.join("\n", lines));
        });
        return out;
    }

    /** Every patch saved, in the order they were saved, whether or not changed containers are in use. */
    public static synchronized List<Patch> all() {
        List<Patch> out = new ArrayList<>();
        try {
            for (JsonElement element : read(file()).getAsJsonArray("patches")) {
                Patch patch = parse(element);
                if (patch != null) {
                    out.add(patch);
                }
            }
        } catch (IOException | RuntimeException e) {
            JesLog.debug("Couldn't read the container patches in {}", file(), e);
        }
        return out;
    }

    public static Patch find(ResourceLocation template, BlockPos pos) {
        for (Patch patch : patches().getOrDefault(template, List.of())) {
            if (patch.pos().equals(pos)) {
                return patch;
            }
        }
        return null;
    }

    /** Changes the loot tables of patched containers in a template that's just been loaded. */
    public static void apply(ResourceLocation id, StructureTemplate template) {
        List<Patch> patches;
        try {
            patches = patches().get(id);
        } catch (RuntimeException e) {
            return;
        }
        if (patches == null) {
            return;
        }
        for (Patch patch : patches) {
            try {
                // Templates load again after every /reload, so each problem is only worth one warning.
                if (!applyOne(template, patch)) {
                    JesLog.warnOnce("patch:" + id + "@" + patch.pos().toShortString(),
                            "Not changing the container at {} in {}: there's no {} with a loot table there any more", patch.pos(), id, patch.block());
                }
            } catch (RuntimeException e) {
                JesLog.warnOnce("patch-error:" + id + "@" + patch.pos().toShortString(), "Couldn't change the container at {} in {}", patch.pos(), id, e);
            }
        }
    }

    private static boolean applyOne(StructureTemplate template, Patch patch) {
        boolean applied = false;
        for (StructureTemplate.Palette palette : ((StructureTemplateAccessor) template).justenoughstructures$palettes()) {
            for (StructureTemplate.StructureBlockInfo info : palette.blocks()) {
                if (!info.pos().equals(patch.pos()) || info.nbt() == null || !info.nbt().contains("LootTable", Tag.TAG_STRING)
                        || !BuiltInRegistries.BLOCK.getKey(info.state().getBlock()).equals(patch.block())) {
                    continue;
                }
                String now = info.nbt().getString("LootTable");
                if (!now.equals(patch.original()) && !now.equals(patch.table().toString())) {
                    JesLog.warnOnce("patch-changed:" + patch.template() + "@" + patch.pos().toShortString(),
                            "The container at {} in {} had its loot table changed by its mod, from {} to {}; using {} as set in the browser",
                            patch.pos(), patch.template(), patch.original(), now, patch.table());
                }
                info.nbt().putString("LootTable", patch.table().toString());
                applied = true;
            }
        }
        return applied;
    }

    /** Saves a patch, replacing any for the same container. It applies from the next /reload. */
    public static synchronized Component save(Patch patch) {
        try {
            JsonObject json = read(file());
            JsonArray patches = json.getAsJsonArray("patches");
            JsonArray kept = new JsonArray();
            for (JsonElement element : patches) {
                Patch existing = parse(element);
                if (existing == null || !existing.template().equals(patch.template()) || !existing.pos().equals(patch.pos())) {
                    kept.add(element);
                }
            }
            JsonObject entry = toJson(patch);
            entry.addProperty("saved", Instant.now().toString());
            kept.add(entry);
            json.add("patches", kept);
            write(json);
        } catch (IOException | RuntimeException e) {
            JustEnoughStructures.LOGGER.warn("Couldn't save the container patch for {} in {}: {}", patch.pos(), patch.template(), e.toString());
            JesLog.debug("Couldn't save the container patch for {} in {}", patch.pos(), patch.template(), e);
            return Component.translatable("screen.justenoughstructures.override.save_failed", String.valueOf(e.getMessage()));
        }
        load();
        return Component.translatable("screen.justenoughstructures.container.saved");
    }

    /** Stops patching a container. The patch moves to the file's list of removed ones rather than going. */
    public static synchronized Component remove(ResourceLocation template, BlockPos pos) {
        try {
            JsonObject json = read(file());
            JsonArray kept = new JsonArray();
            JsonArray removed = json.getAsJsonArray("removed");
            boolean found = false;
            for (JsonElement element : json.getAsJsonArray("patches")) {
                Patch existing = parse(element);
                if (existing != null && existing.template().equals(template) && existing.pos().equals(pos)) {
                    JsonObject gone = element.getAsJsonObject().deepCopy();
                    gone.addProperty("removed", Instant.now().toString());
                    removed.add(gone);
                    found = true;
                } else {
                    kept.add(element);
                }
            }
            if (!found) {
                return Component.translatable("screen.justenoughstructures.container.none");
            }
            json.add("patches", kept);
            write(json);
        } catch (IOException | RuntimeException e) {
            return Component.translatable("screen.justenoughstructures.override.save_failed", String.valueOf(e.getMessage()));
        }
        load();
        return Component.translatable("screen.justenoughstructures.container.removed");
    }

    private static Path file() {
        return LootOverrides.folder().resolve(FILE);
    }

    private static JsonObject read(Path file) throws IOException {
        JsonObject json = Files.exists(file) ? JsonParser.parseString(Files.readString(file)).getAsJsonObject() : new JsonObject();
        if (!json.has("patches") || !json.get("patches").isJsonArray()) {
            json.add("patches", new JsonArray());
        }
        if (!json.has("removed") || !json.get("removed").isJsonArray()) {
            json.add("removed", new JsonArray());
        }
        return json;
    }

    private static void write(JsonObject json) throws IOException {
        Path file = file();
        Files.createDirectories(file.getParent());
        Files.writeString(file, PRETTY.toJson(json));
    }

    private static JsonObject toJson(Patch patch) {
        JsonObject entry = new JsonObject();
        entry.addProperty("template", patch.template().toString());
        JsonArray pos = new JsonArray();
        pos.add(patch.pos().getX());
        pos.add(patch.pos().getY());
        pos.add(patch.pos().getZ());
        entry.add("pos", pos);
        entry.addProperty("block", patch.block().toString());
        entry.addProperty("original", patch.original());
        entry.addProperty("table", patch.table().toString());
        return entry;
    }

    private static Patch parse(JsonElement element) {
        try {
            JsonObject entry = element.getAsJsonObject();
            JsonArray pos = entry.getAsJsonArray("pos");
            ResourceLocation template = ResourceLocation.tryParse(entry.get("template").getAsString());
            ResourceLocation block = ResourceLocation.tryParse(entry.get("block").getAsString());
            ResourceLocation table = ResourceLocation.tryParse(entry.get("table").getAsString());
            if (template == null || block == null || table == null || pos.size() != 3) {
                return null;
            }
            return new Patch(template, new BlockPos(pos.get(0).getAsInt(), pos.get(1).getAsInt(), pos.get(2).getAsInt()), block,
                    entry.has("original") ? entry.get("original").getAsString() : "", table);
        } catch (RuntimeException e) {
            return null;
        }
    }
}
