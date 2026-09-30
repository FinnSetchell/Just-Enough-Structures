package com.finndog.justenoughstructures.overrides;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.google.common.hash.Hashing;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * Loot tables edited in the browser. Each edit is saved as an override in a datapack of its own,
 * in the config folder so it travels with a modpack, and applies from the next /reload.
 *
 * <p>Nothing here ever deletes a dev's work or lets a bad edit break loot. An edit the game can't
 * load is refused when saving, and one broken later by hand is left out of the pack, so the mod's
 * own table stays in use. Each override remembers the table it was made from, so if the mod
 * changes or drops that table it's flagged rather than silently kept or thrown away.
 */
public final class LootOverrides {
    public static final String PACK_ID = "justenoughstructures/loot_overrides";
    /** The datapack format for 1.20.1. */
    private static final int PACK_FORMAT = 15;
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String META = "overrides.json";
    /** What the metadata remembers for an override of a table no mod had: nothing to compare with. */
    private static final String NO_ORIGINAL = "none";

    private static Path folder;

    private LootOverrides() {
    }

    /** Where overrides are, and what's become of an override compared to the table it replaces. */
    public enum Status {
        /** Not overridden. */
        NONE,
        /** Overridden, and the mod's table is still the one it was made from. */
        ACTIVE,
        /** The mod's table has changed since the override was made. */
        ORIGINAL_CHANGED,
        /** The mod no longer has the table the override was made from, so only the override defines it. */
        ORIGINAL_MISSING,
        /** The override can't be loaded, so it's left out and the mod's table is used. */
        BROKEN
    }

    /**
     * A table as the editor sees it. {@code current} is the override if there is one, otherwise
     * the table as the mod or datapacks have it. {@code original} is the table without the override,
     * or null if there's none. {@code base} is the original the override was made from, for merging
     * in what the mod has changed since, or null.
     */
    public record View(ResourceLocation id, String current, String original, String base, Status status) {
    }

    public static Path folder() {
        return folder != null ? folder : JustEnoughStructures.configDir().resolve("loot_overrides");
    }

    /** Points overrides somewhere else, for tests. Null goes back to the config folder. */
    public static void setFolder(Path dir) {
        folder = dir;
    }

    static Path file(Path root, ResourceLocation id) {
        return root.resolve("data").resolve(id.getNamespace()).resolve("loot_tables").resolve(id.getPath() + ".json");
    }

    public static View view(ResourceManager resources, ResourceLocation id) {
        String original = original(resources, id);
        Path root = folder();
        Path file = file(root, id);
        if (!Files.exists(file)) {
            return new View(id, original, original, null, Status.NONE);
        }
        String override;
        try {
            override = Files.readString(file);
        } catch (IOException e) {
            return new View(id, original, original, null, Status.BROKEN);
        }
        String base = base(id);
        Status status;
        if (check(id, override) != null) {
            status = Status.BROKEN;
        } else if (original == null) {
            // A table a dev made from scratch has no original, and that's fine.
            status = NO_ORIGINAL.equals(base) ? Status.ACTIVE : Status.ORIGINAL_MISSING;
        } else if (!hash(original).equals(base)) {
            status = Status.ORIGINAL_CHANGED;
        } else {
            status = Status.ACTIVE;
        }
        return new View(id, override, original, baseText(root, id), status);
    }

    /** The table as the mods and datapacks have it, leaving out any override, or null if none of them has it. */
    public static String original(ResourceManager resources, ResourceLocation id) {
        ResourceLocation location = new ResourceLocation(id.getNamespace(), "loot_tables/" + id.getPath() + ".json");
        List<Resource> stack = resources.getResourceStack(location);
        // Lowest priority first, so the last one that isn't ours is what would be used without us.
        for (int i = stack.size() - 1; i >= 0; i--) {
            Resource resource = stack.get(i);
            if (PACK_ID.equals(resource.sourcePackId())) {
                continue;
            }
            try (InputStream in = resource.open()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                JustEnoughStructures.LOGGER.warn("Couldn't read the loot table {} from {}", id, resource.sourcePackId(), e);
                return null;
            }
        }
        return null;
    }

