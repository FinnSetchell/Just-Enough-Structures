package com.finndog.justenoughstructures;

import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
//? if >=26.1 {
/*import net.minecraft.server.permissions.Permission;
import net.minecraft.server.permissions.PermissionLevel;
*///?}

/** Player queries JES needs, the same on every version. */
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

    /** Runs {@code action} for each of these players who's still online, and forgets those who've left. */
    public static void forEachOnline(MinecraftServer server, Set<UUID> players, Consumer<ServerPlayer> action) {
        for (UUID id : players) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player == null) {
                players.remove(id);
            } else {
                action.accept(player);
            }
        }
    }

    /**
     * Whether the player has this command permission level, 0 to 4 as in server.properties. A level
     * above 4 matches no one, since 26.1's PermissionLevel.byId would clamp it to 4.
     */
    public static boolean hasPermission(Player player, int level) {
        //? if >=26.1 {
        /*return level <= PermissionLevel.OWNERS.id()
                && player.permissions().hasPermission(new Permission.HasCommandLevel(PermissionLevel.byId(level)));
        *///?} else {
        return player.hasPermissions(level);
        //?}
    }

    /** Whether the player hosts this single player or LAN world. */
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
