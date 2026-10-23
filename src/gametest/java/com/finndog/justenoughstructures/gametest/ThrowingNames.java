package com.finndog.justenoughstructures.gametest;

import net.minecraft.world.entity.Entity;

/**
 * Makes one mob fail when it's asked for its name, while armed. Other mods' mobs do that for real: a
 * name worked out from something only a mob in a real world has set up fails in a preview, whose mobs
 * are made without being put in a world. Only the one mob a scenario picks, so the world's own mobs
 * and the player are left alone.
 */
public final class ThrowingNames {
    /** The mob that fails, or null for none. */
    public static volatile Entity armed;
    /** Whether the hook was applied. The test jar for a real game leaves it out, except on Fabric where the game isn't obfuscated. */
    public static volatile boolean hooked;
    /** How many times the preview's hover text, the mob's popup and anything else have asked, so a scenario can tell what was tried. */
    public static volatile int hover;
    public static volatile int popup;
    public static volatile int other;

    private ThrowingNames() {
    }

    public static void arm(Entity mob) {
        hover = 0;
        popup = 0;
        other = 0;
        armed = mob;
    }

    /** Called before {@code entity} works out its name. */
    public static void name(Entity entity) {
        if (entity == null || entity != armed) {
            return;
        }
        if (askedFrom("JesScreen", "hoverLines")) {
            hover++;
        } else if (askedFrom("MobPopup", "<init>")) {
            popup++;
        } else {
            other++;
        }
        throw new NullPointerException("A test mob's name wanted something only a mob in a real world has");
    }

    private static boolean askedFrom(String type, String method) {
        return StackWalker.getInstance().walk(frames -> frames.anyMatch(
                frame -> frame.getClassName().endsWith("." + type) && frame.getMethodName().equals(method)));
    }
}
