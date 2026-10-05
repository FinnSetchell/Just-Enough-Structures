package com.finndog.justenoughstructures.server;

import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.catalog.StructureInfo;
import com.finndog.justenoughstructures.overrides.ContainerPatches;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.finndog.justenoughstructures.overrides.SpawnerPatches;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/**
 * Everything Pack tools shows about a server: its rules, each loot table edited, each container
 * and spawner changed, what's written about each structure, and what's waiting for a /reload.
 *
 * @param settings   the server's rules, as server.json5 has them
 * @param pending    changes saved since the last /reload, as {@link #tableKey}, {@link #chestKey}, {@link #spawnerKey} and {@link #RULES}
 * @param overrides  each loot table with an override, and how it stands
 * @param patches    each container pointed at another loot table, used or not
 * @param spawners   each spawner given another mob, used or not
 * @param structures what's written about each structure that has anything, and whether Pack tools wrote it
 * @param hidden     the structures players can't see, which the browser's list leaves out
 * @param tables     every loot table the server has
 * @param names      other ids only the server has, for the loot table editor to list: {@link #STRUCTURES},
 *                   {@link #STRUCTURE_TAGS}, {@link #PREDICATES} and {@link #ITEM_MODIFIERS}
 */
public record PackToolsState(ServerConfig.Settings settings, Set<String> pending, Map<ResourceLocation, LootOverrides.Status> overrides,
                             List<ContainerPatches.Patch> patches, List<SpawnerPatches.Patch> spawners, Map<ResourceLocation, Written> structures,
                             List<StructureCatalog.Entry> hidden, List<ResourceLocation> tables, Map<String, List<ResourceLocation>> names) {
    public static final String STRUCTURES = "structures";
    public static final String STRUCTURE_TAGS = "structure_tags";
    public static final String PREDICATES = "predicates";
    public static final String ITEM_MODIFIERS = "item_modifiers";

    /** The server.json5 setting that only applies from the next /reload: using changed containers and spawners. */
    public static final String RULES = "rules";

    /** What's written about a structure, and whether it's in the JES pack rather than from its mod. */
    public record Written(StructureInfo info, boolean fromPack) {
    }

    public static String tableKey(ResourceLocation table) {
        return "table:" + table;
    }

    public static String chestKey(ResourceLocation template, BlockPos pos) {
        return "chest:" + template + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    public static String spawnerKey(ResourceLocation template, BlockPos pos) {
        return "spawner:" + template + "@" + pos.getX() + "," + pos.getY() + "," + pos.getZ();
    }

    /** How many changes are waiting for a /reload. */
    public int waiting() {
        return pending.size();
    }

    public StructureInfo info(ResourceLocation structure) {
        Written written = structures.get(structure);
        return written == null ? StructureInfo.NONE : written.info();
    }
}
