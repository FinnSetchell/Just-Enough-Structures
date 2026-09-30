package com.finndog.justenoughstructures.server;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * The server owner's settings, from {@code config/justenoughstructures/server.json5}: structures to
 * keep out of the browser, and who can find and teleport to them. Read when the server starts and
 * again on /reload.
 */
public final class ServerConfig {
    public static final String FILE_NAME = "server.json5";

    private static final Gson GSON = new Gson();
    private static final String TEMPLATE = """
            // Just Enough Structures server settings. Changes apply after /reload or a restart.
            {
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

              // Who can edit loot tables in the browser, saved as overrides in
              // config/justenoughstructures/loot_overrides. 4 is the server owner, or singleplayer
              // with cheats on.
              "edit_permission": %s,

              // False stops using the containers pointed at other loot tables in the browser, so every
              // structure is as its mod made it. They're kept in loot_overrides/containers.json, and
              // used again when this is true.
              "container_changes": %s
            }
            """;

    /** What {@link #get()} returns until a file is read, and whatever a file leaves out. */
    public static final Settings DEFAULTS = new Settings(Set.of(), Set.of(), 2, 2, true, 4, true);

    private static volatile Settings current = DEFAULTS;

    private ServerConfig() {
    }

    /**
     * @param hiddenStructures single structures that are hidden
     * @param hiddenMods       namespaces whose every structure is hidden
     */
    public record Settings(Set<ResourceLocation> hiddenStructures, Set<String> hiddenMods, int locatePermission, int teleportPermission,
                           boolean showLootLocations, int editPermission, boolean containerChanges) {
        public Settings(Set<ResourceLocation> hiddenStructures, Set<String> hiddenMods, int locatePermission, int teleportPermission,
                        boolean showLootLocations, int editPermission) {
            this(hiddenStructures, hiddenMods, locatePermission, teleportPermission, showLootLocations, editPermission, true);
        }

        public boolean hides(ResourceLocation id) {
            return hiddenMods.contains(id.getNamespace()) || hiddenStructures.contains(id);
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
            current = parse(Files.readString(file), file.toString());
        } catch (IOException e) {
            JustEnoughStructures.LOGGER.warn("Couldn't read {}, using the default settings", file, e);
            current = DEFAULTS;
        }
        return current;
    }

    /** The settings in the file, without applying them. The defaults if there's no file or it can't be read. */
    public static Settings read(Path file) {
        try {
            return Files.exists(file) ? parse(Files.readString(file), file.toString()) : DEFAULTS;
        } catch (IOException e) {
            JustEnoughStructures.LOGGER.warn("Couldn't read {}", file, e);
            return DEFAULTS;
        }
    }

    /** Writes these settings to the file, comments and all. The server picks them up on /reload. */
    public static void save(Path file, Settings settings) throws IOException {
        Files.createDirectories(file.getParent());
        Files.writeString(file, render(settings));
    }

    /** The settings file with these values in it. */
    public static String render(Settings settings) {
        List<String> hidden = new ArrayList<>();
        settings.hiddenMods().stream().sorted().forEach(mod -> hidden.add(mod + ":*"));
        settings.hiddenStructures().stream().map(ResourceLocation::toString).sorted().forEach(hidden::add);
        return TEMPLATE.formatted(GSON.toJson(hidden), settings.locatePermission(), settings.teleportPermission(), settings.showLootLocations(),
                settings.editPermission(), settings.containerChanges());
    }

    /**
     * Reads the settings, tolerating comments. A mistake in one setting is logged and that setting
     * keeps its default, so a typo doesn't throw away the rest of the file.
     */
    public static Settings parse(String text, String source) {
        JsonObject json;
        try {
            JsonElement parsed = JsonParser.parseString(text);
            if (!parsed.isJsonObject()) {
                throw new JsonParseException("expected an object with settings in it");
            }
            json = parsed.getAsJsonObject();
        } catch (JsonParseException e) {
            JustEnoughStructures.LOGGER.warn("{} isn't valid, using the default settings: {}", source, e.getMessage());
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
                if (mod != null && !mod.isEmpty() && ResourceLocation.isValidResourceLocation(mod + ":any")) {
                    mods.add(mod);
                } else if (id != null) {
                    structures.add(id);
                } else {
                    JustEnoughStructures.LOGGER.warn("{}: {} in hidden isn't a structure id or modid:*", source, element);
                }
            }
        } else if (hidden != null) {
            JustEnoughStructures.LOGGER.warn("{}: hidden should be a list, like [\"minecraft:ancient_city\"]", source);
        }

        return new Settings(Set.copyOf(structures), Set.copyOf(mods),
                level(json, "locate_permission", DEFAULTS.locatePermission(), source),
                level(json, "teleport_permission", DEFAULTS.teleportPermission(), source),
                flag(json, "show_loot_locations", DEFAULTS.showLootLocations(), source),
                level(json, "edit_permission", DEFAULTS.editPermission(), source),
                flag(json, "container_changes", DEFAULTS.containerChanges(), source));
    }

    private static boolean flag(JsonObject json, String key, boolean fallback, String source) {
        JsonElement value = json.get(key);
        if (value == null) {
            return fallback;
        }
        if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean()) {
            return value.getAsBoolean();
        }
        JustEnoughStructures.LOGGER.warn("{}: {} should be true or false, not {}", source, key, value);
        return fallback;
    }

    private static int level(JsonObject json, String key, int fallback, String source) {
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
        JustEnoughStructures.LOGGER.warn("{}: {} should be a permission level from 0 to 4, not {}", source, key, value);
        return fallback;
    }
}
