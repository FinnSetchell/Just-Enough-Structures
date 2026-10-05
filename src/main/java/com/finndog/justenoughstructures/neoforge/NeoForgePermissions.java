package com.finndog.justenoughstructures.neoforge;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.server.PackToolsAccess;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.server.permission.PermissionAPI;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;
import net.neoforged.neoforge.server.permission.nodes.PermissionNode;
import net.neoforged.neoforge.server.permission.nodes.PermissionTypes;

/**
 * Asks NeoForge's permission system about Pack tools, which permissions mods like LuckPerms answer.
 * Without one it says no, and JES's own rules decide, as on the other loaders.
 */
final class NeoForgePermissions {
    /** justenoughstructures.pack_tools, the same node as on the other loaders. */
    private static final PermissionNode<Boolean> PACK_TOOLS = new PermissionNode<>(JustEnoughStructures.MOD_ID, "pack_tools",
            PermissionTypes.BOOLEAN, (player, uuid, context) -> false);

    private NeoForgePermissions() {
    }

    static void install() {
        NeoForge.EVENT_BUS.addListener((PermissionGatherEvent.Nodes event) -> event.addNodes(PACK_TOOLS));
        PackToolsAccess.setPermissions((player, node) -> PermissionAPI.getPermission(player, PACK_TOOLS));
    }
}
