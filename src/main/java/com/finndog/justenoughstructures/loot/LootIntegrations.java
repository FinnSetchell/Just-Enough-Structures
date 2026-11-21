package com.finndog.justenoughstructures.loot;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JesLog;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * What Loot Integrations adds to loot tables as they're rolled, read from its own map instead of rolled
 * (read in its jars, 4.7 for 1.20.1 Fabric and Forge and for 1.21.1 NeoForge, 21 November 2026). For
 * each table it adds to, the map names the tables it rolls into it and how many of their stacks it
 * picks: into a structure's chest from a category such as lootintegrations:chests/village, and into a
 * category from vanilla chest tables, each rolled through it in turn. It picks with a random of its own
 * that no seed reaches, so rolls give a different few each time, and only listing what its tables can
 * give names the same items every time. It leaves out the items in its lootintegrations:ignored tag
 * and, while its skipMapItems is on (the default), exploration maps.
 */
public final class LootIntegrations {
    private static final String MANAGER = "com.lootintegrations.loot.LootModifierManager";
    private static final String MOD = "com.lootintegrations.LootintegrationsMod";
    private static final ResourceLocation IGNORED = Ids.of("lootintegrations", "ignored");

    /** A table it rolls into another, and how many of its stacks it picks. */
    public record Source(ResourceLocation table, int picks) {
    }

    /**
     * Its map: each table it adds to, with what it rolls into it. {@code leftOut} is the items it never
     * adds.
     */
    public record Listing(Map<ResourceLocation, List<Source>> map, Set<ResourceLocation> leftOut) {
        public static final Listing NONE = new Listing(Map.of(), Set.of());

        /**
         * Every table it rolls into {@code table}, and into those in turn, each once: it doesn't add to a
         * table again while adding to it. Sources it picks none from add nothing.
         */
        public Set<ResourceLocation> sourcesOf(ResourceLocation table) {
            Set<ResourceLocation> out = new TreeSet<>();
            Deque<ResourceLocation> next = new ArrayDeque<>(List.of(table));
            while (!next.isEmpty()) {
                for (Source source : map.getOrDefault(next.pop(), List.of())) {
                    if (source.picks() > 0 && !source.table().equals(table) && out.add(source.table())) {
                        next.push(source.table());
                    }
                }
            }
            return out;
        }

        /** Whether everything it does to {@code table} is to add stacks, which listing shows; not to only drop some. */
        public boolean onlyAdds(ResourceLocation table) {
            List<Source> sources = map.get(table);
            return sources != null && !sources.isEmpty() && sources.stream().allMatch(s -> s.picks() > 0);
        }
    }

    private LootIntegrations() {
    }

    /**
     * Reads its map as it is now. Nothing without the mod, or if the map can't be read, in which case
     * the tables it adds to are rolled as before. Its map is filled again on /reload, on the server
     * thread, so it's read after that.
     */
    public static Listing read() {
        Class<?> manager;
        try {
            manager = Class.forName(MANAGER, false, LootIntegrations.class.getClassLoader());
        } catch (ClassNotFoundException | LinkageError e) {
            return Listing.NONE;
        }
        try {
            Map<?, ?> options = (Map<?, ?>) manager.getField("lootOptionsMap").get(null);
            Map<ResourceLocation, List<Source>> map = new TreeMap<>();
            for (Map.Entry<?, ?> entry : List.copyOf(options.entrySet())) {
                if (!(entry.getKey() instanceof ResourceLocation target) || !(entry.getValue() instanceof List<?> modifiers)) {
                    continue;
                }
                List<Source> sources = new ArrayList<>();
                for (Object modifier : List.copyOf(modifiers)) {
                    Object table = modifier.getClass().getField("lootTableId").get(modifier);
                    Object picks = ((Map<?, ?>) modifier.getClass().getField("integratedTables").get(modifier)).get(target);
                    if (table instanceof ResourceLocation id) {
                        sources.add(new Source(id, picks instanceof Integer n ? n : 0));
                    }
                }
                map.put(target, sources);
            }
            Set<ResourceLocation> leftOut = new TreeSet<>();
            for (Holder<Item> item : BuiltInRegistries.ITEM.getTagOrEmpty(TagKey.create(Registries.ITEM, IGNORED))) {
                item.unwrapKey().ifPresent(key -> leftOut.add(Ids.of(key)));
            }
            if (skipsMaps()) {
                leftOut.add(Ids.parse("filled_map"));
            }
            return new Listing(map, leftOut);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            JesLog.warnOnce("loot-integrations", "Couldn't read what Loot Integrations adds to loot tables, so those tables are rolled as before", e);
            return Listing.NONE;
        }
    }

    /** Its skipMapItems setting: whether exploration maps are left out of what it adds. On, its default, unless it says otherwise. */
    private static boolean skipsMaps() {
        try {
            Object config = Class.forName(MOD, false, LootIntegrations.class.getClassLoader()).getField("config").get(null);
            Object common = config.getClass().getMethod("getCommonConfig").invoke(config);
            return common.getClass().getField("skipMapItems").getBoolean(common);
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return true;
        }
    }
}
