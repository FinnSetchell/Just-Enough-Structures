package com.finndog.justenoughstructures.gametest.fabric;

import net.fabricmc.api.ModInitializer;
//? if >=26.1 {
/*import com.finndog.justenoughstructures.Ids;
import net.fabricmc.fabric.api.loot.v3.LootTableEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.LootTable;
*///?}

/**
 * Adds a nether star to what every ancient city ice box gives, through Fabric's event for changing
 * what a loot table drops as it's rolled, which Fabric API has from 26.1. Forge and NeoForge do the
 * same with a loot modifier. For the game test of what Found in lists.
 */
public final class ChangedDrops implements ModInitializer {
    @Override
    public void onInitialize() {
        //? if >=26.1 {
        /*ResourceKey<LootTable> iceBox = ResourceKey.create(Registries.LOOT_TABLE, Ids.parse("chests/ancient_city_ice_box"));
        LootTableEvents.MODIFY_DROPS.register((table, context, drops) -> {
            if (table.is(iceBox)) {
                drops.add(new ItemStack(Items.NETHER_STAR));
            }
        });
        *///?}
    }
}
