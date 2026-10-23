package com.finndog.justenoughstructures.overrides;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.SafeFiles;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * The names Pack tools gives structures. They're lang files in the JES pack, one for each language,
 * at {@code assets/justenoughstructures/lang/<language>.json}, under the key the game and other mods
 * name a structure by: {@code structure.<namespace>.<path>}, with the path's slashes as dots. The
 * server sends every language's to players with the structure list. On the player's own game the
 * folder is also a resource pack ({@link NamesPack}), so mods that look the key up themselves show
 * the name too.
 */
public final class StructureNameFiles {
    public static final int MAX_NAME = 128;
    /** The longest a language's code can be, like en_us. */
    public static final int MAX_LANGUAGE = 16;
    private static final Pattern LANGUAGE = Pattern.compile("[a-z0-9_]{2," + MAX_LANGUAGE + "}");
    private static final Gson PRETTY = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    /** Every language's names by lang key, read when first asked for, and again after a save or a /reload. */
    private static volatile Map<String, Map<String, String>> names;

    private StructureNameFiles() {
    }

    static Path folder(Path root) {
        return root.resolve("assets").resolve(JustEnoughStructures.MOD_ID).resolve("lang");
    }

    /** The key a structure is named by, as {@code Util.makeDescriptionId("structure", id)} makes it. */
    public static String key(ResourceLocation id) {
        return "structure." + id.getNamespace() + "." + id.getPath().replace('/', '.');
    }

    public static boolean isLanguage(String code) {
        return code != null && LANGUAGE.matcher(code).matches();
    }

    /** A name as it's kept: on one line, without spaces at either end, and no longer than the limit. */
    public static String tidy(String name) {
        String text = name == null ? "" : name.replaceAll("\\s+", " ").strip();
        return text.length() > MAX_NAME ? text.substring(0, MAX_NAME).strip() : text;
    }

    /** Every language's names, by lang key. */
    public static Map<String, Map<String, String>> all() {
        Map<String, Map<String, String>> known = names;
        if (known == null) {
            known = read(folder(LootOverrides.folder()));
            names = known;
        }
        return known;
    }

    /** Reads the files again next time, as after a /reload they may have been changed by hand. */
    public static void forget() {
        names = null;
    }

    /**
     * Returns the game's strings for these languages with the saved names laid over them, one language
     * at a time in load order (English, then the player's language). A name saved in the player's
     * language beats one saved in English, which beats any mod's name for the structure, in any
     * namespace. The files are read again on every call. On error the strings come back unchanged.
     */
    public static Map<String, String> laidOver(Map<String, String> strings, List<String> languages) {
        try {
            Map<String, Map<String, String>> saved = read(folder(LootOverrides.folder()));
            Map<String, String> out = strings;
            for (String language : languages) {
                Map<String, String> inLanguage = saved.get(language);
                if (inLanguage != null) {
                    if (out == strings) {
                        out = new HashMap<>(strings);
                    }
                    out.putAll(inLanguage);
                }
            }
            return out;
        } catch (RuntimeException e) {
            JesLog.warnOnce("names-laid-over", "Couldn't put the saved structure names over the game's own: {}", e.toString());
            return strings;
        }
    }

    static Map<String, Map<String, String>> read(Path folder) {
        if (!Files.isDirectory(folder)) {
            return Map.of();
        }
        Map<String, Map<String, String>> out = new TreeMap<>();
        try (Stream<Path> files = Files.list(folder)) {
            for (Path file : files.sorted().toList()) {
                String fileName = file.getFileName().toString();
                String language = fileName.endsWith(".json") ? fileName.substring(0, fileName.length() - ".json".length()) : "";
                if (!isLanguage(language)) {
                    continue;
                }
                Map<String, String> keys = new TreeMap<>();
                try {
                    JsonElement json = JsonParser.parseString(Files.readString(file));
                    if (json.isJsonObject()) {
                        for (Map.Entry<String, JsonElement> entry : json.getAsJsonObject().entrySet()) {
                            String name = entry.getValue().isJsonPrimitive() ? tidy(entry.getValue().getAsString()) : "";
                            if (entry.getKey().startsWith("structure.") && !name.isEmpty()) {
                                keys.put(entry.getKey(), name);
                            }
                        }
                    }
                } catch (IOException | RuntimeException e) {
                    JesLog.warnOnce("names:" + file, "Couldn't read the structure names in {}: {}", file, e.toString());
                }
                if (!keys.isEmpty()) {
                    out.put(language, Collections.unmodifiableMap(keys));
                }
            }
        } catch (IOException e) {
            JesLog.warnOnce("names:" + folder, "Couldn't read the structure names in {}: {}", folder, e.toString());
        }
        return Collections.unmodifiableMap(out);
    }

    /**
     * Gives a structure a name in one language, or takes its name in that language away when the
     * name is blank, so the mod's own is used again. Only that one key in the lang file changes, so
     * anything else written in it by hand stays, and a file that can't be read is left alone.
     */
    public static Component save(ResourceLocation id, String language, String name) {
        if (!isLanguage(language)) {
            return Component.translatable("screen.justenoughstructures.tools.no_language", String.valueOf(language));
        }
        Path root = LootOverrides.folder();
        Path file = folder(root).resolve(language + ".json");
        String text = tidy(name);
        try {
            JsonObject json = new JsonObject();
            if (Files.exists(file)) {
                json = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            }
            String key = key(id);
            if (text.isEmpty()) {
                json.remove(key);
            } else {
                json.addProperty(key, text);
            }
            if (json.size() == 0) {
                Files.deleteIfExists(file);
            } else {
                LootOverrides.ensurePack(root);
                Files.createDirectories(file.getParent());
                SafeFiles.write(file, PRETTY.toJson(json));
            }
        } catch (IOException | RuntimeException e) {
            JustEnoughStructures.LOGGER.warn("Couldn't save the name of {}: {}", id, e.toString());
            JesLog.debug("Couldn't save the name of {}", id, e);
            return Component.translatable("screen.justenoughstructures.override.save_failed", String.valueOf(e.getMessage()));
        }
        forget();
        return Component.translatable("screen.justenoughstructures.tools.structure_saved");
    }
}
