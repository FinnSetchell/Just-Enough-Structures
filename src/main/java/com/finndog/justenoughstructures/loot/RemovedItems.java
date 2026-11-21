package com.finndog.justenoughstructures.loot;

import com.finndog.justenoughstructures.JesLog;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/**
 * Items another mod takes out of every chest and every player's inventory, which JES leaves out of what
 * loot gives, as they can't be had from a structure. That's Item Obliterator: on Fabric it empties them
 * from a container as it's filled, on Forge and NeoForge from a container a player opens, and on every
 * loader from a player's inventory (read in its jars: 2.3.0 and 3.1.1 for Fabric and Quilt, 2.3.1 for
 * Forge, 2.3.0 for NeoForge, 21 November 2026). It's asked through its own check of an item's id, so
 * its regexes count as they do for it. Without it, or when its check can't be found or throws,
 * nothing is removed.
 */
public final class RemovedItems {
    /** Item Obliterator's package in its Fabric and Quilt, Forge and NeoForge jars, and its check's class there. */
    private static final List<String[]> PLACES = List.of(
            new String[]{"elocindev.item_obliterator.fabric_quilt", "util.Utils"},
            new String[]{"elocindev.item_obliterator.forge", "utils.Utils"},
            new String[]{"elocindev.item_obliterator.neoforge", "utils.Utils"});

    /** Its check of an item's id, and its main class's field that holds its config. */
    private record Mod(Method check, Field config) {
    }

    /** What the check said of each item, for one config: it goes through every entry, and a regex is slow. */
    private record Known(Object config, Map<ResourceLocation, Boolean> answers) {
    }

    private static final Mod MOD = find();
    /** Read from the index's thread as well as the server's. A new config, as it reads on /reload, starts it over. */
    private static volatile Known known = new Known(null, new ConcurrentHashMap<>());

    private RemovedItems() {
    }

    public static boolean removed(Item item) {
        return MOD != null && removed(BuiltInRegistries.ITEM.getKey(item));
    }

    public static boolean removed(ResourceLocation id) {
        if (MOD == null) {
            return false;
        }
        Object config = config();
        Known now = known;
        if (now.config() != config) {
            now = new Known(config, new ConcurrentHashMap<>());
            known = now;
        }
        return now.answers().computeIfAbsent(id, RemovedItems::ask);
    }

    /** Leaves out of {@code items} the ids of items that are removed. */
    public static void dropFrom(Set<ResourceLocation> items) {
        if (MOD != null) {
            items.removeIf(RemovedItems::removed);
        }
    }

    /**
     * What decides which items are removed, for the name kept odds go under, so they're rolled again
     * when it changes: each setting of Item Obliterator's config. Empty without it.
     */
    public static String fingerprint() {
        if (MOD == null) {
            return "";
        }
        Object config = config();
        if (config == null) {
            return "item_obliterator";
        }
        StringBuilder out = new StringBuilder("item_obliterator");
        Field[] fields = config.getClass().getFields();
        Arrays.sort(fields, Comparator.comparing(Field::getName));
        try {
            for (Field f : fields) {
                if (!Modifier.isStatic(f.getModifiers())) {
                    out.append('|').append(f.getName()).append('=').append(f.get(config));
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            JesLog.debug("Couldn't read Item Obliterator's config", e);
        }
        return out.toString();
    }

    /** Forgets what the check said, after a /reload, as Item Obliterator may read its config again. */
    public static void forget() {
        known = new Known(null, new ConcurrentHashMap<>());
    }

    private static boolean ask(ResourceLocation id) {
        try {
            return Boolean.TRUE.equals(MOD.check().invoke(null, id.toString()));
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            JesLog.warnOnce("removed-items", "Couldn't ask Item Obliterator whether it removes {}, so JES lists the items it removes from chests", id, e);
            return false;
        }
    }

    private static Object config() {
        try {
            return MOD.config() == null ? null : MOD.config().get(null);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    private static Mod find() {
        for (String[] place : PLACES) {
            Class<?> check;
            try {
                check = Class.forName(place[0] + "." + place[1], false, RemovedItems.class.getClassLoader());
            } catch (ClassNotFoundException | LinkageError e) {
                continue;
            }
            try {
                Method isDisabled = check.getMethod("isDisabled", String.class);
                Field config = null;
                try {
                    config = Class.forName(place[0] + ".ItemObliterator", false, RemovedItems.class.getClassLoader()).getField("Config");
                } catch (ReflectiveOperationException | LinkageError e) {
                    JesLog.debug("Found Item Obliterator's check but not its config, so kept odds don't change with it", e);
                }
                JesLog.debug("Leaving out of loot the items Item Obliterator removes, through {}", check.getName());
                return new Mod(isDisabled, config);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                JesLog.warnOnce("removed-items-check", "Found Item Obliterator but not its check, so JES lists the items it removes from chests", e);
                return null;
            }
        }
        return null;
    }
}
