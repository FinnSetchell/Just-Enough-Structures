package com.finndog.justenoughstructures.loot;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import net.minecraft.resources.ResourceLocation;

/**
 * The slow half of the loot index, from generating every structure: the loot tables each one uses
 * and the templates it can place. Which items a table can give is read from the tables themselves
 * each time instead, which is quick, so editing a loot table never means generating structures again.
 *
 * <p>{@code patches} is how the containers changed in the browser stood when it was made, per
 * template, along with spawners made trial spawners or back, which change what drops. When they
 * change, only the structures that place one of those templates are generated again.
 */
public record StructureScan(Map<ResourceLocation, Set<ResourceLocation>> tables,
                            Map<ResourceLocation, Set<ResourceLocation>> templates,
                            Map<ResourceLocation, String> patches) {

    /** The templates whose changes differ between the scan and {@code now}. */
    public Set<ResourceLocation> changedTemplates(Map<ResourceLocation, String> now) {
        Set<ResourceLocation> changed = new TreeSet<>();
        for (ResourceLocation template : patches.keySet()) {
            if (!Objects.equals(patches.get(template), now.get(template))) {
                changed.add(template);
            }
        }
        for (ResourceLocation template : now.keySet()) {
            if (!Objects.equals(patches.get(template), now.get(template))) {
                changed.add(template);
            }
        }
        return changed;
    }

    /** The structures that can place any of these templates. */
    public Set<ResourceLocation> placing(Set<ResourceLocation> changed) {
        Set<ResourceLocation> out = new TreeSet<>();
        templates.forEach((structure, placed) -> {
            for (ResourceLocation template : placed) {
                if (changed.contains(template)) {
                    out.add(structure);
                    return;
                }
            }
        });
        return out;
    }
}
