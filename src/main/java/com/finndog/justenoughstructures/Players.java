package com.finndog.justenoughstructures;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
//? if >=1.21.2 {
/*import java.util.Set;
*///?}
//? if >=26.1 {
/*import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
*///?}

/** What JES asks about players, the same way on every version. */
public final class Players {
    private Players() {
    }

    public static ServerLevel level(ServerPlayer player) {
        //? if >=26.1 {
        /*return player.level();
        *///?} else {
        return player.serverLevel();
        //?}
    }

    public static MinecraftServer server(ServerPlayer player) {
        return level(player).getServer();
    }

    /**
     * Whether the player has this command permission level, 0 to 4 as in server.properties. Higher
     * than 4 is no one, as it always was: 26.1 would round it down to 4.
     */
    public static boolean hasPermission(Player player, int level) {
        //? if >=26.1 {
        /*return level <= PermissionLevel.OWNERS.id()
                && player.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(level)));
        *///?} else {
        return player.hasPermissions(level);
        //?}
    }

    /** Whether the player is the one whose single player world, or world opened to LAN, this is. */
    public static boolean isSingleplayerOwner(MinecraftServer server, ServerPlayer player) {
        //? if >=26.1 {
        /*return server.isSingleplayerOwner(player.nameAndId());
        *///?} else {
        return server.isSingleplayerOwner(player.getGameProfile());
        //?}
    }

    /** Moves the player there, facing the way they already were. */
    public static void teleport(ServerPlayer player, ServerLevel level, double x, double y, double z) {
        //? if >=1.21.2 {
        /*player.teleportTo(level, x, y, z, Set.of(), player.getYRot(), player.getXRot(), true);
        *///?} else {
        player.teleportTo(level, x, y, z, player.getYRot(), player.getXRot());
        //?}
    }
}
