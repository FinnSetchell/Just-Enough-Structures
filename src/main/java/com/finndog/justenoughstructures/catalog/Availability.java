package com.finndog.justenoughstructures.catalog;

import net.minecraft.resources.ResourceLocation;

/**
 * Whether a structure turns up in new worlds on its own and, if not, why, so nobody goes looking
 * for one in vain. {@code by} is the mod responsible, and {@code replacedBy} the structure that
 * takes its place, when there's one.
 */
public record Availability(Reason reason, String by, ResourceLocation replacedBy) {
    public static final Availability GENERATES = new Availability(Reason.GENERATES, null, null);

    public enum Reason {
        GENERATES,
        /** In no structure set, so nothing starts it, though commands and other mods still can. */
        NO_SET,
        /** Its structure sets give it no chance to spawn. */
        NEVER,
        /** In Integrated API's disabled_structures tag. */
        TAGGED_OFF,
        /** Turned off in a mod's settings. */
        TURNED_OFF,
        /** A mod puts its own in its place. */
        REPLACED
    }

    public boolean generates() {
        return reason == Reason.GENERATES;
    }
}
