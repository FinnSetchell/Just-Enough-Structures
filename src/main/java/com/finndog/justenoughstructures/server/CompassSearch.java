package com.finndog.justenoughstructures.server;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** A structure compass mod the server can start searching. The loader sets one when such a mod is installed. */
public interface CompassSearch {
    /** Sets the compass in the player's hand searching for the structure, or says why it can't. */
    Component search(ServerPlayer player, ResourceLocation structure);
}
