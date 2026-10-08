package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.Regs;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
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
        if (Language.getInstance().has(key)) {
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
