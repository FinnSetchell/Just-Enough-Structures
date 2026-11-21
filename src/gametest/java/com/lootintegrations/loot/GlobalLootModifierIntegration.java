package com.lootintegrations.loot;

import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/**
 * A stand-in for one of Loot Integrations' data files, with the real one's public fields (see
 * LootModifierManager): the table it rolls, and how many of its stacks it picks into each table it
 * adds to. The real one is made only from its file.
 */
public class GlobalLootModifierIntegration {
    public ResourceLocation lootTableId;
    public Map<ResourceLocation, Integer> integratedTables;

    public GlobalLootModifierIntegration(ResourceLocation lootTableId, Map<ResourceLocation, Integer> integratedTables) {
        this.lootTableId = lootTableId;
        this.integratedTables = integratedTables;
    }
}
