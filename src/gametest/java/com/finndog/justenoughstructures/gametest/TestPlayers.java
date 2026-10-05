package com.finndog.justenoughstructures.gametest;

import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
//? if forge {
/*import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
*///?}

/** Players for tests to act as. */
final class TestPlayers {
    private TestPlayers() {
    }

    /** A creative player in the test's level, like {@link GameTestHelper#makeMockServerPlayerInLevel()} makes. */
    static ServerPlayer mock(GameTestHelper helper) {
        //? if forge {
        /*// Forge's joining code reaches for the connection's netty channel, which the game's own mock
        // player doesn't have, so this one gets a stand-in.
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), "test-mock-player")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player);
        return player;
        *///?} else {
        return helper.makeMockServerPlayerInLevel();
        //?}
    }
}
