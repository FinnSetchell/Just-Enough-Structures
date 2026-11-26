package com.finndog.justenoughstructures.loot;

import com.finndog.justenoughstructures.JesLog;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
//? if neoforge && >=26.1 {
/*import net.neoforged.neoforge.resource.NeoForgeReloadListeners;
*///?}

/**
 * The global loot modifiers loaded now: Forge's, NeoForge's, or on Fabric Porting Lib's port of them
 * (as in Prominence II). Each is read written out with its own codec, which is the same on all three:
 * its conditions say which tables it can act on as a chest is filled, and the rest names the items and
 * tables it adds. Read in the loaders' jars (Forge 47.4.10, 52.1.0, 64.0.8, 65.0.0, 66.0.5; NeoForge
 * 21.1.255, 26.1.2.109, 26.2.0.88, 26.3.0.51; Porting Lib 2.3.8 for 1.20.1), 26 November 2026.
 *
 * <p>Which tables, from its conditions: one that names its table with a loot_table_id condition acts
 * on that table only. One whose conditions can never pass as a chest is filled acts on none: each
 * needs something a chest's loot context never has, a player's kill, a tool, a block, a damage source,
 * checked against the chest context's parameters in this version. Forge and NeoForge let a chest's loot
 * have a killer, as a chest minecart can have one, so there a modifier needing a killer counts. Any other may act on any table, as may one
 * that can't be written out or whose conditions are another mod's.
 */
public final class LootModifiers {
    /** Where the loader keeps them, by id, as of its last /reload. */
    private static final List<String> MAP_FIELDS = List.of("registeredLootModifiers", "modifiers");
    //? if fabric {
    private static final String MODIFIER = "io.github.fabricators_of_create.porting_lib.loot.IGlobalLootModifier";
    //?} else if forge {
    /*private static final String MODIFIER = "net.minecraftforge.common.loot.IGlobalLootModifier";
    *///?} else {
    /*private static final String MODIFIER = "net.neoforged.neoforge.common.loot.IGlobalLootModifier";
    *///?}

    /**
     * One loaded modifier: its id, as written out, which tables it can act on as a chest is filled
     * ({@code null} for any, none if it never acts there), and the item and table ids the rest of it
     * names, which are what it adds, or what it takes out or swaps.
     */
    public record Modifier(ResourceLocation id, String type, JsonObject json, Set<ResourceLocation> tables, Set<ResourceLocation> items,
                           Set<ResourceLocation> namedTables) {
        public boolean anyTable() {
            return tables == null;
        }

        public boolean neverInChests() {
            return tables != null && tables.isEmpty();
        }
    }

    /**
     * What's loaded. {@code readable} is false when the loader's list couldn't be read, so any table may
     * be changed.
     */
    public record Listing(List<Modifier> all, boolean readable) {
        public static final Listing NONE = new Listing(List.of(), true);

        /** The modifiers that name {@code table}, so act on it only. */
        public List<Modifier> on(ResourceLocation table) {
            List<Modifier> out = new ArrayList<>();
            for (Modifier m : all) {
                if (m.tables() != null && m.tables().contains(table)) {
                    out.add(m);
                }
            }
            return out;
        }

        /** The modifiers that may act on any table. */
        public List<Modifier> anyTable() {
            return all.stream().filter(Modifier::anyTable).toList();
        }

        /** Every table a modifier names as the one it acts on. */
        public Set<ResourceLocation> tables() {
            Set<ResourceLocation> out = new TreeSet<>();
            for (Modifier m : all) {
                if (m.tables() != null) {
                    out.addAll(m.tables());
                }
            }
            return out;
        }
    }

    private record Cached(Object loaded, Listing listing) {
    }

    private static volatile Cached last;

    private LootModifiers() {
    }

    /**
     * The modifiers loaded now, read again only after a /reload has loaded new ones. After the server's
     * data has loaded.
     */
    public static Listing read(MinecraftServer server) {
        Map<?, ?> loaded;
        try {
            loaded = loaded(server);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            JesLog.debug("Couldn't read the loaded loot modifiers, so any loot table may be changed as it's rolled", e);
            return new Listing(List.of(), false);
        }
        if (loaded == null) {
            return Listing.NONE;
        }
        Cached cached = last;
        if (cached != null && cached.loaded() == loaded) {
            return cached.listing();
        }
        Listing listing = list(server, loaded);
        last = new Cached(loaded, listing);
        return listing;
    }

