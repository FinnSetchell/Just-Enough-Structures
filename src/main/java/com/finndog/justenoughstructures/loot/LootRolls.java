package com.finndog.justenoughstructures.loot;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JesLog;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
//? if >=1.21 {
/*import it.unimi.dsi.fastutil.objects.Object2IntMap;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.enchantment.Enchantment;
*///?} else {
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
//?}
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;

/**
 * Rolls loot tables with the game's own loot code, so functions and conditions added by any mod
 * behave exactly as they do in a real chest.
 */
public final class LootRolls {
    private LootRolls() {
    }

    public static boolean exists(ServerLevel level, ResourceLocation tableId) {
        return table(level.getServer(), tableId) != LootTable.EMPTY;
    }

    /** The loaded loot table by that name, or {@link LootTable#EMPTY} if there isn't one. */
    public static LootTable table(MinecraftServer server, ResourceLocation tableId) {
        //? if >=1.21 {
        /*return server.reloadableRegistries().getLootTable(ResourceKey.create(Registries.LOOT_TABLE, tableId));
        *///?} else {
        return server.getLootData().getLootTable(tableId);
        //?}
    }

    /** Fills a container of {@code size} slots the way a chest is filled when it's first opened. */
    public static List<ItemStack> fill(ServerLevel level, ResourceLocation tableId, long seed, int size) {
        return fill(level, table(level.getServer(), tableId), seed, size);
    }

    /** The same for a table that isn't loaded, like an edit that hasn't been saved yet. */
    public static List<ItemStack> fill(ServerLevel level, LootTable table, long seed, int size) {
        return JesLog.quietly(() -> {
            SimpleContainer container = new SimpleContainer(size);
            table.fill(container, chestParams(level), seed);
            List<ItemStack> out = new ArrayList<>(size);
            for (int i = 0; i < size; i++) {
                out.add(container.getItem(i).copy());
            }
            return out;
        });
    }

    /** Rolls the table {@code rolls} times and counts how often each item turned up. */
    public static LootOdds odds(ServerLevel level, ResourceLocation tableId, int rolls, long seed) {
        return odds(level, tableId, table(level.getServer(), tableId), rolls, seed);
    }

    /** The same for a table that isn't loaded, like an edit that hasn't been saved yet. */
    public static LootOdds odds(ServerLevel level, ResourceLocation tableId, LootTable table, int rolls, long seed) {
        Roller roller = new Roller(level, tableId, table, rolls, seed);
        roller.rollFor(Long.MAX_VALUE);
        return roller.odds();
    }

    /**
     * Odds worked out a few rolls at a time. Some mods' loot changes make every roll slow, and
     * thousands in one go would hold the server up.
     */
    public static final class Roller {
        private final ResourceLocation tableId;
        private final LootTable table;
        private final LootParams params;
        private final int rolls;
        private final long seed;
        private final Map<Item, LootOdds.Row> rows = new HashMap<>();
        private int done;
        private int empty;

        public Roller(ServerLevel level, ResourceLocation tableId, int rolls, long seed) {
            this(level, tableId, table(level.getServer(), tableId), rolls, seed);
        }

        public Roller(ServerLevel level, ResourceLocation tableId, LootTable table, int rolls, long seed) {
            this.tableId = tableId;
            this.table = table;
            this.params = chestParams(level);
            this.rolls = rolls;
            this.seed = seed;
        }

        /** Rolls for about {@code nanos}, at least once if any are left. True once every roll is done. */
        public boolean rollFor(long nanos) {
            long started = System.nanoTime();
            // Thousands of rolls of another mod's table can mean thousands of the same warning.
            JesLog.quietly(() -> {
                while (done < rolls) {
                    rollOnce();
                    if (System.nanoTime() - started >= nanos) {
                        break;
                    }
                }
            });
            return done >= rolls;
        }

        /** The odds from the rolls so far. */
        public LootOdds odds() {
            List<LootOdds.Row> sorted = new ArrayList<>(rows.values());
            sorted.sort((a, b) -> b.hits() != a.hits() ? Integer.compare(b.hits(), a.hits())
                    : a.example().getItem().getDescriptionId().compareTo(b.example().getItem().getDescriptionId()));
            return new LootOdds(tableId, done, empty, sorted);
        }

        private void rollOnce() {
            int i = done++;
            List<ItemStack> rolled;
            try {
                rolled = table.getRandomItems(params, seed + i);
            } catch (RuntimeException | LinkageError e) {
                // Still one of the rolls, as one that gave nothing, so the odds so far add up.
                empty++;
                throw e;
            }
            Map<Item, Integer> counts = new HashMap<>();
            Map<Item, ItemStack> examples = new HashMap<>();
            for (ItemStack stack : rolled) {
                if (stack.isEmpty()) {
                    continue;
                }
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
                examples.putIfAbsent(stack.getItem(), stack);
            }
            if (counts.isEmpty()) {
                empty++;
            }
            for (Map.Entry<Item, Integer> e : counts.entrySet()) {
                rows.computeIfAbsent(e.getKey(), item -> new LootOdds.Row(examples.get(item).copyWithCount(1)))
                        .record(e.getValue());
            }
            for (ItemStack stack : rolled) {
                LootOdds.Row row = rows.get(stack.getItem());
                if (row == null) {
                    continue;
                }
                //? if >=1.21 {
                /*for (Object2IntMap.Entry<Holder<Enchantment>> e : EnchantmentHelper.getEnchantmentsForCrafting(stack).entrySet()) {
                    e.getKey().unwrapKey().ifPresent(key -> row.variant("enchantment:" + Ids.of(key), e.getIntValue()));
                }
                PotionContents potion = stack.get(DataComponents.POTION_CONTENTS);
                if (potion != null) {
                    potion.potion().flatMap(Holder::unwrapKey).ifPresent(key -> row.variant("potion:" + Ids.of(key), 0));
                }
                *///?} else {
                EnchantmentHelper.getEnchantments(stack).forEach((enchantment, enchantmentLevel) ->
                        row.variant("enchantment:" + BuiltInRegistries.ENCHANTMENT.getKey(enchantment), enchantmentLevel));
                Potion potion = PotionUtils.getPotion(stack);
                if (potion != Potions.EMPTY) {
                    row.variant("potion:" + BuiltInRegistries.POTION.getKey(potion), 0);
                }
                //?}
            }
        }
    }

    // Deliberately no origin. With one, exploration_map searches the real world for the nearest
    // structure on every roll, which is the slow treasure map lookup, thousands of times over for the
    // odds. Without it maps come out blank but keep their name.
    private static LootParams chestParams(ServerLevel level) {
        return new LootParams.Builder(level).create(LootContextParamSets.EMPTY);
    }
}
