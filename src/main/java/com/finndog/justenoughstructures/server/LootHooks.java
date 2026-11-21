package com.finndog.justenoughstructures.server;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.loot.LootIntegrations;
import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

/**
 * Which loot tables other mods' code can change as they're rolled, as far as can be told without
 * rolling: the loot index rolls those in full, and the rest only until their first rolls fit what they
 * give worked out. Read from the mods' own lists, so a mod that changes a table only now and then is
 * still found:
 *
 * <ul>
 * <li>Porting Lib's loot modifiers (Forge's, ported to Fabric, as in Prominence II): each names its
 * table with a loot_table_id condition. One that needs a killer, a tool, a block or a place never acts
 * in JES's rolls, which have none of those. Any other could act on every table.</li>
 * <li>Loot Integrations' map of the tables it adds to. Where it only adds, the loot index lists what
 * it adds from the map ({@link LootIntegrations}), so a few rolls that give nothing more are enough.
 * Where it may only take stacks out, the table is rolled in full.</li>
 * </ul>
 *
 * <p>Forge's and NeoForge's own loot modifiers, Fabric's MODIFY_DROPS event (from 26.1) and LootJS
 * aren't listed here, so with any of them every table may be changed, and every table is rolled in full,
 * as before. Anything that can't be read counts the same way.
 */
public final class LootHooks {
    /** Conditions JES's rolls never pass: each needs something these rolls don't have. */
    private static final Set<String> NEVER_IN_JES_ROLLS = Set.of("minecraft:killed_by_player", "minecraft:match_tool",
            "minecraft:entity_properties", "minecraft:block_state_property", "minecraft:damage_source_properties", "minecraft:location_check",
            "minecraft:entity_scores", "porting_lib:can_tool_perform_action", "forge:can_tool_perform_action");

    /**
     * What's known: the tables mod code changes as they're rolled, or that it may change any table, and
     * what Loot Integrations adds to tables.
     */
    public record Found(Set<ResourceLocation> tables, boolean anyTable, LootIntegrations.Listing integrations) {
        public boolean touches(ResourceLocation table) {
            return anyTable || tables.contains(table);
        }

        /** Whether all that's changed in the table as it's rolled is what Loot Integrations adds, which the index lists. */
        public boolean listed(ResourceLocation table) {
            return !touches(table) && integrations.onlyAdds(table);
        }
    }

    private LootHooks() {
    }

    /** Looks now. On the server thread, after the server's data has loaded. */
    public static Found find() {
        LootIntegrations.Listing integrations = LootIntegrations.read();
        //? if fabric {
        Set<ResourceLocation> tables = new TreeSet<>();
        try {
            if (present("com.almostreliable.lootjs.LootModificationsAPI") || modifyDropsListened()) {
                return new Found(tables, true, integrations);
            }
            if (!portingLib(tables)) {
                return new Found(tables, true, integrations);
            }
            for (ResourceLocation table : integrations.map().keySet()) {
                if (!integrations.onlyAdds(table)) {
                    tables.add(table);
                }
            }
            return new Found(tables, false, integrations);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            JesLog.debug("Couldn't read which loot tables other mods change as they're rolled, so every table is rolled in full", e);
            return new Found(tables, true, integrations);
        }
        //?} else {
        /*// Forge's and NeoForge's own loot modifiers aren't read here.
        return new Found(Set.of(), true, integrations);
        *///?}
    }

    private static boolean present(String className) {
        return LootHooks.class.getClassLoader().getResource(className.replace('.', '/') + ".class") != null;
    }

    /** Whether anything listens to Fabric's MODIFY_DROPS, which changes what any table gives as it's rolled. */
    private static boolean modifyDropsListened() throws ReflectiveOperationException {
        //? if fabric && >=26.1 {
        /*Object event = net.fabricmc.fabric.api.loot.v3.LootTableEvents.MODIFY_DROPS;
        Field handlers = field(event.getClass(), "handlers");
        Object[] listening = (Object[]) handlers.get(event);
        return listening != null && listening.length > 0;
        *///?} else {
        return false;
        //?}
    }

    /** Adds the tables Porting Lib's loot modifiers name. False if one may act on any table, or they can't be read. */
    private static boolean portingLib(Set<ResourceLocation> tables) throws ReflectiveOperationException {
        String manager = "io.github.fabricators_of_create.porting_lib.loot.LootModifierManager";
        if (!present(manager)) {
            return true;
        }
        Class<?> managerClass = Class.forName(manager);
        Object instance = managerClass.getMethod("getLootModifierManager").invoke(null);
        Collection<?> modifiers = (Collection<?>) managerClass.getMethod("getAllLootMods").invoke(instance);
        for (Object modifier : modifiers) {
            Field conditionsField = field(modifier.getClass(), "conditions");
            if (conditionsField == null) {
                JesLog.debug("A loot modifier with no conditions to read ({}) may change any loot table", modifier.getClass().getName());
                return false;
            }
            Object[] conditions = (Object[]) conditionsField.get(modifier);
            ResourceLocation target = null;
            boolean never = false;
            for (Object c : conditions) {
                String type = String.valueOf(typeOf((LootItemCondition) c));
                if (type.endsWith(":loot_table_id")) {
                    Field id = field(c.getClass(), "targetLootTableId");
                    target = id == null ? null : (ResourceLocation) id.get(c);
                    if (target == null) {
                        return false;
                    }
                } else if (NEVER_IN_JES_ROLLS.contains(type)) {
                    never = true;
                }
            }
            if (target != null) {
                tables.add(target);
            } else if (!never) {
                JesLog.debug("The loot modifier {} names no loot table, so it may change any", modifier.getClass().getName());
                return false;
            }
        }
        return true;
    }

    private static ResourceLocation typeOf(LootItemCondition condition) {
        //? if >=26.1 {
        /*return BuiltInRegistries.LOOT_CONDITION_TYPE.getKey(condition.codec());
        *///?} else {
        return BuiltInRegistries.LOOT_CONDITION_TYPE.getKey(condition.getType());
        //?}
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