    private static Listing list(MinecraftServer server, Map<?, ?> loaded) {
        Codec<Object> codec;
        try {
            @SuppressWarnings("unchecked")
            Codec<Object> direct = (Codec<Object>) Class.forName(MODIFIER, false, LootModifiers.class.getClassLoader()).getField("DIRECT_CODEC").get(null);
            codec = direct;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            JesLog.debug("Couldn't find how loot modifiers are written out, so any loot table may be changed as it's rolled", e);
            return new Listing(List.of(), false);
        }
        DynamicOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        Set<Object> chest = chestParams();
        List<Modifier> all = new ArrayList<>();
        for (Map.Entry<?, ?> entry : List.copyOf(loaded.entrySet())) {
            ResourceLocation id = entry.getKey() instanceof ResourceLocation key ? key : null;
            JsonObject json = null;
            try {
                if (codec.encodeStart(ops, entry.getValue()).result().orElse(null) instanceof JsonObject written) {
                    json = written;
                }
            } catch (RuntimeException | LinkageError e) {
                JesLog.debug("Couldn't write out the loot modifier {}", id, e);
            }
            if (json == null) {
                all.add(new Modifier(id, entry.getValue().getClass().getName(), null, null, Set.of(), Set.of()));
                continue;
            }
            String type = json.has("type") && json.get("type").isJsonPrimitive() ? json.get("type").getAsString() : "";
            Set<ResourceLocation> tables = all(conditionsOf(json), chest);
            Set<ResourceLocation> items = new TreeSet<>();
            Set<ResourceLocation> named = new TreeSet<>();
            for (Map.Entry<String, JsonElement> field : json.entrySet()) {
                if (!field.getKey().equals("type") && !field.getKey().equals("conditions") && !field.getKey().equals("condition")) {
                    names(server, field.getValue(), items, named);
                }
            }
            all.add(new Modifier(id, type, json, tables, items, named));
        }
        return new Listing(List.copyOf(all), true);
    }

    /**
     * The tables a modifier written out can act on as a chest is filled, from its conditions: those it
     * names, none, or {@code null} for any.
     */
    public static Set<ResourceLocation> tablesOf(JsonObject modifier) {
        return all(conditionsOf(modifier), chestParams());
    }

    /** A modifier's conditions, all of which pass for it to act: a list, or from NeoForge 26.3 one condition. */
    static List<JsonElement> conditionsOf(JsonObject modifier) {
        List<JsonElement> out = new ArrayList<>();
        JsonElement json = modifier.has("conditions") ? modifier.get("conditions") : modifier.get("condition");
        if (json instanceof JsonArray array) {
            array.forEach(out::add);
        } else if (json != null) {
            out.add(json);
        }
        return out;
    }

    /** Where conditions that all have to pass can: as in {@link #reach}, intersected. */
    private static Set<ResourceLocation> all(List<JsonElement> conditions, Set<Object> chest) {
        Set<ResourceLocation> tables = null;
        for (JsonElement c : conditions) {
            Set<ResourceLocation> one = reach(c, chest);
            if (one == null) {
                continue;
            }
            if (one.isEmpty()) {
                return Set.of();
            }
            if (tables == null) {
                tables = new TreeSet<>(one);
            } else {
                tables.retainAll(one);
            }
        }
        return tables;
    }

    /**
     * The tables a condition can pass on as a chest is filled: those it names, none if it can never pass
     * in a chest, or {@code null} for any table.
     */
    static Set<ResourceLocation> reach(JsonElement json, Set<Object> chest) {
        // Its type is under condition, or from 26.3 under type.
        if (!(json instanceof JsonObject c)) {
            return null;
        }
        JsonElement kind = c.has("condition") ? c.get("condition") : c.get("type");
        if (!(kind instanceof JsonPrimitive) || !kind.getAsJsonPrimitive().isString()) {
            return null;
        }
        String type = kind.getAsString();
        if (!type.contains(":")) {
            type = "minecraft:" + type;
        }
        if (type.endsWith(":loot_table_id")) {
            ResourceLocation id = c.has("loot_table_id") && c.get("loot_table_id").isJsonPrimitive()
                    ? ResourceLocation.tryParse(c.get("loot_table_id").getAsString()) : null;
            return id == null ? null : Set.of(id);
        }
        switch (type) {
            case "minecraft:all_of" -> {
                return all(terms(c), chest);
            }
            case "minecraft:any_of", "minecraft:alternative" -> {
                Set<ResourceLocation> tables = new TreeSet<>();
                for (JsonElement term : terms(c)) {
                    Set<ResourceLocation> one = reach(term, chest);
                    if (one == null) {
                        return null;
                    }
                    tables.addAll(one);
                }
                return tables;
            }
            default -> {
                Object needs = needs(type, c);
                return needs != null && !chest.contains(needs) ? Set.of() : null;
            }
        }
    }

