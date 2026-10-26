package com.finndog.justenoughstructures.gametest;

import static com.finndog.justenoughstructures.gametest.TestSupport.packToolsFor;
import static com.finndog.justenoughstructures.gametest.TestSupport.reload;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.loot.LootFormat;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import com.finndog.justenoughstructures.network.JesNetwork;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.finndog.justenoughstructures.server.JesServer;
import com.finndog.justenoughstructures.server.ServerConfig;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
//? if >=1.21 {
/*import net.minecraft.server.level.ClientInformation;
*///?}

/** Where loot is rolled for players. */
public final class RollLevelTests {
    private static final ResourceLocation TABLE = Ids.parse("justenoughstructures_gametest:rain_only");
    /** A diamond while it rains, and nothing otherwise. It never rains in the Nether. */
    private static final String RAIN_ONLY = "{\"type\": \"minecraft:generic\", \"pools\": [{\"rolls\": 1, \"entries\": [{\"type\": \"minecraft:item\","
            + " \"name\": \"minecraft:diamond\", \"conditions\": [{\"condition\": \"minecraft:weather_check\", \"raining\": true}]}]}]}";

    private RollLevelTests() {
    }

    /**
     * A table's odds are rolled in the overworld, as the loot index rolls it, whichever dimension the
     * player asking is in. Otherwise the first player to ask decides everyone's odds until a /reload,
     * and the odds can leave out what the index lists. The table here gives a diamond only while it
     * rains: asked from the Nether while the overworld rains, the odds, Pack tools' odds and rolls of an
     * edit, and a chest preview must all give it.
     */
    public static void lootIsRolledInTheOverworld(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel overworld = server.overworld();
        ServerLevel nether = server.getLevel(Level.NETHER);
        helper.assertTrue(nether != null, "the test server has no Nether");
        String sent = LootFormat.forGame(JsonParser.parseString(RAIN_ONLY).getAsJsonObject()).toString();
        Component problem = LootOverrides.check(TABLE, sent);
        helper.assertTrue(problem == null, "the table doesn't load: " + (problem == null ? "" : problem.getString()));

        // Saved as an edit in a folder of the test's own, so a /reload loads it as a real table.
        try {
            LootOverrides.setFolder(Files.createTempDirectory("jes-overrides"));
        } catch (IOException e) {
            throw new AssertionError("couldn't make a temporary folder", e);
        }
        Component saved = LootOverrides.save(server.getResourceManager(), TABLE, sent);
        helper.assertTrue(TestSupport.key(saved).endsWith("override.saved"), "the table wasn't saved: " + saved.getString());
        CompletableFuture<Void> loaded = reload(server);

        ServerPlayer player = standingIn(nether);
        float rainBefore = overworld.getRainLevel(1f);
        JesNetwork.ServerSender sender = JesNetwork.serverSender();
        List<byte[]> odds = new ArrayList<>();
        List<byte[]> preview = new ArrayList<>();
        AtomicReference<JesServer.DraftOdds> draftOdds = new AtomicReference<>();
        List<List<ItemStack>> draftRoll = new ArrayList<>();
        AtomicReference<CompletableFuture<Void>> unloaded = new AtomicReference<>();
        boolean[] logged = new boolean[1];
        helper.succeedWhen(() -> {
            helper.assertTrue(loaded.isDone(), "still loading the table");
            if (unloaded.get() == null) {
                // Rain fades a little every tick, so it's kept up until every answer is in.
                overworld.setRainLevel(1f);
                if (draftRoll.isEmpty()) {
                    helper.assertTrue(overworld.isRaining() && !nether.isRaining(), "the overworld isn't raining, or the Nether is");
                    JesNetwork.setServerSender((target, channel, buf) -> {
                        if (target == player && channel.equals(JesNetwork.ODDS)) {
                            odds.add(Blobs.bytes(buf));
                        } else if (target == player && channel.equals(JesNetwork.LOOT)) {
                            preview.add(Blobs.bytes(buf));
                        } else {
                            sender.send(target, channel, buf);
                        }
                    });
                    ServerConfig.Settings before = ServerConfig.get();
                    try {
                        ServerConfig.set(packToolsFor(0));
                        JesServer.onRequestOdds(player, 1, TABLE);
                        JesServer.draftOdds(player, TABLE, sent, draftOdds::set);
                        draftRoll.add(JesServer.draftRoll(player, new Codecs.DraftRoll(new Codecs.Draft(TABLE, sent), 3L, 27)));
                        JesServer.answerLoot(player, 2, TABLE, LootOverrides.parse(sent), 3L, 27);
                    } finally {
                        ServerConfig.set(before);
                    }
                }
                helper.assertTrue(!odds.isEmpty() && draftOdds.get() != null && !preview.isEmpty(), "the odds haven't all come yet");
                // Everything is put back before the answers are judged, so a failure leaves nothing behind.
                overworld.setRainLevel(rainBefore);
                JesNetwork.setServerSender(sender);
                JesServer.left(player);
                LootOverrides.remove(TABLE);
                unloaded.set(reload(server));
            }
            helper.assertTrue(unloaded.get().isDone(), "still unloading the table");
            LootOverrides.setFolder(null);

            FriendlyByteBuf oddsAnswer = Blobs.fromBytes(helper.getLevel().registryAccess(), MessageTests.only(helper, odds, "odds"));
            helper.assertTrue(oddsAnswer.readVarInt() == 1, "the odds weren't for the request that asked");
            FriendlyByteBuf previewAnswer = Blobs.fromBytes(helper.getLevel().registryAccess(), MessageTests.only(helper, preview, "chest preview"));
            helper.assertTrue(previewAnswer.readVarInt() == 2, "the chest preview wasn't for the request that asked");
            String seen = "the odds " + diamonds(Codecs.readOdds(oddsAnswer)) + ", Pack tools' odds " + diamonds(draftOdds.get().odds())
                    + ", a roll of the edit " + diamonds(draftRoll.get(0)) + " and a chest preview " + diamonds(Codecs.readItems(previewAnswer));
            if (!logged[0]) {
                logged[0] = true;
                JustEnoughStructures.LOGGER.info("In the test of where loot is rolled: asked from the Nether while the overworld rains, {}", seen);
            }
            helper.assertFalse(seen.contains("no diamond"), "asked from the Nether while the overworld rains, " + seen);
        });
    }

    /** What the odds say about the diamond. Anything short of every roll reads "no diamond" somewhere. */
    private static String diamonds(LootOdds odds) {
        if (odds == null) {
            return "gave no diamond, as there were no odds";
        }
        int hits = odds.rows().stream().filter(r -> r.example().is(Items.DIAMOND)).mapToInt(LootOdds.Row::hits).sum();
        return odds.rolls() > 0 && hits == odds.rolls() ? "gave a diamond in every roll"
                : "gave a diamond in " + hits + " of " + odds.rolls() + " rolls, and no diamond in the rest";
    }

    private static String diamonds(List<ItemStack> items) {
        return items.stream().anyMatch(stack -> stack.is(Items.DIAMOND)) ? "gave a diamond" : "gave no diamond";
    }

    /** A player in that level who hasn't joined, for code that only asks where they are. */
    private static ServerPlayer standingIn(ServerLevel level) {
        GameProfile profile = new GameProfile(UUID.randomUUID(), "test-nether-player");
        //? if >=1.21 {
        /*return new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault());
        *///?} else {
        return new ServerPlayer(level.getServer(), level, profile);
        //?}
    }
}
