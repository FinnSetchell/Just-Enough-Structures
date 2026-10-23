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
import com.google.gson.JsonArray;
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
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
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
 * Loot tables edited in the browser. Each edit is saved as an override in a separate datapack in
 * the config folder, so it travels with a modpack, and applies from the next /reload.
 *
 * <p>An edit the game can't load is refused when saving, and one broken later by hand is left out of
 * the pack, so the mod's table stays in use. Each override remembers the table it was made from, so
 * if the mod changes or drops that table, the override is flagged instead of being silently kept or
 * thrown away.
 */
public final class LootOverrides {
    public static final String PACK_ID = "justenoughstructures/loot_overrides";
    /** The datapack format for 1.20.1. */
    private static final int PACK_FORMAT = 15;
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final String META = "overrides.json";
    /** The base recorded for an override of a table no mod had, so there's nothing to compare with. */
    private static final String NO_ORIGINAL = "none";

    private static Path folder;

    private LootOverrides() {
    }

    /** An override's state compared with the table it replaces. */
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
     * Returns {@code file} after checking it is inside {@code root}. Ids come from player requests, and
     * one that isn't {@link Ids#fileSafe} could otherwise point at any file on the server.
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
            // A table made from scratch has no original, which is expected.
            status = NO_ORIGINAL.equals(base) ? Status.ACTIVE : Status.ORIGINAL_MISSING;
        } else if (!hash(original).equals(base)) {
            status = Status.ORIGINAL_CHANGED;
        } else {
            status = Status.ACTIVE;
        }
        return new View(id, override, original, baseText(root, id), status);
    }

    /** Every table with an override and its status, so the browser can mark edited tables. */
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
        // Lowest priority first, so the last one not from the override pack is what the game would use without it.
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
     * The same. Set {@code beforeWorld} while a world is still loading. From 1.21, reading a table needs
     * registries, and before a world loads only the vanilla ones exist. So the table is then read with
     * lenient vanilla registries: anything else it names, like a mod's enchantment or a datapack's
     * trim, is assumed to exist, as it will once the world has loaded.
     */
    public static Component check(ResourceLocation id, String json, boolean beforeWorld) {
        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(json);
        } catch (JsonParseException e) {
            return jsonProblem(e);
        }
        String problem = problem(id, parsed, beforeWorld);
        return problem == null ? null : located(id, parsed, beforeWorld, problem);
    }

    /** Why the game can't load this as a loot table, or null if it can. */
    private static String problem(ResourceLocation id, JsonElement json, boolean beforeWorld) {
        try {
            return fromJson(id, json, beforeWorld) == null ? "" : null;
        } catch (RuntimeException e) {
            return message(e);
        }
    }

    /**
     * Narrows a load error to the smallest part that still fails on its own: a pool, then an entry,
     * then a child entry. Returns that part's error.
     */
    private static Component located(ResourceLocation id, JsonElement json, boolean beforeWorld, String whole) {
        // If it fails even with no pools, the fault is in the table's other fields, and there's no part to name.
        if (json instanceof JsonObject table && table.get("pools") instanceof JsonArray pools
                && problem(id, with(table, "pools", new JsonArray()), beforeWorld) == null) {
            for (int p = 0; p < pools.size(); p++) {
                String inPool = problem(id, with(table, "pools", only(pools.get(p))), beforeWorld);
                if (inPool == null) {
                    continue;
                }
                // If the pool fails even with no entries, the fault is in its other fields.
                if (pools.get(p) instanceof JsonObject pool && pool.get("entries") instanceof JsonArray entries
                        && problem(id, with(table, "pools", only(with(pool, "entries", new JsonArray()))), beforeWorld) == null) {
                    for (int e = 0; e < entries.size(); e++) {
                        Function<JsonElement, String> alone = entry -> problem(id, with(table, "pools", only(with(pool, "entries", only(entry)))), beforeWorld);
                        String inEntry = alone.apply(entries.get(e));
                        if (inEntry != null) {
                            StringBuilder child = new StringBuilder();
                            String reason = deepest(entries.get(e), alone, child, inEntry);
                            return Component.translatable("screen.justenoughstructures.override.invalid_entry" + (child.length() == 0 ? "" : "_inside"),
                                    e + 1, p + 1, reason, child.toString());
                        }
                    }
                }
                return Component.translatable("screen.justenoughstructures.override.invalid_pool", p + 1, inPool);
            }
        }
        return Component.translatable("screen.justenoughstructures.override.invalid_table", whole);
    }

    /**
     * Follows an entry's error down into its children while a single child still reproduces it.
     * {@code alone} returns the table's error with a given entry in place. Appends the path to
     * {@code child} and returns the deepest error.
     */
    private static String deepest(JsonElement entry, Function<JsonElement, String> alone, StringBuilder child, String reason) {
        if (!(entry instanceof JsonObject holder) || !(holder.get("children") instanceof JsonArray children)
                || alone.apply(with(holder, "children", new JsonArray())) != null) {
            return reason;
        }
        for (int c = 0; c < children.size(); c++) {
            Function<JsonElement, String> inside = one -> alone.apply(with(holder, "children", only(one)));
            String inChild = inside.apply(children.get(c));
            if (inChild != null) {
                child.append(child.length() == 0 ? "" : ".").append("children.").append(c);
                return deepest(children.get(c), inside, child, inChild);
            }
        }
        return reason;
    }

    /** A copy of an object with one of its values swapped. What's in it is shared, not copied. */
    private static JsonObject with(JsonObject object, String key, JsonElement value) {
        JsonObject copy = new JsonObject();
        object.entrySet().forEach(e -> copy.add(e.getKey(), e.getKey().equals(key) ? value : e.getValue()));
        return copy;
    }

    private static JsonArray only(JsonElement element) {
        JsonArray array = new JsonArray();
        array.add(element);
        return array;
    }

    /**
     * The part of a table a problem from {@link #check} is about: a pool, counted from 0, an entry in
     * it or -1 for the pool itself, and for an entry inside another the path down to it from that
     * entry, like "children.1". The problem's text is what the player reads; this is for highlighting
     * the part in the editor.
     */
    public record Fault(int pool, int entry, String child) {
        /** The part a problem names, or null if it names none. */
        public static Fault of(Component problem) {
            if (problem == null || !(problem.getContents() instanceof TranslatableContents said)) {
                return null;
            }
            String key = said.getKey();
            Object[] args = said.getArgs();
            try {
                if (key.endsWith("override.invalid_pool") && args.length >= 1) {
                    return new Fault(number(args[0]) - 1, -1, "");
                }
                if ((key.endsWith("override.invalid_entry") || key.endsWith("override.invalid_entry_inside")) && args.length >= 2) {
                    return new Fault(number(args[1]) - 1, number(args[0]) - 1, args.length >= 4 ? said(args[3]) : "");
                }
            } catch (NumberFormatException e) {
                // Not a JES problem message after all.
            }
            return null;
        }

        // An arg is the number as given, or a text component once the message has been sent to a player.
        private static int number(Object arg) {
            return Integer.parseInt(said(arg).trim());
        }

        private static String said(Object arg) {
            return arg instanceof Component component ? component.getString() : String.valueOf(arg);
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

    // Vanilla registries that accept any missing entry or tag. See check(id, json, beforeWorld).
    private static synchronized HolderLookup.Provider lenientRegistries() {
        if (lenient == null) {
            HolderLookup.Provider vanilla = VanillaRegistries.createLookup();
            lenient = HolderLookup.Provider.create(vanilla.listRegistryKeys().<HolderLookup.RegistryLookup<?>>map(key -> lenient(vanilla.lookup(key).orElseThrow())));
        }
        return lenient;
    }
    *///?} else if >=1.21 {
    /*private static HolderLookup.Provider lenient;

    // Vanilla registries that accept any missing entry or tag. See check(id, json, beforeWorld).
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

    // Registry view that returns a stand-alone holder for a missing entry, and an empty set for a missing tag.
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

    //? if >=26.3 {
    /*// From 26.3 a table refers to other tables, predicates and item modifiers as registry entries, not
    // plain ids, so reading a table needs those registries too: the server's once a world has loaded.
    // A missing one is assumed to exist, since it may be a table saved here that the next reload loads.
    private static HolderLookup.Provider withLoot(HolderLookup.Provider registries, MinecraftServer server) {
        HolderLookup.Provider loaded = server == null ? null : server.reloadableRegistries().lookup();
        java.util.List<HolderLookup.RegistryLookup<?>> all = new java.util.ArrayList<>();
        registries.listRegistries().filter(registry -> !LOOT_REGISTRIES.contains(registry.key())).forEach(all::add);
        all.add(loot(loaded, net.minecraft.core.registries.Registries.LOOT_TABLE));
        all.add(loot(loaded, net.minecraft.core.registries.Registries.ITEM_MODIFIER));
        all.add(loot(loaded, net.minecraft.core.registries.Registries.PREDICATE));
        return HolderLookup.Provider.create(all.stream());
    }

    private static final java.util.Set<ResourceKey<?>> LOOT_REGISTRIES = java.util.Set.of(net.minecraft.core.registries.Registries.LOOT_TABLE,
            net.minecraft.core.registries.Registries.ITEM_MODIFIER, net.minecraft.core.registries.Registries.PREDICATE);

    private static <T> HolderLookup.RegistryLookup<T> loot(HolderLookup.Provider loaded, ResourceKey<net.minecraft.core.Registry<T>> key) {
        HolderLookup.RegistryLookup<T> there = loaded == null ? null : loaded.<T>lookup(key).orElse(null);
        return lenient(there != null ? there : new Empty<T>(key));
    }

    // An empty registry, standing in for the server's before a world has loaded.
    private record Empty<T>(ResourceKey<? extends net.minecraft.core.Registry<? extends T>> key) implements HolderLookup.RegistryLookup<T> {
        @Override
        public com.mojang.serialization.Lifecycle registryLifecycle() {
            return com.mojang.serialization.Lifecycle.stable();
        }

        @Override
        public Optional<Holder.Reference<T>> get(ResourceKey<T> id) {
            return Optional.empty();
        }

        @Override
        public Optional<HolderSet.Named<T>> get(TagKey<T> tag) {
            return Optional.empty();
        }

        @Override
        public Stream<Holder.Reference<T>> listElements() {
            return Stream.empty();
        }

        @Override
        public Stream<HolderSet.Named<T>> listTags() {
            return Stream.empty();
        }
    }
    *///?} else if >=1.21 {
    /*private static HolderLookup.Provider withLoot(HolderLookup.Provider registries, MinecraftServer server) {
        return registries;
    }
    *///?}

    /** Reads a loot table the way the game reads one from a datapack. */
    private static LootTable fromJson(ResourceLocation id, JsonElement json, boolean beforeWorld) {
        //? if >=1.21 {
        /*// Use the server's registries, or the lenient vanilla ones before a world loads.
        MinecraftServer server = JesServer.running();
        HolderLookup.Provider registries = beforeWorld || server == null ? lenientRegistries() : server.registryAccess();
        HolderLookup.Provider all = withLoot(registries, beforeWorld ? null : server);
        return named(LootTable.DIRECT_CODEC.parse(RegistryOps.create(JsonOps.INSTANCE, all), json).getOrThrow(JsonParseException::new), id);
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
            // Write the metadata and original first, so an unreadable metadata file stops the save
            // before the table changes. The table is written last.
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
     * Describes where the JSON goes wrong and what's likely missing, instead of the parser's message,
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
