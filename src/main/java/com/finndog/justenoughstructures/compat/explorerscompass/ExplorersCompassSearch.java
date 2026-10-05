package com.finndog.justenoughstructures.compat.explorerscompass;

// Its Forge build's names differ, see stonecutter.gradle.kts.
//~ compass_names

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.items.ExplorersCompassItem;
import com.chaosthedude.explorerscompass.util.ItemUtils;
import com.chaosthedude.explorerscompass.util.StructureUtils;
import com.finndog.justenoughstructures.server.CompassSearch;
import com.finndog.justenoughstructures.server.JesServer;
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

/**
 * Starts the held Explorer's Compass searching, through its own search, as if the structure had
 * been picked in its screen. Its screen won't offer what the compass can't do, but its search
 * doesn't check again, so this does first.
 */
public final class ExplorersCompassSearch implements CompassSearch {
    private ExplorersCompassSearch() {
    }

    /** Only called with Explorer's Compass installed, so its classes are never touched otherwise. */
    public static void install() {
        JesServer.setCompassSearch(new ExplorersCompassSearch());
    }

    @Override
    public Component search(ServerPlayer player, ResourceLocation structure) {
        ExplorersCompassItem compass = ExplorersCompass.EXPLORERS_COMPASS_ITEM;
        ItemStack stack = ItemUtils.getHeldItem(player, compass);
        if (stack.isEmpty()) {
            return Component.translatable("screen.justenoughstructures.compass_not_held");
        }
        if (compass.isBroken(stack)) {
            return Component.translatable("screen.justenoughstructures.compass_broken");
        }
        ServerLevel level = player.serverLevel();
        // What it's set up to refuse, like its blacklist, only ever keeps things out of its screen.
        if (!StructureUtils.getAllowedStructureIDs(level).contains(structure)) {
            return Component.translatable("screen.justenoughstructures.compass_not_allowed");
        }
        // It would still charge for a search that can't start here, and forget what it pointed at.
        Optional<Holder.Reference<Structure>> holder = level.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .getHolder(ResourceKey.create(Registries.STRUCTURE, structure));
        if (holder.isEmpty() || level.getChunkSource().getGeneratorState().getPlacementsForStructure(holder.get()).isEmpty()) {
            return Component.translatable("screen.justenoughstructures.locate_wrong_dimension");
        }
        int levels = StructureUtils.getXpLevelsForStructure(level, structure);
        if (!player.getAbilities().instabuild && player.experienceLevel < levels) {
            return Component.translatable("screen.justenoughstructures.compass_needs_levels", levels);
        }
        compass.searchForStructure(level, player, player.blockPosition(), structure, false, stack);
        return Component.translatable("screen.justenoughstructures.compass_now_searching");
    }
}
