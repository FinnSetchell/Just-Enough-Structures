package com.finndog.justenoughstructures.client;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * A structure compass mod the browser can hand a structure to. The loader sets one when such a
 * mod is installed. The compass then does everything itself: searching, its costs and its rules.
 */
public interface CompassLink {
    /** A still 16x16 picture of the compass, for the browser's button. */
    ResourceLocation icon();

    /** Whether the player holds a compass this can open. */
    boolean holding(Player player);

    /** Opens the held compass's own screen with this structure picked. False if it can't right now. */
    boolean open(Player player, ResourceLocation structure);

    /** What the held compass says about this structure, or null when it's set to something else. */
    Component status(Player player, ResourceLocation structure);

    final class Holder {
        private static CompassLink link;

        private Holder() {
        }
    }

    static CompassLink get() {
        return Holder.link;
    }

    static void set(CompassLink link) {
        Holder.link = link;
    }
}
