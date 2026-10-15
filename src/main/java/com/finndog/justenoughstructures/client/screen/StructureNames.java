package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.Regs;
import com.finndog.justenoughstructures.client.render.RenderFailures;
import com.finndog.justenoughstructures.overrides.StructureNameFiles;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.client.resources.language.LanguageInfo;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.alchemy.Potion;
//? if >=26.1 {
/*import net.minecraft.world.item.alchemy.PotionContents;
*///?} else if >=1.21 {
/*import java.util.Optional;
*///?}

/** Readable names for structures, mods, loot tables and mobs. */
public final class StructureNames {
    /** Names already worked out, as sorting the list asks for each one many times. Dropped when the language changes. */
    private static final Map<ResourceLocation, String> STRUCTURES = new ConcurrentHashMap<>();
    private static Language namedIn;
    /** The names Pack tools gave structures on the server, each language's by lang key, or null when it sends none. */
    private static volatile Map<String, Map<String, String>> given;
    /** Goes up each time they change, so names worked out from the old ones are dropped. */
    private static volatile int givenChanges;
    private static int namedWith;

    private StructureNames() {
    }

    /**
     * The name Pack tools gave it on the server, in the game's language or else in English, then a
     * translation if a mod provides {@code structure.<namespace>.<path>}, otherwise the id tidied up.
     */
    public static String structure(ResourceLocation id) {
        Language language = Language.getInstance();
        int changes = givenChanges;
        if (language != namedIn || changes != namedWith) {
            STRUCTURES.clear();
            namedIn = language;
            namedWith = changes;
        }
        return STRUCTURES.computeIfAbsent(id, StructureNames::name);
    }

    private static String name(ResourceLocation id) {
        String key = StructureNameFiles.key(id);
        String named = givenIn(language(), key);
        if (named == null) {
            named = givenIn("en_us", key);
        }
        return named != null ? named : modsOwn(id);
    }

    /** What a mod's translation names it, or the id tidied up, leaving out any name Pack tools gave it. */
    public static String modsOwn(ResourceLocation id) {
        String key = StructureNameFiles.key(id);
        if (Language.getInstance().has(key)) {
            return I18n.get(key);
        }
        return pretty(id.getPath());
    }

    private static String givenIn(String language, String key) {
        Map<String, Map<String, String>> names = given;
        Map<String, String> inLanguage = names == null ? null : names.get(language);
        return inLanguage == null ? null : inLanguage.get(key);
    }

    /** The name Pack tools gave a structure in the game's language, or an empty string. */
    public static String given(ResourceLocation id) {
        String named = givenIn(language(), StructureNameFiles.key(id));
        return named == null ? "" : named;
    }

    /** The names Pack tools gave structures, as the server sent them, or null from a server that sends none. */
    public static void useGiven(Map<String, Map<String, String>> names) {
        given = names;
        givenChanges++;
    }

    /** A name just saved, put in use here straight away rather than when the structure list next comes. */
    public static void saved(ResourceLocation id, String language, String name) {
        Map<String, Map<String, String>> names = given;
        if (names == null) {
            return;
        }
        Map<String, Map<String, String>> next = new HashMap<>(names);
        Map<String, String> inLanguage = new HashMap<>(next.getOrDefault(language, Map.of()));
        String text = StructureNameFiles.tidy(name);
        if (text.isEmpty()) {
            inLanguage.remove(StructureNameFiles.key(id));
        } else {
            inLanguage.put(StructureNameFiles.key(id), text);
        }
        next.put(language, inLanguage);
        useGiven(next);
    }

    /** Whether the server keeps names Pack tools gives structures, which versions from before them don't. */
    public static boolean namesFromServer() {
        return given != null;
    }

    /** The language the game is in, like en_us. */
    public static String language() {
        return Minecraft.getInstance().getLanguageManager().getSelected();
    }

    /** What the game's language is called, like "English (US)". */
    public static Component languageName() {
        LanguageInfo info = Minecraft.getInstance().getLanguageManager().getLanguage(language());
        return info == null ? Component.literal(language()) : info.toComponent();
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

    /** A mob's own name, or "Nothing" for none, or its id tidied up if the game doesn't know it. */
    public static Component mob(String id) {
        if (id == null || id.isEmpty()) {
            return Component.translatable("screen.justenoughstructures.tools.no_mob");
        }
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        if (parsed != null && BuiltInRegistries.ENTITY_TYPE.containsKey(parsed)) {
            return Regs.value(BuiltInRegistries.ENTITY_TYPE, parsed).getDescription();
        }
        return Component.literal(parsed == null ? id : pretty(parsed.getPath()));
    }

    /**
     * What a mob placed in a preview calls itself, or the name of its kind if asking fails, as it can
     * for another mod's mob: a preview makes its mobs without putting them in a world.
     */
    static Component mobName(Entity mob) {
        try {
            return mob.getName();
        } catch (Throwable e) {
            RenderFailures.rethrowIfFatal(e);
            ResourceLocation id = BuiltInRegistries.ENTITY_TYPE.getKey(mob.getType());
            JesLog.warnOnce("mob-name:" + id + "|" + e.getClass().getName(), "A {} in a preview failed when asked for its name, so the name of its kind is shown", id, e);
            return mob.getType().getDescription();
        }
    }

    /** An item's own name. */
    public static Component item(Item item) {
        //? if >=26.1 {
        /*return item.getName(item.getDefaultInstance());
        *///?} else {
        return item.getDescription();
        //?}
    }

    /** A potion's name as its bottle shows it, like "Potion of Swiftness". */
    public static Component potion(Holder<Potion> potion) {
        //? if >=26.1 {
        /*return new PotionContents(potion).getName("item.minecraft.potion.effect.");
        *///?} else if >=1.21 {
        /*return Component.translatable(Potion.getName(Optional.of(potion), "item.minecraft.potion.effect."));
        *///?} else {
        return Component.translatable(potion.value().getName("item.minecraft.potion.effect."));
        //?}
    }

    /**
     * The Overworld, the Nether or the End, a translation if a mod provides {@code dimension.<namespace>.<path>},
     * otherwise the id tidied up, like "Twilight Forest".
     */
    public static String dimension(ResourceLocation id) {
        String vanilla = switch (id.toString()) {
            case "minecraft:overworld" -> "overworld";
            case "minecraft:the_nether" -> "nether";
            case "minecraft:the_end" -> "end";
            default -> null;
        };
        if (vanilla != null) {
            return I18n.get("screen.justenoughstructures.dimension." + vanilla);
        }
        String key = "dimension." + id.getNamespace() + "." + id.getPath().replace('/', '.');
        return Language.getInstance().has(key) ? I18n.get(key) : pretty(id.getPath());
    }

    public static String pretty(String path) {
        return Ids.pretty(path);
    }
}
