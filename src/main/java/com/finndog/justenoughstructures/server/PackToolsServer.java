package com.finndog.justenoughstructures.server;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.Regs;
import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.catalog.StructureInfo;
import com.finndog.justenoughstructures.overrides.ContainerPatches;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.finndog.justenoughstructures.overrides.SpawnerPatches;
import com.finndog.justenoughstructures.overrides.StructureInfoFiles;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.loot.LootDataType;
//? if >=26.1 {
/*import com.finndog.justenoughstructures.Ids;
import net.minecraft.resources.ResourceKey;
*///?}

/**
 * Pack tools on the server: what it shows, and the changes it makes besides editing loot tables,
 * containers and spawners, which have their own requests. Only for players {@link PackToolsAccess} lets in.
 */
public final class PackToolsServer {
    /** Changes saved since the last /reload, which only apply from the next one. */
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();

    private PackToolsServer() {
    }

    /** Notes a change that applies from the next /reload. */
    public static void waiting(String key) {
        PENDING.add(key);
    }

    /** After a /reload, or when the server starts, everything saved is in use. */
    public static void reloaded() {
        PENDING.clear();
    }

    public static PackToolsState state(MinecraftServer server) {
        Map<ResourceLocation, PackToolsState.Written> structures = new HashMap<>();
        StructureInfo.all().forEach((id, info) -> structures.put(id, new PackToolsState.Written(info, StructureInfoFiles.written(id))));
        List<StructureCatalog.Entry> hidden = new ArrayList<>();
        for (StructureCatalog.Entry entry : StructureCatalog.build(server)) {
            if (ServerConfig.hides(entry.id())) {
                hidden.add(entry);
            }
        }
        //? if >=26.1 {
        /*List<ResourceLocation> tables = reloadable(server, Registries.LOOT_TABLE);
        *///?} else if >=1.21 {
        /*List<ResourceLocation> tables = new ArrayList<>(new TreeSet<>(server.reloadableRegistries().getKeys(Registries.LOOT_TABLE)));
        *///?} else {
        List<ResourceLocation> tables = new ArrayList<>(new TreeSet<>(server.getLootData().getKeys(LootDataType.TABLE)));
        //?}
        Map<String, List<ResourceLocation>> names = new HashMap<>();
        Registry<Structure> registry = server.registryAccess().registryOrThrow(Registries.STRUCTURE);
        names.put(PackToolsState.STRUCTURES, new ArrayList<>(new TreeSet<>(registry.keySet())));
        names.put(PackToolsState.STRUCTURE_TAGS, new ArrayList<>(new TreeSet<>(Regs.tagIds(registry).map(TagKey::location).toList())));
        //? if >=26.1 {
        /*names.put(PackToolsState.PREDICATES, reloadable(server, Registries.PREDICATE));
        names.put(PackToolsState.ITEM_MODIFIERS, reloadable(server, Registries.ITEM_MODIFIER));
        *///?} else if >=1.21 {
        /*names.put(PackToolsState.PREDICATES, new ArrayList<>(new TreeSet<>(server.reloadableRegistries().getKeys(Registries.PREDICATE))));
        names.put(PackToolsState.ITEM_MODIFIERS, new ArrayList<>(new TreeSet<>(server.reloadableRegistries().getKeys(Registries.ITEM_MODIFIER))));
        *///?} else {
        names.put(PackToolsState.PREDICATES, new ArrayList<>(new TreeSet<>(server.getLootData().getKeys(LootDataType.PREDICATE))));
        names.put(PackToolsState.ITEM_MODIFIERS, new ArrayList<>(new TreeSet<>(server.getLootData().getKeys(LootDataType.MODIFIER))));
        //?}
        return new PackToolsState(ServerConfig.get(), Set.copyOf(PENDING), LootOverrides.statuses(server.getResourceManager()),
                ContainerPatches.all(), SpawnerPatches.all(), structures, hidden, tables, names);
    }

    //? if >=26.1 {
    /*// Every id of this kind the server has loaded from datapacks, sorted.
    private static <T> List<ResourceLocation> reloadable(MinecraftServer server, ResourceKey<? extends Registry<? extends T>> type) {
        return new ArrayList<>(new TreeSet<>(server.reloadableRegistries().lookup().lookupOrThrow(type).listElementIds().map(Ids::of).toList()));
    }
    *///?}

    /**
     * Saves the server's rules to server.json5 and puts them in use. Using changed containers and
     * spawners only applies from the next /reload, as templates are only read then.
     */
    public static Component saveRules(MinecraftServer server, ServerConfig.Settings asked) {
        ServerConfig.Settings before = ServerConfig.get();
        ServerConfig.Settings settings = checked(asked);
        try {
            ServerConfig.save(ServerConfig.file(), settings);
        } catch (IOException e) {
            JustEnoughStructures.LOGGER.warn("Couldn't save {}: {}", ServerConfig.file(), e.toString());
            return Component.translatable("screen.justenoughstructures.override.save_failed", String.valueOf(e.getMessage()));
        }
        ServerConfig.load();
        if (before.containerChanges() != settings.containerChanges()) {
            if (PENDING.contains(PackToolsState.RULES)) {
                // Switched back before a /reload: nothing's waiting any more.
                PENDING.remove(PackToolsState.RULES);
            } else {
                PENDING.add(PackToolsState.RULES);
            }
        }
        JesServer.structuresChanged(server);
        return Component.translatable("screen.justenoughstructures.tools.rules_saved");
    }

    /** Only what server.json5 allows: levels from 0 to 4, or -1 to 4 for Pack tools, and real player names. */
    static ServerConfig.Settings checked(ServerConfig.Settings s) {
        Set<String> names = new LinkedHashSet<>();
        for (String name : s.packTools().players()) {
            String trimmed = name.trim();
            if (ServerConfig.isPlayerName(trimmed) && names.stream().noneMatch(trimmed::equalsIgnoreCase)) {
                names.add(trimmed);
            }
        }
        Set<String> mods = new TreeSet<>();
        for (String mod : s.hiddenMods()) {
            if (ResourceLocation.tryParse(mod + ":any") != null) {
                mods.add(mod);
            }
        }
        return new ServerConfig.Settings(Set.copyOf(s.hiddenStructures()), Set.copyOf(mods), clamp(s.locatePermission(), 0), clamp(s.teleportPermission(), 0),
                s.showLootLocations(), new ServerConfig.PackTools(List.copyOf(names), clamp(s.packTools().permissionLevel(), -1)), s.containerChanges());
    }

    private static int clamp(int level, int lowest) {
        return Math.max(lowest, Math.min(4, level));
    }

    /** Saves what players are told about a structure: its notes, and whether where its loot is stays a secret. */
    public static Component saveStructure(MinecraftServer server, ResourceLocation id, String notes, boolean secret) {
        if (!server.registryAccess().registryOrThrow(Registries.STRUCTURE).containsKey(id)) {
            return Component.translatable("screen.justenoughstructures.tools.no_structure", id.toString());
        }
        Component reply = StructureInfoFiles.save(server.getResourceManager(), id, notes, secret);
        JesServer.structuresChanged(server);
        return reply;
    }

    /** Runs /reload for a Pack tools user, who may not be allowed the command itself. */
    public static Component reload(MinecraftServer server) {
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload");
        return Component.translatable("screen.justenoughstructures.tools.reloading");
    }
}
