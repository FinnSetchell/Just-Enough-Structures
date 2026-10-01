package com.finndog.justenoughstructures.loot;

import com.finndog.justenoughstructures.JesLog;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.alchemy.Potion;
import net.minecraft.world.item.alchemy.PotionUtils;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
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
        return level.getServer().getLootData().getLootTable(tableId) != LootTable.EMPTY;
    }

    /** Fills a container of {@code size} slots the way a chest is filled when it's first opened. */
    public static List<ItemStack> fill(ServerLevel level, ResourceLocation tableId, long seed, int size) {
        return fill(level, level.getServer().getLootData().getLootTable(tableId), seed, size);
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
        return odds(level, tableId, level.getServer().getLootData().getLootTable(tableId), rolls, seed);
    }

    /** The same for a table that isn't loaded, like an edit that hasn't been saved yet. */
    public static LootOdds odds(ServerLevel level, ResourceLocation tableId, LootTable table, int rolls, long seed) {
        // Thousands of rolls of another mod's table can mean thousands of the same warning.
        return JesLog.quietly(() -> roll(level, tableId, table, rolls, seed));
    }

    private static LootOdds roll(ServerLevel level, ResourceLocation tableId, LootTable table, int rolls, long seed) {
        LootParams params = chestParams(level);
        Map<Item, LootOdds.Row> rows = new HashMap<>();
        int empty = 0;
        for (int i = 0; i < rolls; i++) {
            Map<Item, Integer> counts = new HashMap<>();
            Map<Item, ItemStack> examples = new HashMap<>();
            List<ItemStack> rolled = table.getRandomItems(params, seed + i);
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
                EnchantmentHelper.getEnchantments(stack).forEach((enchantment, enchantmentLevel) ->
                        row.variant("enchantment:" + BuiltInRegistries.ENCHANTMENT.getKey(enchantment), enchantmentLevel));
                Potion potion = PotionUtils.getPotion(stack);
                if (potion != Potions.EMPTY) {
                    row.variant("potion:" + BuiltInRegistries.POTION.getKey(potion), 0);
                }
            }
        }
        List<LootOdds.Row> sorted = new ArrayList<>(rows.values());
        sorted.sort((a, b) -> b.hits() != a.hits() ? Integer.compare(b.hits(), a.hits())
                : a.example().getDescriptionId().compareTo(b.example().getDescriptionId()));
        return new LootOdds(tableId, rolls, empty, sorted);
    }

    // Deliberately no origin. With one, exploration_map searches the real world for the nearest
    // structure on every roll, which is the slow treasure map lookup, thousands of times over for the
    // odds. Without it maps come out blank but keep their name.
    private static LootParams chestParams(ServerLevel level) {
        return new LootParams.Builder(level).create(LootContextParamSets.EMPTY);
    }
}
