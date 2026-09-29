package com.finndog.justenoughstructures.loot;

import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** How often each item came out of a loot table over a number of rolls. */
public record LootOdds(ResourceLocation tableId, int rolls, int emptyRolls, List<Row> rows) {

    public static final class Row {
        private final ItemStack example;
        private int hits;
        private int total;
        private int min = Integer.MAX_VALUE;
        private int max;

        public Row(ItemStack example) {
            this.example = example;
        }

        public Row(ItemStack example, int hits, int total, int min, int max) {
            this.example = example;
            this.hits = hits;
            this.total = total;
            this.min = min;
            this.max = max;
        }

        void record(int count) {
            hits++;
            total += count;
            min = Math.min(min, count);
            max = Math.max(max, count);
        }

        public ItemStack example() {
            return example;
        }

        /** Number of rolls the item appeared in at all. */
        public int hits() {
            return hits;
        }

        public int total() {
            return total;
        }

        public int min() {
            return min;
        }

        public int max() {
            return max;
        }
    }
}
