package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.Regs;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.server.PackToolsState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.saveddata.maps.MapDecoration;
//? if >=1.21 {
/*import net.minecraft.client.multiplayer.ClientPacketListener;
*///?}

/**
 * What the editor's boxes can be set to, as misode's generator lists them: items, enchantments,
 * effects, tags and the like from the game's own lists, so mods' are there too; loot tables and
 * structure tags from the server, which only it has; and the fixed words some settings take.
 */
final class LootOptions {
    /** Something a box can be set to: what's written, and a readable name for it. */
    record Option(String id, String name) {
    }

    /** A list of options, worked out when first wanted, with each one's name to hand by its id. */
    static final class Source {
        private final Supplier<List<Option>> make;
        private List<Option> list;
        private Map<String, String> names;

        private Source(Supplier<List<Option>> make) {
            this.make = make;
        }

        List<Option> list() {
            if (list == null) {
                list = make.get();
                names = new HashMap<>();
                list.forEach(o -> names.put(o.id(), o.name()));
            }
            return list;
        }

        /** The readable name for {@code id}, or null if it isn't one of these. */
        String name(String id) {
            list();
            String found = names.get(id);
            // "sharpness" means minecraft:sharpness to the game.
            return found != null || id.contains(":") || id.isEmpty() ? found : names.get((id.startsWith("#") ? "#minecraft:" + id.substring(1) : "minecraft:" + id));
        }

        /** Worked out again next time, for lists that change, like tags after a /reload. */
        void forget() {
            list = null;
            names = null;
        }
    }

    private static final List<Source> ALL = new ArrayList<>();

    private static Source source(Supplier<List<Option>> make) {
        Source source = new Source(make);
        ALL.add(source);
        return source;
    }

    static final Source ITEMS = source(() -> registry(BuiltInRegistries.ITEM, item -> StructureNames.item(item).getString(), "minecraft:air"));
    /** Item tags as tag entries and item predicates write them, without a "#". */
    static final Source ITEM_TAG_IDS = source(() -> tags(BuiltInRegistries.ITEM, ""));
    static final Source BLOCKS = source(() -> registry(BuiltInRegistries.BLOCK, block -> block.getName().getString(), "minecraft:air"));
    //? if >=1.21 {
    /*static final Source ENCHANTMENTS = source(() -> fromServer(Registries.ENCHANTMENT, enchantment -> enchantment.description().getString()));
    static final Source POTIONS = source(() -> registry(BuiltInRegistries.POTION,
            potion -> StructureNames.potion(BuiltInRegistries.POTION.wrapAsHolder(potion)).getString(),
            null));
    *///?} else {
    static final Source ENCHANTMENTS = source(() -> registry(BuiltInRegistries.ENCHANTMENT,
            enchantment -> Component.translatable(enchantment.getDescriptionId()).getString(), null));
    static final Source POTIONS = source(() -> registry(BuiltInRegistries.POTION,
            potion -> Component.translatable(potion.getName("item.minecraft.potion.effect.")).getString(), null));
    //?}
    static final Source EFFECTS = source(() -> registry(BuiltInRegistries.MOB_EFFECT, effect -> effect.getDisplayName().getString(), null));
    static final Source ATTRIBUTES = source(() -> registry(BuiltInRegistries.ATTRIBUTE,
            attribute -> Component.translatable(attribute.getDescriptionId()).getString(), null));
    static final Source BLOCK_ENTITY_TYPES = source(() -> registry(BuiltInRegistries.BLOCK_ENTITY_TYPE, null, null));
    //? if >=1.21 {
    /*static final Source BANNER_PATTERNS = source(() -> fromServer(Registries.BANNER_PATTERN, null));
    *///?} else {
    static final Source BANNER_PATTERNS = source(() -> registry(BuiltInRegistries.BANNER_PATTERN, null, null));
    //?}
    /** Instrument tags, which set_instrument wants with their "#". */
    //? if >=1.21.2 {
    /*static final Source INSTRUMENT_TAGS = source(() -> fromServerTags(Registries.INSTRUMENT));
    *///?} else {
    static final Source INSTRUMENT_TAGS = source(() -> tags(BuiltInRegistries.INSTRUMENT, "#"));
    //?}
    static final Source ENTITY_TYPES = source(() -> registry(BuiltInRegistries.ENTITY_TYPE, type -> type.getDescription().getString(), null));
    /** An entity predicate's type: an entity, or with a "#", a tag of them. */
    static final Source ENTITY_TYPES_OR_TAGS = source(() -> {
        List<Option> out = new ArrayList<>(ENTITY_TYPES.list());
        out.addAll(tags(BuiltInRegistries.ENTITY_TYPE, "#"));
        return out;
    });
    static final Source BIOMES = source(LootOptions::biomes);
    static final Source DIMENSIONS = source(LootOptions::dimensions);
    static final Source LOOT_TABLES = source(() -> server(state -> state.tables(), StructureNames::lootTable));
    static final Source STRUCTURES = source(() -> serverNames(PackToolsState.STRUCTURES, id -> StructureNames.structure(Ids.parse(id))));
    /** Structure tags, as an explorer map's destination writes them, without a "#". */
    static final Source STRUCTURE_TAGS = source(() -> serverNames(PackToolsState.STRUCTURE_TAGS, null));
    static final Source PREDICATES = source(() -> serverNames(PackToolsState.PREDICATES, null));
    static final Source ITEM_MODIFIERS = source(() -> serverNames(PackToolsState.ITEM_MODIFIERS, null));
    //? if >=1.21 {
    /*// Since 1.20.5 a set of things is one id, a tag with its "#", or a list of ids. The first two share
    // a box, which lists both.
    static final Source ITEMS_OR_TAGS = either(ITEMS, () -> tags(BuiltInRegistries.ITEM, "#"));
    static final Source ENCHANTMENTS_OR_TAGS = either(ENCHANTMENTS, () -> fromServerTags(Registries.ENCHANTMENT));
    static final Source POTIONS_OR_TAGS = either(POTIONS, () -> tags(BuiltInRegistries.POTION, "#"));
    static final Source BIOMES_OR_TAGS = either(BIOMES, LootOptions::biomeTags);
    static final Source STRUCTURES_OR_TAGS = either(STRUCTURES,
            () -> STRUCTURE_TAGS.list().stream().map(o -> new Option("#" + o.id(), o.name())).toList());
    *///?}

