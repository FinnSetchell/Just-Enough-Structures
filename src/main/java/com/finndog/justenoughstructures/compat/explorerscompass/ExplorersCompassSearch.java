package com.finndog.justenoughstructures.compat.explorerscompass;

// Its Forge and NeoForge builds' names differ, see stonecutter.gradle.kts.
//~ compass_names

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.items.ExplorersCompassItem;
import com.chaosthedude.explorerscompass.util.ItemUtils;
import com.chaosthedude.explorerscompass.util.StructureUtils;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.Players;
import com.finndog.justenoughstructures.Regs;
import com.finndog.justenoughstructures.server.CompassSearch;
import com.finndog.justenoughstructures.server.JesServer;
import java.lang.reflect.Method;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.structure.Structure;
// Stonecutter reads && and || left to right, so these stay apart.
//? if neoforge && <26.1 {
/*import java.util.List;
*///?}
//? if forge && >=1.21 {
/*import java.util.List;
*///?}

/**
 * Starts the held Explorer's Compass searching, through its own search, as if the structure had
 * been picked in its screen. Its screen won't offer what the compass can't do, but its search
 * doesn't check again, so this does first.
 */
public final class ExplorersCompassSearch implements CompassSearch {
    private ExplorersCompassSearch() {
    }

    /**
     * Only called with Explorer's Compass installed, so its classes are never touched otherwise. A
     * build without everything this calls gets no link, as a call it can't answer would stop the server.
     */
    public static void install() {
        if (!supported()) {
            JustEnoughStructures.LOGGER.warn("This Explorer's Compass is older than the one the browser works with, so it won't set the compass searching. Updating it brings that back");
            return;
        }
        JesServer.setCompassSearch(new ExplorersCompassSearch());
    }

    /**
     * Whether the installed compass has what this calls. Its builds from before April 2026 have no
     * durability or XP cost to check.
     */
    private static boolean supported() {
        //? if !forge || <1.21 {
        return has(ExplorersCompassItem.class, "isBroken") && has(StructureUtils.class, "getXpLevelsForStructure");
        //?} else {
        /*return true;
        *///?}
    }

    private static boolean has(Class<?> type, String method) {
        try {
            for (Method m : type.getMethods()) {
                if (m.getName().equals(method)) {
                    return true;
                }
            }
        } catch (LinkageError | RuntimeException e) {
            // A class that can't be looked through is as good as missing.
        }
        return false;
    }

    @Override
    public Component search(ServerPlayer player, ResourceLocation structure) {
        ExplorersCompassItem compass = ExplorersCompass.EXPLORERS_COMPASS_ITEM;
        ItemStack stack = ItemUtils.getHeldItem(player, compass);
        if (stack.isEmpty()) {
            return Component.translatable("screen.justenoughstructures.compass_not_held");
        }
        // Its Forge build for 1.21 never breaks, and its searches cost nothing, so it has no checks for either.
        //? if !forge || <1.21 {
        if (compass.isBroken(stack)) {
            return Component.translatable("screen.justenoughstructures.compass_broken");
        }
        //?}
        ServerLevel level = Players.level(player);
        // What it's set up to refuse, like its blacklist, only ever keeps things out of its screen.
        if (!StructureUtils.getAllowedStructureIDs(level).contains(structure)) {
            return Component.translatable("screen.justenoughstructures.compass_not_allowed");
        }
        // It would still charge for a search that can't start here, and forget what it pointed at.
        Optional<Holder.Reference<Structure>> holder = Regs.holder(level.registryAccess().registryOrThrow(Registries.STRUCTURE),
                ResourceKey.create(Registries.STRUCTURE, structure));
        if (holder.isEmpty() || level.getChunkSource().getGeneratorState().getPlacementsForStructure(holder.get()).isEmpty()) {
            return Component.translatable("screen.justenoughstructures.locate_wrong_dimension");
        }
        //? if !forge || <1.21 {
        int levels = StructureUtils.getXpLevelsForStructure(level, structure);
        if (!player.getAbilities().instabuild && player.experienceLevel < levels) {
            return Component.translatable("screen.justenoughstructures.compass_needs_levels", levels);
        }
        //?}
        //? if neoforge && <26.1 {
        /*// Its NeoForge build searches for a group of structures, here a group of one, as its screen does.
        compass.searchForStructure(level, player, structure, List.of(structure), player.blockPosition(), stack, false);
        *///?} else if forge && >=1.21 {
        /*// Its Forge build for 1.21 does too, but without saying whether it's a group.
        compass.searchForStructure(level, player, structure, List.of(structure), player.blockPosition(), stack);
        *///?} else {
        compass.searchForStructure(level, player, player.blockPosition(), structure, false, stack);
        //?}
        return Component.translatable("screen.justenoughstructures.compass_now_searching");
    }
}
