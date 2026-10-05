package com.finndog.justenoughstructures.forge;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.server.PackToolsAccess;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.server.permission.PermissionAPI;
import net.minecraftforge.server.permission.events.PermissionGatherEvent;
import net.minecraftforge.server.permission.nodes.PermissionNode;
import net.minecraftforge.server.permission.nodes.PermissionTypes;

/**
 * Asks Forge's permission system about Pack tools, which permissions mods like LuckPerms answer.
 * Without one it says no, and JES's own rules decide, as on Fabric.
 */
final class ForgePermissions {
    /** justenoughstructures.pack_tools, the same node as on Fabric. */
    private static final PermissionNode<Boolean> PACK_TOOLS = new PermissionNode<>(JustEnoughStructures.MOD_ID, "pack_tools",
            PermissionTypes.BOOLEAN, (player, uuid, context) -> false);

    private ForgePermissions() {
    }

    static void install() {
        //? if >=26.1 {
        /*PermissionGatherEvent.Nodes.BUS.addListener(event -> event.addNodes(PACK_TOOLS));
        *///?} else {
        MinecraftForge.EVENT_BUS.addListener((PermissionGatherEvent.Nodes event) -> event.addNodes(PACK_TOOLS));
        //?}
        PackToolsAccess.setPermissions((player, node) -> PermissionAPI.getPermission(player, PACK_TOOLS));
    }
}
