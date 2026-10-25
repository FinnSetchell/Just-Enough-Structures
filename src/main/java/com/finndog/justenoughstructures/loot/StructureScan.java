package com.finndog.justenoughstructures.loot;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.resources.ResourceLocation;

/**
 * The slow half of the loot index, from generating every structure: the loot tables each one uses
 * and the templates it can place. Which items a table can give is read from the tables themselves
 * each time instead, which is quick, so editing a loot table never means generating structures again.
 *
 * <p>{@code patches} records, per template, the container patches and spawner kind changes in effect
 * when the scan was made (a trial spawner changes what drops). When they change, only the structures
 * that place those templates are generated again.
 *
 * <p>{@code retry} lists structures that failed for a temporary reason, like the server running short
 * on memory. They're generated again the next time the scan is brought up to date.
 *
 * <p>{@code placed} holds the items each structure places itself, without a loot table.
 *
 * <p>{@code rolled} holds, per loot table, the items rolling it gave that reading it doesn't show,
 * like those a loot modifier adds. Rolling takes the server's own time, so it's kept here rather than
 * done after every start, under a hash of the table so it's done again once the table changes.
 */
public record StructureScan(Map<ResourceLocation, Set<ResourceLocation>> tables,
                            Map<ResourceLocation, Set<ResourceLocation>> templates,
                            Map<ResourceLocation, String> patches,
                            Set<ResourceLocation> retry,
                            Map<ResourceLocation, Set<ResourceLocation>> placed,
                            Map<ResourceLocation, Rolled> rolled) {

    /** What rolling a loot table gave that reading it doesn't show, and the hash of the table it was rolled as. */
    public record Rolled(String hash, Set<ResourceLocation> items) {
    }

    public StructureScan(Map<ResourceLocation, Set<ResourceLocation>> tables, Map<ResourceLocation, Set<ResourceLocation>> templates,
                         Map<ResourceLocation, String> patches) {
        this(tables, templates, patches, Set.of());
    }

    public StructureScan(Map<ResourceLocation, Set<ResourceLocation>> tables, Map<ResourceLocation, Set<ResourceLocation>> templates,
                         Map<ResourceLocation, String> patches, Set<ResourceLocation> retry) {
        this(tables, templates, patches, retry, Map.of());
    }

    public StructureScan(Map<ResourceLocation, Set<ResourceLocation>> tables, Map<ResourceLocation, Set<ResourceLocation>> templates,
                         Map<ResourceLocation, String> patches, Set<ResourceLocation> retry, Map<ResourceLocation, Set<ResourceLocation>> placed) {
        this(tables, templates, patches, retry, placed, Map.of());
    }

    /** Every loot table the structures use. */
    public Set<ResourceLocation> usedTables() {
        Set<ResourceLocation> out = new TreeSet<>();
        tables.values().forEach(out::addAll);
        return out;
    }

    /** The scan with these rolls kept too, and without the rolls of tables no structure uses any more. */
    public StructureScan withRolled(Map<ResourceLocation, Rolled> more) {
        Set<ResourceLocation> used = usedTables();
        Map<ResourceLocation, Rolled> kept = new TreeMap<>();
        rolled.forEach((table, r) -> {
            if (used.contains(table)) {
                kept.put(table, r);
            }
        });
        more.forEach((table, r) -> {
            if (used.contains(table)) {
                kept.put(table, r);
            }
        });
        return new StructureScan(tables, templates, patches, retry, placed, kept);
    }

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