    /** What's wrong with a draft, or null if the game can load it as a loot table. */
    public static Component check(ResourceLocation id, String json) {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(json);
        } catch (JsonParseException e) {
            return Component.translatable("screen.justenoughstructures.override.invalid_json", message(e));
        }
        try {
            LootTable table = LootDataType.TABLE.parser().fromJson(parsed, LootTable.class);
            return table == null ? Component.translatable("screen.justenoughstructures.override.invalid_table", "") : null;
        } catch (RuntimeException e) {
            return Component.translatable("screen.justenoughstructures.override.invalid_table", message(e));
        }
    }

    /** A draft as a loot table, or null if it isn't one. For rolling it before it's saved. */
    public static LootTable parse(String json) {
        try {
            return LootDataType.TABLE.parser().fromJson(JsonParser.parseString(json), LootTable.class);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** Saves an edit, remembering the table it was made from. It applies after /reload. */
    public static Component save(ResourceManager resources, ResourceLocation id, String json) {
        Component problem = check(id, json);
        if (problem != null) {
            return problem;
        }
        Path root = folder();
        Path file = file(root, id);
        try {
            ensurePack(root);
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(temp, PRETTY.toJson(JsonParser.parseString(json)));
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            String original = original(resources, id);
            JsonObject meta = readMeta(root);
            JsonObject entry = new JsonObject();
            entry.addProperty("base", original == null ? NO_ORIGINAL : hash(original));
            entry.addProperty("saved", Instant.now().toString());
            meta.add(id.toString(), entry);
            writeMeta(root, meta);
            writeBaseText(root, id, original);
        } catch (IOException | RuntimeException e) {
            JustEnoughStructures.LOGGER.warn("Couldn't save the loot override for {}", id, e);
            return Component.translatable("screen.justenoughstructures.override.save_failed", message(e));
        }
        return Component.translatable("screen.justenoughstructures.override.saved");
    }

    /** Keeps an override as it is after its original changed, and stops flagging it until it changes again. */
    public static Component keep(ResourceManager resources, ResourceLocation id) {
        Path root = folder();
        if (!Files.exists(file(root, id))) {
            return Component.translatable("screen.justenoughstructures.override.none");
        }
        String original = original(resources, id);
        try {
            JsonObject meta = readMeta(root);
            JsonObject entry = meta.has(id.toString()) && meta.get(id.toString()).isJsonObject()
                    ? meta.getAsJsonObject(id.toString()) : new JsonObject();
            entry.addProperty("base", original == null ? NO_ORIGINAL : hash(original));
            meta.add(id.toString(), entry);
            writeMeta(root, meta);
            writeBaseText(root, id, original);
        } catch (IOException | RuntimeException e) {
            return Component.translatable("screen.justenoughstructures.override.save_failed", message(e));
        }
        return Component.translatable("screen.justenoughstructures.override.kept");
    }

    /**
     * Turns an override off. The file is moved aside into a dated folder, never deleted, so an
     * override removed by mistake can always be put back.
     */
    public static Component remove(ResourceLocation id) {
        Path root = folder();
        Path file = file(root, id);
        if (!Files.exists(file)) {
            return Component.translatable("screen.justenoughstructures.override.none");
        }
        Path kept = root.resolve("removed").resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")))
                .resolve(id.getNamespace()).resolve(id.getPath() + ".json");
        try {
            Files.createDirectories(kept.getParent());
            Files.move(file, kept, StandardCopyOption.REPLACE_EXISTING);
            JsonObject meta = readMeta(root);
            meta.remove(id.toString());
            writeMeta(root, meta);
        } catch (IOException | RuntimeException e) {
            return Component.translatable("screen.justenoughstructures.override.save_failed", message(e));
        }
        return Component.translatable("screen.justenoughstructures.override.removed", root.relativize(kept).toString().replace('\\', '/'));
    }

    /** Writes pack.mcmeta, which makes the folder a datapack, if it isn't there yet. */
    static void ensurePack(Path root) throws IOException {
        Path mcmeta = root.resolve("pack.mcmeta");
        if (Files.exists(mcmeta)) {
            return;
        }
        Files.createDirectories(root);
        JsonObject pack = new JsonObject();
        pack.addProperty("pack_format", PACK_FORMAT);
        pack.addProperty("description", "Loot tables edited in Just Enough Structures");
        JsonObject json = new JsonObject();
        json.add("pack", pack);
        Files.writeString(mcmeta, PRETTY.toJson(json));
    }

    private static String base(ResourceLocation id) {
        try {
            JsonObject meta = readMeta(folder());
            JsonElement entry = meta.get(id.toString());
            return entry != null && entry.isJsonObject() && entry.getAsJsonObject().has("base")
                    ? entry.getAsJsonObject().get("base").getAsString() : null;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    /**
     * A copy of the original an override was made from, kept next to it. Merging what the mod changed
     * since needs to know what the table was then, not only that it's different now.
     */
    private static Path baseFile(Path root, ResourceLocation id) {
        return root.resolve("originals").resolve(id.getNamespace()).resolve(id.getPath() + ".json");
    }

    private static void writeBaseText(Path root, ResourceLocation id, String original) throws IOException {
        Path file = baseFile(root, id);
        if (original == null) {
            Files.deleteIfExists(file);
            return;
        }
        Files.createDirectories(file.getParent());
        Files.writeString(file, original);
    }

    private static String baseText(Path root, ResourceLocation id) {
        Path file = baseFile(root, id);
        try {
            return Files.exists(file) ? Files.readString(file) : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static JsonObject readMeta(Path root) throws IOException {
        Path file = root.resolve(META);
        if (!Files.exists(file)) {
            return new JsonObject();
        }
        JsonElement json = JsonParser.parseString(Files.readString(file));
        return json.isJsonObject() ? json.getAsJsonObject() : new JsonObject();
    }

    private static void writeMeta(Path root, JsonObject meta) throws IOException {
        Files.createDirectories(root);
        Files.writeString(root.resolve(META), PRETTY.toJson(meta));
    }

    /** A fingerprint of a table that ignores spacing, so reformatting a file isn't taken as a change. */
    static String hash(String json) {
        String normalised;
        try {
            normalised = JsonParser.parseString(json).toString();
        } catch (JsonParseException e) {
            normalised = json;
        }
        return Hashing.sha256().hashString(normalised, StandardCharsets.UTF_8).toString();
    }

    private static String message(Exception e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : message;
    }
}
