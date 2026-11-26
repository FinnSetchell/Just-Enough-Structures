package com.finndog.justenoughstructures.server;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.loot.LootIntegrations;
import com.finndog.justenoughstructures.loot.LootModifiers;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;

/**
 * Which loot tables other mods' code can change as they're rolled, as far as can be told without
 * rolling: the loot index rolls those in full, and the rest only until their first rolls fit what they
 * give worked out. Read from the mods' own lists, so a mod that changes a table only now and then is
 * still found, the same way on every loader:
 *
 * <ul>
 * <li>Global loot modifiers: Forge's, NeoForge's, or Porting Lib's port of them on Fabric (as in
 * Prominence II), read by {@link LootModifiers}. Each names its table with a loot_table_id condition,
 * or acts on none in a chest, as its conditions need something a chest never has. Any other could act
 * on every table.</li>
 * <li>Loot Integrations' map of the tables it adds to. Where it only adds, the loot index lists what
 * it adds from the map ({@link LootIntegrations}), so a few rolls that give nothing more are enough.
 * Where it may only take stacks out, the table is rolled in full.</li>
 * </ul>
 *
 * <p>Fabric's MODIFY_DROPS event (from 26.1) and LootJS aren't listed here, so with either of them
 * every table may be changed, and every table is rolled in full, as before. Anything that can't be
 * read counts the same way.
 */
public final class LootHooks {
    /**
     * What's known: the tables mod code changes as they're rolled, or that it may change any table, what
     * Loot Integrations adds to tables, and the loaded loot modifiers.
     */
    public record Found(Set<ResourceLocation> tables, boolean anyTable, LootIntegrations.Listing integrations, LootModifiers.Listing modifiers) {
        public boolean touches(ResourceLocation table) {
            return anyTable || tables.contains(table);
        }

        /** Whether all that's changed in the table as it's rolled is what Loot Integrations adds, which the index lists. */
        public boolean listed(ResourceLocation table) {
            return !touches(table) && integrations.onlyAdds(table);
        }

        /**
         * For each table a loot modifier names as the one it acts on, the item and loot table ids its
         * modifiers name besides: what they add, without rolling. They may also name what they take out
         * or swap, and a modifier that names its table only in its code isn't here; rolls show those.
         */
        public Map<ResourceLocation, Set<ResourceLocation>> added() {
            Map<ResourceLocation, Set<ResourceLocation>> out = new TreeMap<>();
            for (LootModifiers.Modifier m : modifiers.all()) {
                if (m.tables() == null) {
                    continue;
                }
                for (ResourceLocation table : m.tables()) {
                    Set<ResourceLocation> ids = out.computeIfAbsent(table, t -> new TreeSet<>());
                    ids.addAll(m.items());
                    ids.addAll(m.namedTables());
                }
            }
            return out;
        }

        /**
         * The items loot modifiers add to {@code table} as read: the items they name, and what each
         * table they name gives by {@code readItems}, one level deep.
         */
        public Set<ResourceLocation> addedItems(ResourceLocation table, Function<ResourceLocation, Set<ResourceLocation>> readItems) {
            Set<ResourceLocation> out = new TreeSet<>();
            for (LootModifiers.Modifier m : modifiers.on(table)) {
                out.addAll(m.items());
                for (ResourceLocation named : m.namedTables()) {
                    out.addAll(readItems.apply(named));
                }
            }
            return out;
        }
    }

    private LootHooks() {
    }

    /** Looks now. On the server thread, after the server's data has loaded. */
    public static Found find(MinecraftServer server) {
        LootIntegrations.Listing integrations = LootIntegrations.read();
        LootModifiers.Listing modifiers = LootModifiers.read(server);
        Set<ResourceLocation> tables = new TreeSet<>();
        try {
            // The tables named are rolled in full whatever else may change any table.
            tables.addAll(modifiers.tables());
            for (ResourceLocation table : integrations.map().keySet()) {
                if (!integrations.onlyAdds(table)) {
                    tables.add(table);
                }
            }
            List<LootModifiers.Modifier> never = modifiers.all().stream().filter(LootModifiers.Modifier::neverInChests).toList();
            if (!never.isEmpty()) {
                JesLog.debug("{} loot modifiers never act as a chest is filled, as each needs something a chest's loot never has: {}",
                        never.size(), never.stream().map(m -> m.id() + " (" + m.type() + ")").toList());
            }
            List<LootModifiers.Modifier> anyTable = modifiers.anyTable();
            if (!anyTable.isEmpty()) {
                JesLog.debug("{} loot modifiers name no loot table, so any may be changed: {}", anyTable.size(),
                        anyTable.stream().map(m -> m.id() + " (" + m.type() + ")").toList());
            }
            boolean any = !modifiers.readable() || !anyTable.isEmpty() || present("com.almostreliable.lootjs.LootModificationsAPI")
                    || modifyDropsListened();
            return new Found(tables, any, integrations, modifiers);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            JesLog.debug("Couldn't read which loot tables other mods change as they're rolled, so every table is rolled in full", e);
            return new Found(tables, true, integrations, modifiers);
        }
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
