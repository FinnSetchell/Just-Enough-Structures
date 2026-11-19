package com.finndog.justenoughstructures.compat.foundin;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.client.ClientState;
import com.finndog.justenoughstructures.client.ClientState.ChestLoot;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Which chest loot tabs the JEI, EMI and REI pages show, as the player chose in the settings: JES's
 * Found in structures, other mods' chest loot tabs, or both. Other mods' tabs are known by their ids
 * alone, with no compile dependency on those mods, so a tab that isn't there is simply never hidden.
 * Kept free of the viewers' classes so it can be tested without them.
 */
public final class ChestLootTabs {
    /**
     * Other mods' chest loot tabs: EMI Loot's (EMI only), Advanced Loot Info's (all three viewers)
     * and Just Enough Resources' dungeon chests (JEI only).
     */
    public static final Set<String> OTHER_TABS = Set.of(
            "emi_loot:chest_loot", "emi_loot:trial_chamber",
            "ali:chest_loot", "ali:trial_chambers",
            "jeresources:dungeon");

    private static final List<Runnable> LISTENERS = new CopyOnWriteArrayList<>();

    private ChestLootTabs() {
    }

    /** What the player chose. Safe from any thread. */
    public static ChestLoot chosen() {
        ClientState.load();
        return ClientState.chestLoot;
    }

    /**
     * What the viewers show: what the player chose, or with JES set to Command only, other mods' tabs
     * alone and none of them hidden, as if JES weren't there.
     */
    private static ChestLoot shown() {
        ClientState.load();
        return ClientState.commandOnly ? ChestLoot.OTHER_MODS : ClientState.chestLoot;
    }

    /** Whether JES's own Found in structures rows are shown. */
    public static boolean showsOwn() {
        return shown() != ChestLoot.OTHER_MODS;
    }

    /** Whether any other mod's chest loot tab is hidden. */
    public static boolean hidesOthers() {
        return shown() == ChestLoot.JUST_ENOUGH_STRUCTURES;
    }

    /** Whether the viewer tab or category with this id, written {@code namespace:path}, is hidden. */
    public static boolean hides(String id) {
        return hidesOthers() && OTHER_TABS.contains(id);
    }

    /** Runs {@code listener} on the client thread each time the player changes the setting. */
    public static void onChange(Runnable listener) {
        LISTENERS.add(listener);
    }

    /**
     * Sets what the player chose. If that changes what the viewers show, each is told, to show it at
     * once where it can. With Command only on it changes nothing they show until that goes off.
     */
    public static void choose(ChestLoot value) {
        ClientState.load();
        if (value == null || value == ClientState.chestLoot) {
            return;
        }
        ChestLoot before = shown();
        ClientState.chestLoot = value;
        if (shown() != before) {
            tell();
        }
    }

    /**
     * Turns Command only on or off. The viewers are told when what they show changes, so JES's tabs
     * go or come back at once where they can.
     */
    public static void setCommandOnly(boolean on) {
        ClientState.load();
        if (on == ClientState.commandOnly) {
            return;
        }
        ChestLoot before = shown();
        ClientState.commandOnly = on;
        if (shown() != before) {
            tell();
        }
    }

    private static void tell() {
        for (Runnable listener : LISTENERS) {
            try {
                listener.run();
            } catch (RuntimeException | LinkageError e) {
                JesLog.warnOnce("chest-loot-tabs", "Couldn't show the new chest loot tab setting in a recipe viewer; it applies when the world is joined again", e);
            }
        }
    }
}
