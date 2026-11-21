package elocindev.item_obliterator.fabric_quilt.util;

import elocindev.item_obliterator.fabric_quilt.ItemObliterator;

/**
 * A stand-in for Item Obliterator's check, for the game tests, which don't have the mod. The real one
 * is in the same place in its Fabric and Quilt jars, 2.3.0 for 1.20.1 and 3.1.1 for 1.21.1 (read with
 * javap, 21 November 2026); its Forge and NeoForge jars have it as forge.utils.Utils and
 * neoforge.utils.Utils. Like the real one without its hash sets: an exact id, "!" before a regex, and
 * "//" before a comment.
 */
public class Utils {
    public static boolean isDisabled(String id) {
        if (id.equals("minecraft:air")) {
            return false;
        }
        for (String entry : ItemObliterator.Config.blacklisted_items) {
            if (entry == null || entry.startsWith("//")) {
                continue;
            }
            if (entry.equals(id) || entry.startsWith("!") && id.matches(entry.substring(1))) {
                return true;
            }
        }
        return false;
    }
}
