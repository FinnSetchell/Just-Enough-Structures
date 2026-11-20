package com.finndog.justenoughstructures.client;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.google.gson.JsonElement;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.ResourceLocation;

/**
 * Other mods' settings screens, which the browser offers for the structure on show. The loader sets
 * one when it can open them: Mod Menu's list on Fabric, the Mods list's on Forge and NeoForge.
 */
public interface ConfigScreens {
    /** Whether the mod has a settings screen. */
    boolean has(String modId);

    /** Builds the mod's settings screen, returning to {@code parent}. Null if it has none after all. May throw. */
    Screen open(String modId, Screen parent);

    /** The mod whose jar holds a file, given by its path inside the jar like {@code data/<namespace>/...}, or null for none. May throw. */
    String modWithFile(String path);

    final class Holder {
        private static ConfigScreens screens;
        /** The mod whose jar holds each structure's file, "" for none, worked out once per structure. */
        private static final Map<ResourceLocation, String> owners = new HashMap<>();

        private Holder() {
        }
    }

    static ConfigScreens get() {
        return Holder.screens;
    }

    static void set(ConfigScreens screens) {
        Holder.screens = screens;
    }

    /**
     * Whether a mod's settings screen can be offered: the loader can open screens and the mod has one.
     * Never the game's own options, which Mod Menu lists under minecraft, as they govern no structure.
     */
    static boolean canOpen(String modId) {
        ConfigScreens screens = get();
        if (screens == null || modId == null || "minecraft".equals(modId)) {
            return false;
        }
        try {
            return screens.has(modId);
        } catch (Throwable t) {
            JesLog.warnOnce("config_screens_has", "Couldn't ask whether {} has a settings screen", modId, t);
            return false;
        }
    }

    /**
     * The mod whose settings the browser's mod row opens for a structure, or null when none has a
     * screen to open. In order: Moog's Structure Lib for structures it places, as its screen has a row
     * for their sets; the mod of the structure's namespace; and the mod whose jar holds the structure's
     * file, for mods whose id isn't their structures' namespace, like Towns and Towers. A mod that
     * turned the structure off or replaced it is left to the Info tab, which names it.
     */
    static String modFor(StructureCatalog.Entry entry) {
        ConfigScreens screens = get();
        if (screens == null) {
            return null;
        }
        for (StructureCatalog.SetInfo set : entry.sets()) {
            JsonElement type = set.placement() == null ? null : set.placement().get("type");
            if (type != null && type.isJsonPrimitive() && type.getAsString().startsWith("moogs_structures:")) {
                if (canOpen("moogs_structures")) {
                    return "moogs_structures";
                }
                break;
            }
        }
        String namespace = entry.id().getNamespace();
        if ("minecraft".equals(namespace)) {
            return null;
        }
        if (canOpen(namespace)) {
            return namespace;
        }
        String owner = owner(screens, entry.id());
        return owner != null && !owner.equals(namespace) && canOpen(owner) ? owner : null;
    }

    /** The mod whose jar holds a structure's file, or null. Never throws. */
    private static String owner(ConfigScreens screens, ResourceLocation id) {
        String found = Holder.owners.computeIfAbsent(id, key -> {
            try {
                String mod = screens.modWithFile("data/" + key.getNamespace() + "/worldgen/structure/" + key.getPath() + ".json");
                return mod == null ? "" : mod;
            } catch (Throwable t) {
                JesLog.warnOnce("config_screens_owner", "Couldn't find which mod holds {}", key, t);
                return "";
            }
        });
        return found.isEmpty() ? null : found;
    }
}