    // The fixed words some settings take, named by the editor's lang file.
    static final Source ENTITY_TARGETS = words("target", "this", "killer", "direct_killer", "killer_player");
    static final Source COPY_SOURCES = words("target", "block_entity", "this", "killer", "killer_player");
    static final Source NBT_SOURCES = words("target", "block_entity", "this", "killer", "killer_player", "direct_killer");
    static final Source COPY_OPS = words("copy_op", "replace", "append", "merge");
    //? if >=1.21 {
    /*static final Source OPERATIONS = words("operation", "add_value", "add_multiplied_base", "add_multiplied_total");
    static final Source SLOTS = words("slot", "any", "mainhand", "offhand", "hand", "head", "chest", "legs", "feet", "armor", "body");
    static final Source NAME_TARGETS = words("name_target", "custom_name", "item_name");
    static final Source LIST_MODES = words("list_mode", "append", "insert", "replace_all", "replace_section");
    static final Source CONTAINERS = words("container", "minecraft:container", "minecraft:bundle_contents", "minecraft:charged_projectiles");
    *///?} else {
    static final Source OPERATIONS = words("operation", "addition", "multiply_base", "multiply_total");
    static final Source SLOTS = words("slot", "mainhand", "offhand", "head", "chest", "legs", "feet");
    //?}
    static final Source FORMULAS = words("formula", "minecraft:ore_drops", "minecraft:uniform_bonus_count", "minecraft:binomial_with_bonus_count");
    static final Source DYNAMIC = words("dynamic", "minecraft:contents", "minecraft:sherds");
    static final Source DYE_COLORS = source(() -> {
        List<Option> out = new ArrayList<>();
        for (DyeColor colour : DyeColor.values()) {
            out.add(new Option(colour.getName(), Component.translatable("color.minecraft." + colour.getName()).getString()));
        }
        return out;
    });
    //? if >=1.21 {
    /*static final Source MAP_DECORATIONS = source(() -> registry(BuiltInRegistries.MAP_DECORATION_TYPE, null, null));
    *///?} else {
    static final Source MAP_DECORATIONS = source(() -> {
        List<Option> out = new ArrayList<>();
        for (MapDecoration.Type type : MapDecoration.Type.values()) {
            String id = type.name().toLowerCase(Locale.ROOT);
            out.add(new Option(id, StructureNames.pretty(id)));
        }
        return out;
    });
    //?}

    private LootOptions() {
    }

    /** Lists that change are worked out afresh, as when the editor opens again after a /reload. */
    static void forget() {
        ALL.forEach(Source::forget);
    }

    /** A block's state properties, like "facing" or "waterlogged", for {@code block}. */
    static Source properties(String block) {
        return new Source(() -> {
            List<Option> out = new ArrayList<>();
            Block found = block(block);
            if (found != null) {
                for (Property<?> property : found.getStateDefinition().getProperties()) {
                    out.add(new Option(property.getName(), property.getName()));
                }
            }
            return out;
        });
    }

