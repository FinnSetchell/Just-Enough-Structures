package com.finndog.justenoughstructures.gametest;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.function.Supplier;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootPool;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.entries.LootItem;
import net.minecraft.world.level.storage.loot.functions.LootItemFunction;

/**
 * Loot tables that fail the way another mod's loot can: one item, with a loot function that throws
 * as soon as it's rolled. The function is put together as the game runs rather than written out as
 * a class, so it's the same on every version of the game. Each table is named the way the game
 * names a table it loads, so it rolls like one.
 */
final class ThrowingLoot {
    /** How many times one of these tables has thrown, so a test can tell its table really was rolled. */
    static int thrown;

    private ThrowingLoot() {
    }

    /** A table named {@code id} whose every roll throws what {@code failure} gives. */
    static LootTable table(ResourceLocation id, Supplier<Throwable> failure) {
        return sometimes(id, 1, failure);
    }

    /**
     * A table named {@code id} of one stone a roll, except that every {@code every}th roll throws what
     * {@code failure} gives, as loot does that fails now and then.
     */
    static LootTable sometimes(ResourceLocation id, int every, Supplier<Throwable> failure) {
        int[] calls = new int[1];
        LootItemFunction function = (LootItemFunction) Proxy.newProxyInstance(LootItemFunction.class.getClassLoader(),
                new Class<?>[]{LootItemFunction.class}, (proxy, method, args) -> {
                    switch (method.getName()) {
                        case "apply":
                            if (++calls[0] % every != 0) {
                                return args[0];
                            }
                            thrown++;
                            throw failure.get();
                        case "hashCode":
                            return System.identityHashCode(proxy);
                        case "equals":
                            return proxy == args[0];
                        case "toString":
                            return "a loot function that throws";
                        default:
                            return method.isDefault() ? InvocationHandler.invokeDefault(proxy, method, args) : null;
                    }
                });
        LootTable table = LootTable.lootTable().withPool(LootPool.lootPool().add(LootItem.lootTableItem(Items.STONE).apply(() -> function))).build();
        //? if neoforge {
        /*// As NeoForge names every table it loads, and LootOverrides.named a draft: from 26.3 a roll
        // fails without a name, as it's looked up for loot modifiers once the table has been rolled.
        table.setLootTableId(id);
        *///?}
        return table;
    }
}
