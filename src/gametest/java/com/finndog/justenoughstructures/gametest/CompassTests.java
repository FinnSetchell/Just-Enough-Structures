package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.server.JesServer;
import com.finndog.justenoughstructures.server.ServerConfig;
import java.util.List;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
//? if >=1.21 {
/*import com.finndog.justenoughstructures.Ids;
import net.minecraft.core.component.DataComponentType;
*///?}

/** Setting a held Explorer's Compass searching from the browser. Skipped without Explorer's Compass. */
public final class CompassTests {
    private static final ResourceLocation COMPASS = Ids.of("explorerscompass", "explorerscompass");
    private static final ResourceLocation PYRAMID = Ids.parse("desert_pyramid");

    private CompassTests() {
    }

    /** It's refused wherever the compass itself would refuse, and otherwise the compass's own search starts. */
    public static void compassOnlySearchesWhereItCould(GameTestHelper helper) {
        if (!BuiltInRegistries.ITEM.containsKey(COMPASS)) {
            helper.succeed();
            return;
        }
        ServerPlayer player = TestPlayers.mock(helper);
        helper.assertTrue(key(JesServer.compassFor(player, PYRAMID)).endsWith("compass_not_held"), "a player without a compass wasn't told to hold one");

        ItemStack compass = new ItemStack(BuiltInRegistries.ITEM.get(COMPASS));
        player.setItemInHand(InteractionHand.MAIN_HAND, compass);
        player.giveExperienceLevels(30);

        // An end city can't generate here, and trying would cost levels and wipe the compass's target.
        Component reply = JesServer.compassFor(player, Ids.parse("end_city"));
        helper.assertTrue(key(reply).endsWith("locate_wrong_dimension"), "searching for an end city in the overworld got " + reply.getString());
        helper.assertTrue(target(compass) == null, "a refused search still changed the compass");

        ServerConfig.Settings before = ServerConfig.get();
        try {
            ServerConfig.set(new ServerConfig.Settings(Set.of(PYRAMID), Set.of(), 2, 2, true, ServerConfig.PackTools.level(4)));
            helper.assertTrue(key(JesServer.compassFor(player, PYRAMID)).endsWith("locate_hidden"), "the compass was set searching for a hidden structure");
        } finally {
            ServerConfig.set(before);
        }

        // The test world is superflat, where only villages and strongholds can generate.
        ResourceLocation village = Ids.parse("village_plains");
        reply = JesServer.compassFor(player, village);
        helper.assertTrue(key(reply).endsWith("compass_now_searching"), "the compass didn't start searching, got " + reply.getString());
        helper.assertTrue(village.toString().equals(target(compass)), "the compass is set to " + target(compass) + " instead of the plains village");

        // Stops the search the way a player would, by using the compass while sneaking, so it doesn't
        // keep generating chunks for the rest of the tests.
        player.setShiftKeyDown(true);
        compass.getItem().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.succeed();
    }

    private static String target(ItemStack compass) {
        //? if >=1.21 {
        /*// Kept in a data component of its own since 1.20.5.
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(Ids.of("explorerscompass", "structure_id"));
        Object id = type == null ? null : compass.get(type);
        return id == null ? null : id.toString();
        *///?} else {
        // Its Fabric build keeps what it's set to as StructureID, its Forge build as StructureKey.
        CompoundTag tag = compass.getTag();
        if (tag != null) {
            for (String key : List.of("StructureID", "StructureKey")) {
                if (tag.contains(key)) {
                    return tag.getString(key);
                }
            }
        }
        return null;
        //?}
    }

    private static String key(Component reply) {
        return reply.getContents() instanceof TranslatableContents t ? t.getKey() : reply.getString();
    }
}