    private static List<JsonElement> terms(JsonObject c) {
        List<JsonElement> out = new ArrayList<>();
        if (c.get("terms") instanceof JsonArray array) {
            array.forEach(out::add);
        }
        return out;
    }

    /**
     * The context parameter without which a condition never passes, for the ones known to fail without
     * it (read in each version's classes), or null. An entity_properties condition with nothing to match
     * passes without its entity, so only one with a predicate counts.
     */
    private static Object needs(String type, JsonObject c) {
        return switch (type) {
            case "minecraft:killed_by_player" -> LootContextParams.LAST_DAMAGE_PLAYER;
            case "minecraft:match_tool", "forge:can_tool_perform_action", "porting_lib:can_tool_perform_action",
                 "neoforge:can_item_perform_ability" -> LootContextParams.TOOL;
            case "minecraft:block_state_property" -> LootContextParams.BLOCK_STATE;
            case "minecraft:damage_source_properties" -> LootContextParams.DAMAGE_SOURCE;
            case "minecraft:entity_scores" -> entity(c);
            case "minecraft:entity_properties" -> c.get("predicate") instanceof JsonObject p && p.size() > 0 ? entity(c) : null;
            default -> null;
        };
    }

    /** The parameter an entity condition's entity comes from. */
    private static Object entity(JsonObject c) {
        if (!(c.get("entity") instanceof JsonPrimitive name)) {
            return null;
        }
        try {
            //? if >=26.1 {
            /*return LootContext.EntityTarget.getByName(name.getAsString()).contextParam();
            *///?} else {
            return LootContext.EntityTarget.getByName(name.getAsString()).getParam();
            //?}
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The parameters a chest's loot context can have. */
    static Set<Object> chestParams() {
        //? if >=26.1 {
        /*return new HashSet<>(LootContextParamSets.CHEST.allowed());
        *///?} else {
        return new HashSet<>(LootContextParamSets.CHEST.getAllowed());
        //?}
    }

    /** Adds the item ids and loaded tables' ids in {@code json}. */
    private static void names(MinecraftServer server, JsonElement json, Set<ResourceLocation> items, Set<ResourceLocation> tables) {
        if (json instanceof JsonArray array) {
            array.forEach(e -> names(server, e, items, tables));
        } else if (json instanceof JsonObject object) {
            object.entrySet().forEach(e -> names(server, e.getValue(), items, tables));
        } else if (json instanceof JsonPrimitive p && p.isString() && p.getAsString().contains(":")) {
            ResourceLocation id = ResourceLocation.tryParse(p.getAsString());
            if (id == null) {
                return;
            }
            if (BuiltInRegistries.ITEM.containsKey(id) && !id.getPath().equals("air")) {
                items.add(id);
            }
            if (LootRolls.table(server, id) != LootTable.EMPTY) {
                tables.add(id);
            }
        }
    }

    /** The loader's loaded modifiers by id, or null without any to load them (Fabric without Porting Lib). */
    private static Map<?, ?> loaded(MinecraftServer server) throws ReflectiveOperationException {
        Object manager;
        //? if fabric {
        Class<?> managerClass;
        try {
            managerClass = Class.forName("io.github.fabricators_of_create.porting_lib.loot.LootModifierManager", false,
                    LootModifiers.class.getClassLoader());
        } catch (ClassNotFoundException e) {
            return null;
        }
        manager = managerClass.getMethod("getLootModifierManager").invoke(null);
        //?} else if forge {
        /*Method get = net.minecraftforge.common.ForgeInternalHandler.class.getDeclaredMethod("getLootModifierManager");
        get.setAccessible(true);
        manager = get.invoke(null);
        *///?} else if <26.1 {
        /*Method get = net.neoforged.neoforge.common.NeoForgeEventHandler.class.getDeclaredMethod("getLootModifierManager");
        get.setAccessible(true);
        manager = get.invoke(null);
        *///?} else {
        /*manager = server.getServerResources().managers().getListener(NeoForgeReloadListeners.LOOT_MODIFIERS_KEY);
        *///?}
        for (String name : MAP_FIELDS) {
            Field field = field(manager.getClass(), name);
            if (field != null && field.get(manager) instanceof Map<?, ?> map) {
                return map;
            }
        }
        throw new NoSuchFieldException("the loaded loot modifiers in " + manager.getClass().getName());
    }

    /** A field of this class or one it extends, made readable, or null. */
    private static Field field(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException e) {
                // Look further up.
            }
        }
        return null;
    }
}
