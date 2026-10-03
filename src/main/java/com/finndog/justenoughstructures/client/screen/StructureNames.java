package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.JustEnoughStructures;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;

/** Readable names for structures, mods and loot tables. */
public final class StructureNames {
    /** Names already worked out, as sorting the list asks for each one many times. Dropped when the language changes. */
    private static final Map<ResourceLocation, String> STRUCTURES = new ConcurrentHashMap<>();
    private static Language namedIn;

    private StructureNames() {
    }

    /** A translation if a mod provides {@code structure.<namespace>.<path>}, otherwise the id tidied up. */
    public static String structure(ResourceLocation id) {
        Language language = Language.getInstance();
        if (language != namedIn) {
            STRUCTURES.clear();
            namedIn = language;
        }
        return STRUCTURES.computeIfAbsent(id, StructureNames::name);
    }

    private static String name(ResourceLocation id) {
        String key = "structure." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        if (I18n.exists(key)) {
            return I18n.get(key);
        }
        return pretty(id.getPath());
    }

    public static String mod(String namespace) {
        return JustEnoughStructures.modName(namespace);
    }

    /**
     * "minecraft:chests/desert_pyramid" becomes "Desert Pyramid", and tables that aren't for chests
     * say what they are, so "minecraft:archaeology/desert_pyramid" becomes "Desert Pyramid (Archaeology)".
     */
    public static String lootTable(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        if (parsed == null) {
            return id;
        }
        String path = parsed.getPath();
        int slash = path.indexOf('/');
        String kind = slash > 0 ? path.substring(0, slash) : "chests";
        return kind.equals("chests") ? pretty(path) : pretty(path) + " (" + pretty(kind) + ")";
    }

    public static String pretty(String path) {
        String last = path.substring(path.lastIndexOf('/') + 1);
        StringBuilder out = new StringBuilder();
        for (String word : last.split("_")) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return out.toString();
    }
}