    /** The values one of a block's state properties can have. */
    static Source values(String block, String property) {
        return new Source(() -> {
            List<Option> out = new ArrayList<>();
            Block found = block(block);
            Property<?> p = found == null ? null : found.getStateDefinition().getProperty(property);
            if (p != null) {
                for (Object value : p.getPossibleValues()) {
                    String name = valueName(p, value);
                    out.add(new Option(name, name));
                }
            }
            return out;
        });
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String valueName(Property property, Object value) {
        return property.getName((Comparable) value);
    }

    private static Block block(String id) {
        ResourceLocation parsed = ResourceLocation.tryParse(id.trim());
        return parsed != null && BuiltInRegistries.BLOCK.containsKey(parsed) ? Regs.value(BuiltInRegistries.BLOCK, parsed) : null;
    }

    private static Source words(String kind, String... ids) {
        return source(() -> {
            List<Option> out = new ArrayList<>();
            for (String id : ids) {
                String path = id.contains(":") ? id.substring(id.indexOf(':') + 1) : id;
                out.add(new Option(id, Component.translatableWithFallback("screen.justenoughstructures.editor." + kind + "." + path,
                        StructureNames.pretty(path)).getString()));
            }
            return out;
        });
    }

    //? if >=1.21 {
    /*// A registry the server sends over with the world, like enchantments since 1.21.
    private static <T> List<Option> fromServer(ResourceKey<Registry<T>> key, Function<T, String> name) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection == null ? new ArrayList<>()
                : connection.registryAccess().registry(key).map(registry -> registry(registry, name, null)).orElseGet(ArrayList::new);
    }

    // And its tags, each with its "#".
    private static <T> List<Option> fromServerTags(ResourceKey<Registry<T>> key) {
        ClientPacketListener connection = Minecraft.getInstance().getConnection();
        return connection == null ? new ArrayList<>()
                : connection.registryAccess().registry(key).map(registry -> tags(registry, "#")).orElseGet(ArrayList::new);
    }

    private static List<Option> biomeTags() {
        Minecraft minecraft = Minecraft.getInstance();
        return minecraft.level == null ? new ArrayList<>() : tags(minecraft.level.registryAccess().registryOrThrow(Registries.BIOME), "#");
    }

    // One list's options, then another's after them.
    private static Source either(Source first, Supplier<List<Option>> then) {
        return source(() -> {
            List<Option> out = new ArrayList<>(first.list());
            out.addAll(then.get());
            return out;
        });
    }

    *///?}
    private static <T> List<Option> registry(Registry<T> registry, Function<T, String> name, String leaveOut) {
        List<Option> out = new ArrayList<>();
        for (ResourceLocation id : registry.keySet()) {
            if (id.toString().equals(leaveOut)) {
                continue;
            }
            String shown = null;
            if (name != null) {
                try {
                    shown = name.apply(Regs.value(registry, id));
                } catch (RuntimeException e) {
                    // A mod's own thing that can't name itself: its id will do.
                }
            }
            out.add(new Option(id.toString(), shown == null || shown.isEmpty() ? pretty(id) : shown));
        }
        return sorted(out);
    }

    /** A registry's tags, each written with {@code prefix} before it: "#", or nothing where the setting is always a tag. */
    private static <T> List<Option> tags(Registry<T> registry, String prefix) {
        List<Option> out = new ArrayList<>();
        Regs.tagIds(registry).forEach(tag -> out.add(new Option(prefix + tag.location(), pretty(tag.location()))));
        return sorted(out);
    }

    private static List<Option> biomes() {
        Minecraft minecraft = Minecraft.getInstance();
        List<Option> out = new ArrayList<>();
        if (minecraft.level != null) {
            for (ResourceLocation id : minecraft.level.registryAccess().registryOrThrow(Registries.BIOME).keySet()) {
                out.add(new Option(id.toString(), Component.translatableWithFallback("biome." + id.getNamespace() + "." + id.getPath(), pretty(id)).getString()));
            }
        }
        return sorted(out);
    }

    private static List<Option> dimensions() {
        Minecraft minecraft = Minecraft.getInstance();
        List<Option> out = new ArrayList<>();
        if (minecraft.getConnection() != null) {
            for (ResourceKey<Level> level : minecraft.getConnection().levels()) {
                out.add(new Option(Ids.of(level).toString(), pretty(Ids.of(level))));
            }
        }
        return sorted(out);
    }

    /** From what the server last said; asks it again if it hasn't yet. */
    private static List<Option> server(Function<PackToolsState, List<ResourceLocation>> ids, Function<String, String> name) {
        PackToolsState state = ClientRequests.lastTools();
        if (state == null) {
            ClientRequests.tools();
            return List.of();
        }
        List<Option> out = new ArrayList<>();
        for (ResourceLocation id : ids.apply(state)) {
            out.add(new Option(id.toString(), name == null ? pretty(id) : name.apply(id.toString())));
        }
        return sorted(out);
    }

    private static List<Option> serverNames(String key, Function<String, String> name) {
        return server(state -> state.names().getOrDefault(key, List.of()), name);
    }

    private static String pretty(ResourceLocation id) {
        String name = StructureNames.pretty(id.getPath().substring(id.getPath().lastIndexOf('/') + 1));
        return id.getNamespace().equals("minecraft") ? name : name + " (" + StructureNames.mod(id.getNamespace()) + ")";
    }

    private static List<Option> sorted(List<Option> options) {
        options.sort(Comparator.comparing((Option o) -> !o.id().startsWith("minecraft:") && !o.id().startsWith("#minecraft:"))
                .thenComparing(o -> o.name().toLowerCase(Locale.ROOT)));
        return options;
    }
}
