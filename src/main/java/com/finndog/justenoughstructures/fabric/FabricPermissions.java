package com.finndog.justenoughstructures.fabric;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.server.PackToolsAccess;
import java.lang.reflect.Method;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.world.entity.Entity;

/**
 * Asks a permissions mod (LuckPerms, FTB Ranks and others) about Pack tools through the Fabric
 * Permissions API, which those mods ship. Looked up by name, so JES needs nothing extra when no
 * permissions mod is installed.
 */
final class FabricPermissions {
    private FabricPermissions() {
    }

    static void install() {
        if (!FabricLoader.getInstance().isModLoaded("fabric-permissions-api-v0")) {
            return;
        }
        try {
            Class<?> permissions = Class.forName("me.lucko.fabric.api.permissions.v0.Permissions");
            Method check = permissions.getMethod("check", Entity.class, String.class, boolean.class);
            PackToolsAccess.setPermissions((player, node) -> {
                try {
                    return (boolean) check.invoke(null, player, node, false);
                } catch (ReflectiveOperationException e) {
                    throw new IllegalStateException(e);
                }
            });
        } catch (ReflectiveOperationException | LinkageError e) {
            JesLog.warnOnce("permissions-api", "Couldn't use the Fabric Permissions API for {}: {}", PackToolsAccess.NODE, e.toString());
        }
    }
}
