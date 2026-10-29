package com.finndog.justenoughstructures.catalog;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/** What a #tag search finds, given each structure tag with the listed structures in it. */
public final class TagSearch {
    private TagSearch() {
    }

    /**
     * The structures in the tags {@code text} names: the one tag when it's a tag's whole id, else every
     * tag with {@code text} in its id, so typing "#vill" finds villages before the id is finished.
     */
    public static Set<ResourceLocation> structuresIn(Map<ResourceLocation, List<ResourceLocation>> tags, String text) {
        Set<ResourceLocation> found = new HashSet<>();
        for (Map.Entry<ResourceLocation, List<ResourceLocation>> tag : tags.entrySet()) {
            if (tag.getKey().toString().equals(text)) {
                return new HashSet<>(tag.getValue());
            }
            if (tag.getKey().toString().contains(text)) {
                found.addAll(tag.getValue());
            }
        }
        return found;
    }
}
