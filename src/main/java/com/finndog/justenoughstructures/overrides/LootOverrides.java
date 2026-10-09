package com.finndog.justenoughstructures.overrides;

import com.finndog.justenoughstructures.FileFormat;
import com.finndog.justenoughstructures.Folders;
import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.SafeFiles;
import com.finndog.justenoughstructures.loot.LootRolls;
import com.finndog.justenoughstructures.server.JesServer;
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
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.level.storage.loot.LootDataType;
import net.minecraft.world.level.storage.loot.LootTable;
//? if >=1.21 {
/*import com.mojang.serialization.JsonOps;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderOwner;
import net.minecraft.core.HolderSet;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
*///?}

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
        return inside(root, root.resolve("data").resolve(id.getNamespace()).resolve(Folders.LOOT_TABLES).resolve(id.getPath() + ".json"));
    }

    /**
     * The file, after making sure it's in the folder. Ids come from players' requests, and only one
     * that isn't {@link Ids#fileSafe} could take it out, to any file on the server.
     */
    private static Path inside(Path root, Path file) {
        if (!file.normalize().startsWith(root.normalize())) {
            throw new IllegalArgumentException(file + " isn't in " + root);
        }
        return file;
    }

    public static View view(ResourceManager resources, ResourceLocation id) {
        if (!Ids.fileSafe(id)) {
            return new View(id, null, null, null, Status.NONE);
        }
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

    /** Every table with an override here, and how each stands, for the browser to mark the ones that are edited. */
    public static Map<ResourceLocation, Status> statuses(ResourceManager resources) {
        Map<ResourceLocation, Status> out = new TreeMap<>();
        Path data = folder().resolve("data");
        if (!Files.isDirectory(data)) {
            return out;
        }
        try (Stream<Path> files = Files.walk(data)) {
            files.filter(f -> f.toString().endsWith(".json")).forEach(f -> {
                // data/<namespace>/loot_tables/<path>.json, or loot_table from 1.21
                Path relative = data.relativize(f);
                if (relative.getNameCount() < 3 || !relative.getName(1).toString().equals(Folders.LOOT_TABLES)) {
                    return;
                }
                String path = relative.subpath(2, relative.getNameCount()).toString().replace('\\', '/');
                ResourceLocation id = ResourceLocation.tryParse(relative.getName(0) + ":" + path.substring(0, path.length() - 5));
                if (id != null) {
                    out.put(id, view(resources, id).status());
                }
            });
        } catch (IOException | RuntimeException e) {
            JesLog.debug("Couldn't list the loot overrides in {}", data, e);
        }
        return out;
    }

    /** The table as the mods and datapacks have it, leaving out any override, or null if none of them has it. */
    public static String original(ResourceManager resources, ResourceLocation id) {
        ResourceLocation location = Ids.of(id.getNamespace(), Folders.LOOT_TABLES + "/" + id.getPath() + ".json");
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
                JesLog.debug("Couldn't read the loot table {} from {}", id, resource.sourcePackId(), e);
                return null;
            }
        }
        return null;
    }

    /** Whether there's a loot table by that name, loaded or saved here since the last /reload. */
    public static boolean exists(MinecraftServer server, ResourceLocation id) {
        return LootRolls.table(server, id) != LootTable.EMPTY || Ids.fileSafe(id) && Files.exists(file(folder(), id));
    }

    /** What's wrong with a draft, or null if the game can load it as a loot table. */
    public static Component check(ResourceLocation id, String json) {
        return check(id, json, JesServer.running() == null);
    }

    /**
     * The same, with {@code beforeWorld} while a world is still loading and only the game's own
     * registries are there to read a table with: anything else it names, like a mod's enchantment or
     * a datapack's trim, is taken as there, as it will be once the world has loaded.
     */
    public static Component check(ResourceLocation id, String json, boolean beforeWorld) {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(json);
        } catch (JsonParseException e) {
            return jsonProblem(e);
        }
        try {
            LootTable table = fromJson(id, parsed, beforeWorld);
            return table == null ? Component.translatable("screen.justenoughstructures.override.invalid_table", "") : null;
        } catch (RuntimeException e) {
            return Component.translatable("screen.justenoughstructures.override.invalid_table", message(e));
        }
    }

    /** A draft as a loot table, or null if it isn't one. For rolling it before it's saved. */
    public static LootTable parse(String json) {
        try {
            return fromJson(JustEnoughStructures.id("draft"), JsonParser.parseString(json), JesServer.running() == null);
        } catch (RuntimeException e) {
            return null;
        }
    }

    //? if >=26.1 {
    /*private static HolderLookup.Provider lenient;

    // The game's own registries, where an entry or tag they don't have is taken as there: before a
    // world has loaded, what its mods and datapacks add to them isn't known.
    private static synchronized HolderLookup.Provider lenientRegistries() {
        if (lenient == null) {
            HolderLookup.Provider vanilla = VanillaRegistries.createLookup();
            lenient = HolderLookup.Provider.create(vanilla.listRegistryKeys().<HolderLookup.RegistryLookup<?>>map(key -> lenient(vanilla.lookup(key).orElseThrow())));
        }
        return lenient;
    }
    *///?} else if >=1.21 {
    /*private static HolderLookup.Provider lenient;

    // The game's own registries, where an entry or tag they don't have is taken as there: before a
    // world has loaded, what its mods and datapacks add to them isn't known.
    private static synchronized HolderLookup.Provider lenientRegistries() {
        if (lenient == null) {
            HolderLookup.Provider vanilla = VanillaRegistries.createLookup();
            lenient = HolderLookup.Provider.create(vanilla.listRegistries().<HolderLookup.RegistryLookup<?>>map(key -> lenient(vanilla.lookup(key).orElseThrow())));
        }
        return lenient;
    }
    *///?}

    //? if >=1.21 {
    /*private static <T> HolderLookup.RegistryLookup<T> lenient(HolderLookup.RegistryLookup<T> registry) {
        return new Lenient<>(registry, new HolderOwner<>() {
        });
    }

    // A registry that takes an entry or tag it doesn't have as there, standing alone.
    private record Lenient<T>(HolderLookup.RegistryLookup<T> parent, HolderOwner<T> owner) implements HolderLookup.RegistryLookup.Delegate<T> {
        @Override
        public Optional<Holder.Reference<T>> get(ResourceKey<T> key) {
            return Optional.of(parent.get(key).orElseGet(() -> Holder.Reference.createStandAlone(owner, key)));
        }

        @Override
        public Optional<HolderSet.Named<T>> get(TagKey<T> tag) {
            return Optional.of(parent.get(tag).orElseGet(() -> HolderSet.emptyNamed(owner, tag)));
        }
    }
    *///?}

    /** Reads a loot table the way the game reads one from a datapack. */
    private static LootTable fromJson(ResourceLocation id, JsonElement json, boolean beforeWorld) {
        //? if >=1.21 {
        /*// Read with the running server's registries, or before a world has loaded, the game's own,
        // taking whatever else a table names as there, as a mod's enchantment will be by then.
        MinecraftServer server = JesServer.running();
        HolderLookup.Provider registries = beforeWorld || server == null ? lenientRegistries() : server.registryAccess();
        return named(LootTable.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, registries), json).getOrThrow(JsonParseException::new), id);
        *///?} else if forge {
        /*// Forge names each pool as a table is read, which only works inside its own loading.
        return net.minecraftforge.common.ForgeHooks.loadLootTable(LootDataType.TABLE.parser(), id, json, true);
        *///?} else {
        return LootDataType.TABLE.parser().fromJson(json, LootTable.class);
        //?}
    }

    //? if neoforge {
    /*// NeoForge looks a table's id up as it rolls it, for its loot modifiers, and from 26.3 fails without one.
    private static LootTable named(LootTable table, ResourceLocation id) {
        table.setLootTableId(id);
        return table;
    }
    *///?} else if >=1.21 {
    /*private static LootTable named(LootTable table, ResourceLocation id) {
        return table;
    }
    *///?}

    /** Saves an edit, remembering the table it was made from. It applies after /reload. */
    public static Component save(ResourceManager resources, ResourceLocation id, String json) {
        if (!Ids.fileSafe(id)) {
            return Component.translatable("screen.justenoughstructures.override.bad_id", id.toString());
        }
        Component problem = check(id, json);
        if (problem != null) {
            return problem;
        }
        Path root = folder();
        Path file = file(root, id);
        try {
            ensurePack(root);
            // What it was made from goes down first, so a record that can't be read stops the save
            // before the table changes, and the table is only put in place once the rest is there.
            String original = original(resources, id);
            JsonObject meta = readMeta(root);
            JsonObject entry = new JsonObject();
            entry.addProperty("base", original == null ? NO_ORIGINAL : hash(original));
            entry.addProperty("saved", Instant.now().toString());
            meta.add(id.toString(), entry);
            writeBaseText(root, id, original);
            writeMeta(root, meta);
            Files.createDirectories(file.getParent());
            SafeFiles.write(file, PRETTY.toJson(JsonParser.parseString(json)));
        } catch (IOException | RuntimeException e) {
            JustEnoughStructures.LOGGER.warn("Couldn't save the loot override for {}: {}", id, e.toString());
            JesLog.debug("Couldn't save the loot override for {}", id, e);
            return Component.translatable("screen.justenoughstructures.override.save_failed", message(e));
        }
        return Component.translatable("screen.justenoughstructures.override.saved");
    }

    /** Keeps an override as it is after its original changed, and stops flagging it until it changes again. */
    public static Component keep(ResourceManager resources, ResourceLocation id) {
        Path root = folder();
        if (!Ids.fileSafe(id) || !Files.exists(file(root, id))) {
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
        if (!Ids.fileSafe(id)) {
            return Component.translatable("screen.justenoughstructures.override.none");
        }
        Path file = file(root, id);
        if (!Files.exists(file)) {
            return Component.translatable("screen.justenoughstructures.override.none");
        }
        Path kept = inside(root, root.resolve("removed").resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")))
                .resolve(id.getNamespace()).resolve(id.getPath() + ".json"));
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
        SafeFiles.write(mcmeta, PRETTY.toJson(json));
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
        return inside(root, root.resolve("originals").resolve(id.getNamespace()).resolve(id.getPath() + ".json"));
    }

    private static void writeBaseText(Path root, ResourceLocation id, String original) throws IOException {
        Path file = baseFile(root, id);
        if (original == null) {
            Files.deleteIfExists(file);
            return;
        }
        Files.createDirectories(file.getParent());
        SafeFiles.write(file, original);
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
        JsonObject meta = json.isJsonObject() ? json.getAsJsonObject() : new JsonObject();
        FileFormat.check(meta, file);
        return meta;
    }

    private static void writeMeta(Path root, JsonObject meta) throws IOException {
        Files.createDirectories(root);
        SafeFiles.write(root.resolve(META), PRETTY.toJson(FileFormat.stamped(meta)));
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

    private static final Pattern WHERE = Pattern.compile("line (\\d+) column (\\d+)");

    /**
     * Where the JSON goes wrong and what's likely missing, in place of the parser's own message,
     * which reads like a Java error. The line and column count from the top of the text as typed.
     */
    static Component jsonProblem(JsonParseException e) {
        String text = String.valueOf(e.getMessage());
        String reason;
        if (text.contains("EOFException") || text.contains("End of input")) {
            reason = "ends_early";
        } else if (text.contains("Unterminated")) {
            reason = "unterminated";
        } else if (text.contains("Expected ':'")) {
            reason = "colon";
        } else if (text.contains("Expected name")) {
            reason = "name";
        } else if (text.contains("Expected value")) {
            reason = "value";
        } else {
            reason = "other";
        }
        Component why = Component.translatable("screen.justenoughstructures.json." + reason);
        Matcher where = WHERE.matcher(text);
        return where.find()
                ? Component.translatable("screen.justenoughstructures.override.invalid_json_at", where.group(1), where.group(2), why)
                : Component.translatable("screen.justenoughstructures.override.invalid_json", why);
    }

    private static String message(Exception e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : message;
    }
}
