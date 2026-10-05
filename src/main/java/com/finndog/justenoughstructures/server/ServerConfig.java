package com.finndog.justenoughstructures.server;

import com.finndog.justenoughstructures.FileFormat;
import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.helpers.MessageFormatter;

/**
 * The server owner's settings, from {@code config/justenoughstructures/server.json5}: structures to
 * keep out of the browser, who can find and teleport to them, and who can use Pack tools. Read when
 * the server starts and again on /reload.
 */
public final class ServerConfig {
    public static final String FILE_NAME = "server.json5";

    private static final Gson GSON = new Gson();
    private static final String TEMPLATE = """
            // Just Enough Structures server settings. Changes apply after /reload or a restart.
            {
              // Which version of this file's layout it is, for Just Enough Structures itself. Leave it as it is.
              "format": 1,

              // Structures to leave out of the browser. They can't be previewed or located and their
              // loot isn't listed, so nothing gives them away. Use a structure's id, or "modid:*" for
              // everything from one mod. For example: ["minecraft:ancient_city", "somemod:*"]
              "hidden": %s,

              // Who can use the locate button, as a vanilla permission level: 0 is everyone, 2 is
              // operators and singleplayer with cheats on (the same as /locate), 4 is the server owner.
              "locate_permission": %s,

              // Who can Ctrl-click it to teleport there. 2 is the same as /tp.
              "teleport_permission": %s,

              // False keeps where every structure's loot is out of the browser: no chest markers and
              // no opening chests in the preview. What the loot can be is still listed. A structure's
              // own datapack file can hide just its loot instead.
              "show_loot_locations": %s,

              // Who can use Pack tools on this server: editing loot tables and chests, hiding
              // structures and these settings. Anyone a permissions mod gives the
              // justenoughstructures.pack_tools permission can, and so can these. In singleplayer,
              // having cheats on is enough.
              "pack_tools": {
                // By name. Matched by UUID once they have joined, so a rename doesn't lock anyone out.
                "players": %s,
                // A permission level that also gets it: -1 for none, or 0 to 4. /op gives level 4
                // unless op-permission-level in server.properties says otherwise, so any level here
                // lets every operator in.
                "permission_level": %s
              },

              // False stops using the containers pointed at other loot tables and the spawners given
              // other mobs in the browser, so every structure is as its mod made it. They're kept in
              // loot_overrides/containers.json and spawners.json, and used again when this is true.
              "container_changes": %s
            }
            """;

    /** What {@link #get()} returns until a file is read, and whatever a file leaves out. */
    public static final Settings DEFAULTS = new Settings(Set.of(), Set.of(), 2, 2, true, PackTools.NONE, true);

    private static volatile Settings current = DEFAULTS;

    private ServerConfig() {
    }

    /**
     * @param hiddenStructures single structures that are hidden
     * @param hiddenMods       namespaces whose every structure is hidden
     */
    public record Settings(Set<ResourceLocation> hiddenStructures, Set<String> hiddenMods, int locatePermission, int teleportPermission,
                           boolean showLootLocations, PackTools packTools, boolean containerChanges) {
        public Settings(Set<ResourceLocation> hiddenStructures, Set<String> hiddenMods, int locatePermission, int teleportPermission,
                        boolean showLootLocations, PackTools packTools) {
            this(hiddenStructures, hiddenMods, locatePermission, teleportPermission, showLootLocations, packTools, true);
        }

        public boolean hides(ResourceLocation id) {
            return hiddenMods.contains(id.getNamespace()) || hiddenStructures.contains(id);
        }
    }

    /**
     * Who can use Pack tools besides anyone a permissions mod allows: players by name, and anyone
     * with {@code permissionLevel}, or nobody else at -1.
     */
    public record PackTools(List<String> players, int permissionLevel) {
        public static final PackTools NONE = new PackTools(List.of(), -1);

        public static PackTools level(int permissionLevel) {
            return new PackTools(List.of(), permissionLevel);
        }
    }

    public static Settings get() {
        return current;
    }

    /** Swaps the settings in place of the file's, for tests. */
    public static void set(Settings settings) {
        current = settings;
    }

    public static boolean hides(ResourceLocation id) {
        return current.hides(id);
    }

    public static Path file() {
        return JustEnoughStructures.configDir().resolve(FILE_NAME);
    }

