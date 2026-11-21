package com.lootintegrations.loot;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.resources.ResourceLocation;

/**
 * A stand-in for Loot Integrations' list of what it adds, for the game tests, which don't have the mod.
 * The real one is in the same place in its jars, 4.7 for 1.20.1 Fabric and Forge and for 1.21.1
 * NeoForge (read with javap, 21 November 2026), and is filled from data files as data loads; it adds
 * to tables as they're rolled, which this doesn't. Nothing is listed until a test lists it. Read from
 * other threads, as the loot index is built on its own.
 */
public class LootModifierManager {
    public static final Map<ResourceLocation, List<GlobalLootModifierIntegration>> lootOptionsMap = new ConcurrentHashMap<>();
}
