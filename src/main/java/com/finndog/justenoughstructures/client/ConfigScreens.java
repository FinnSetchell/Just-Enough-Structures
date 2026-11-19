package com.finndog.justenoughstructures.client;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.catalog.Availability;
import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.google.gson.JsonElement;
import java.util.LinkedHashSet;
import java.util.Set;
import net.minecraft.client.gui.screens.Screen;

/**
 * Other mods' settings screens, which the browser offers for the structure on show. The loader sets
 * one when it can open them: Mod Menu's list on Fabric, the Mods list's on Forge and NeoForge.
 */
public interface ConfigScreens {
    /** Whether the mod has a settings screen. */
    boolean has(String modId);

    /** Builds the mod's settings screen, returning to {@code parent}. Null if it has none after all. May throw. */
    Screen open(String modId, Screen parent);

    final class Holder {
        private static ConfigScreens screens;

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
     * The mod whose settings govern a structure, or null when none has a screen to open. In order:
     * Moog's Structure Lib for structures it places, the mod that turned the structure off or
     * replaced it, Moog's Structure Lib again for a replacement (its screen picks those), and last
     * the structure's own mod.
     */
    static String modFor(StructureCatalog.Entry entry) {
        ConfigScreens screens = get();
        if (screens == null) {
            return null;
        }
        Set<String> candidates = new LinkedHashSet<>();
        for (StructureCatalog.SetInfo set : entry.sets()) {
            JsonElement type = set.placement() == null ? null : set.placement().get("type");
            if (type != null && type.isJsonPrimitive() && type.getAsString().startsWith("moogs_structures:")) {
                candidates.add("moogs_structures");
            }
        }
        Availability availability = entry.availability();
        if (availability != null && availability.by() != null) {
            switch (availability.reason()) {
                case TURNED_OFF, TAGGED_OFF -> candidates.add(availability.by());
                case REPLACED -> {
                    candidates.add(availability.by());
                    candidates.add("moogs_structures");
                }
                default -> {
                }
            }
        }
        candidates.add(entry.id().getNamespace());
        // The game's own options govern no structure. Mod Menu lists them under minecraft.
        candidates.remove("minecraft");
        for (String mod : candidates) {
            try {
                if (screens.has(mod)) {
                    return mod;
                }
            } catch (Throwable t) {
                JesLog.warnOnce("config_screens_has", "Couldn't ask whether {} has a settings screen", mod, t);
            }
        }
        return null;
    }
}