    /** Reads the file in the config folder, writing a commented one first if there isn't one. */
    public static Settings load() {
        return load(file());
    }

    public static Settings load(Path file) {
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, render(DEFAULTS));
            }
            String text = Files.readString(file);
            current = parse(text, file.toString(), true);
            addMissingSettings(file, text, current);
        } catch (IOException e) {
            JesLog.warnOnce("read:" + file + "|" + e, "Couldn't read {}, using the default settings: {}", file, e.toString());
            JesLog.debug("Couldn't read {}", file, e);
            current = DEFAULTS;
        }
        return current;
    }

    /** Every setting the file has, in the order it lists them. */
    private static final List<String> KEYS = List.of("format", "hidden", "locate_permission", "teleport_permission", "show_loot_locations",
            "pack_tools", "container_changes");

    /**
     * A file written by an older version doesn't have the settings added since, so its owner can't
     * see them to change them. It's written again from the template, with its comments and the
     * owner's own values, and the file as it was is kept beside it. A file that doesn't read is left
     * alone for its owner to fix.
     */
    private static void addMissingSettings(Path file, String text, Settings settings) {
        JsonObject json;
        try {
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) {
                return;
            }
            json = parsed.getAsJsonObject();
        } catch (JsonParseException e) {
            return;
        }
        List<String> missing = KEYS.stream().filter(key -> !json.has(key)).toList();
        if (missing.isEmpty() || FileFormat.newer(json)) {
            return;
        }
        try {
            Files.copy(file, file.resolveSibling(file.getFileName() + ".old"), StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(file, render(settings));
            JesLog.debug("Added {} to {}, keeping the old file as {}.old", missing, file, file.getFileName());
        } catch (IOException e) {
            JesLog.debug("Couldn't add {} to {}", missing, file, e);
        }
    }

    /** The settings in the file, without applying them. The defaults if there's no file or it can't be read. */
    public static Settings read(Path file) {
        try {
            return Files.exists(file) ? parse(Files.readString(file), file.toString(), false) : DEFAULTS;
        } catch (IOException e) {
            JesLog.debug("Couldn't read {}", file, e);
            return DEFAULTS;
        }
    }

    /** Writes these settings to the file, comments and all. The server picks them up on /reload. */
    public static void save(Path file, Settings settings) throws IOException {
        if (Files.exists(file)) {
            try {
                JsonElement existing = JsonParser.parseString(Files.readString(file));
                if (existing.isJsonObject()) {
                    FileFormat.check(existing.getAsJsonObject(), file);
                }
            } catch (JsonParseException e) {
                // A file that doesn't read is written over, as the owner asked for these settings.
            }
        }
        Files.createDirectories(file.getParent());
        Files.writeString(file, render(settings));
    }

    /** The settings file with these values in it. */
    public static String render(Settings settings) {
        List<String> hidden = new ArrayList<>();
        settings.hiddenMods().stream().sorted().forEach(mod -> hidden.add(mod + ":*"));
        settings.hiddenStructures().stream().map(ResourceLocation::toString).sorted().forEach(hidden::add);
        return TEMPLATE.formatted(GSON.toJson(hidden), settings.locatePermission(), settings.teleportPermission(), settings.showLootLocations(),
                GSON.toJson(settings.packTools().players()), settings.packTools().permissionLevel(), settings.containerChanges());
    }

    /** Reads the settings without reporting their mistakes in the game's log. */
    public static Settings parse(String text, String source) {
        return parse(text, source, false);
    }

    /**
     * Reads the settings, tolerating comments. A mistake in one setting is logged and that setting
     * keeps its default, so a typo doesn't throw away the rest of the file. With {@code report} each
     * mistake is a warning in the game's log, once, for the server's own file; otherwise they only go
     * to the debug log.
     */
    public static Settings parse(String text, String source, boolean report) {
        JsonObject json;
        try {
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) {
                throw new JsonParseException("expected an object with settings in it");
            }
            json = parsed.getAsJsonObject();
        } catch (JsonParseException e) {
            problem(report, source, "{} isn't valid, using the default settings: {}", source, e.getMessage());
            return DEFAULTS;
        }

        Set<ResourceLocation> structures = new LinkedHashSet<>();
        Set<String> mods = new LinkedHashSet<>();
        JsonElement hidden = json.get("hidden");
        if (hidden != null && hidden.isJsonArray()) {
            for (JsonElement element : hidden.getAsJsonArray()) {
                String entry = element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() ? element.getAsString().trim() : "";
                String mod = entry.endsWith(":*") ? entry.substring(0, entry.length() - 2) : null;
                ResourceLocation id = entry.isEmpty() || mod != null ? null : ResourceLocation.tryParse(entry);
                if (mod != null && !mod.isEmpty() && ResourceLocation.tryParse(mod + ":any") != null) {
                    mods.add(mod);
                } else if (id != null) {
                    structures.add(id);
                } else {
                    problem(report, source, "{}: {} in hidden isn't a structure id or modid:*", source, element);
                }
            }
        } else if (hidden != null) {
            problem(report, source, "{}: hidden should be a list, like [\"minecraft:ancient_city\"]", source);
        }

        return new Settings(Set.copyOf(structures), Set.copyOf(mods),
                level(json, "locate_permission", DEFAULTS.locatePermission(), source, report),
                level(json, "teleport_permission", DEFAULTS.teleportPermission(), source, report),
                flag(json, "show_loot_locations", DEFAULTS.showLootLocations(), source, report),
                packTools(json.get("pack_tools"), source, report),
                flag(json, "container_changes", DEFAULTS.containerChanges(), source, report));
    }

    /**
     * The server loads its file when it starts and again on every /reload, so a mistake is only
     * worth one warning. Anywhere else, like the settings screen checking its own values, it's noise.
     */
    private static void problem(boolean report, String source, String format, Object... args) {
        if (report) {
            JesLog.warnOnce(source + "|" + MessageFormatter.arrayFormat(format, args).getMessage(), format, args);
        } else {
            JesLog.debug(format, args);
        }
    }

    private static boolean flag(JsonObject json, String key, boolean fallback, String source, boolean report) {
        JsonElement value = json.get(key);
        if (value == null) {
            return fallback;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
            return value.getAsBoolean();
        }
        problem(report, source, "{}: {} should be true or false, not {}", source, key, value);
        return fallback;
    }

    /** The pack_tools section: names that look like player names, and a level from -1 to 4. */
    private static PackTools packTools(JsonElement value, String source, boolean report) {
        if (value == null) {
            return DEFAULTS.packTools();
        }
        if (!value.isJsonObject()) {
            problem(report, source, "{}: pack_tools should be a section, like {\"players\": [], \"permission_level\": -1}", source);
            return DEFAULTS.packTools();
        }
        JsonObject section = value.getAsJsonObject();
        List<String> players = new ArrayList<>();
        JsonElement names = section.get("players");
        if (names != null && names.isJsonArray()) {
            for (JsonElement name : names.getAsJsonArray()) {
                String text = name.isJsonPrimitive() && name.getAsJsonPrimitive().isString() ? name.getAsString().trim() : "";
                if (isPlayerName(text)) {
                    if (players.stream().noneMatch(text::equalsIgnoreCase)) {
                        players.add(text);
                    }
                } else {
                    problem(report, source, "{}: {} in pack_tools.players isn't a player's name", source, name);
                }
            }
        } else if (names != null) {
            problem(report, source, "{}: pack_tools.players should be a list of names, like [\"Steve\"]", source);
        }
        int level = DEFAULTS.packTools().permissionLevel();
        JsonElement levelValue = section.get("permission_level");
        if (levelValue != null) {
            if (levelValue.isJsonPrimitive() && levelValue.getAsJsonPrimitive().isNumber() && levelValue.getAsInt() >= -1 && levelValue.getAsInt() <= 4) {
                level = levelValue.getAsInt();
            } else {
                problem(report, source, "{}: pack_tools.permission_level should be -1 for none, or 0 to 4, not {}", source, levelValue);
            }
        }
        return new PackTools(List.copyOf(players), level);
    }

    /** Letters, digits and underscores, 1 to 16 of them, as Minecraft allows. */
    public static boolean isPlayerName(String name) {
        return name.matches("[A-Za-z0-9_]{1,16}");
    }

    private static int level(JsonObject json, String key, int fallback, String source, boolean report) {
        JsonElement value = json.get(key);
        if (value == null) {
            return fallback;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber()) {
            int level = value.getAsInt();
            if (level >= 0 && level <= 4) {
                return level;
            }
        }
        problem(report, source, "{}: {} should be a permission level from 0 to 4, not {}", source, key, value);
        return fallback;
    }
}
