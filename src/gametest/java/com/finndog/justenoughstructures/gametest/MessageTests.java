package com.finndog.justenoughstructures.gametest;

import static com.finndog.justenoughstructures.gametest.TestSupport.key;
import static com.finndog.justenoughstructures.gametest.TestSupport.packToolsFor;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import com.finndog.justenoughstructures.network.JesNetwork;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import com.finndog.justenoughstructures.server.CompassSearch;
import com.finndog.justenoughstructures.server.JesServer;
import com.finndog.justenoughstructures.server.ServerConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * The small messages the server sends, each a packet of its own. For each one: it's written as the
 * bytes this version of the protocol sends, set out here by hand, what's written reads back as it
 * was, and what the server really sends a player reads as what it meant.
 */
public final class MessageTests {
    private MessageTests() {
    }

    /**
     * Whether the build failed is one byte after how far it got, where older clients stop reading, so
     * they still read the progress and leave that byte. An older server sends nothing there, which
     * reads as not failed.
     */
    public static void indexProgressReadsBack(GameTestHelper helper) {
        for (boolean failed : new boolean[]{false, true}) {
            Codecs.IndexProgress progress = new Codecs.IndexProgress(3, 898, failed);
            byte[] written = bytes(helper, buf -> Codecs.writeIndexProgress(buf, progress));
            same(helper, written, bytes(helper, buf -> {
                buf.writeVarInt(3);
                buf.writeVarInt(898);
                buf.writeBoolean(failed);
            }), "the loot index's progress");
            Codecs.IndexProgress back = read(helper, written, Codecs::readIndexProgress);
            helper.assertTrue(back.equals(progress), "sent " + progress + " but read " + back);
            // Read as an older client reads it: how far, out of how many, and no further.
            FriendlyByteBuf old = Blobs.fromBytes(helper.getLevel().registryAccess(), written);
            int done = old.readVarInt();
            int total = old.readVarInt();
            helper.assertTrue(done == 3 && total == 898 && old.readableBytes() == 1,
                    "read as a version from before reads it, " + progress + " was " + done + " of " + total + " with " + old.readableBytes() + " bytes after");
        }
        byte[] fromBefore = bytes(helper, buf -> {
            buf.writeVarInt(3);
            buf.writeVarInt(898);
        });
        Codecs.IndexProgress back = read(helper, fromBefore, Codecs::readIndexProgress);
        helper.assertTrue(back.equals(new Codecs.IndexProgress(3, 898, false)), "progress from a server before builds said they failed read as " + back);

        ServerPlayer player = TestPlayers.mock(helper);
        byte[] sent = only(helper, sent(player, JesNetwork.INDEX_PROGRESS, () -> JesServer.sendIndexProgress(player, 41, 1200, true)), "index progress");
        Codecs.IndexProgress told = read(helper, sent, Codecs::readIndexProgress);
        helper.assertTrue(told.equals(new Codecs.IndexProgress(41, 1200, true)), "the server's build failed 41 of 1200 through but it said " + told);
        helper.succeed();
    }

    public static void browserSettingsReadBack(GameTestHelper helper) {
        // The two levels, then a byte for each switch, in the order the record has them.
        for (int on = 0; on < 4; on++) {
            Codecs.BrowserSettings settings = new Codecs.BrowserSettings(1, 3, on == 0, on == 1, on == 2, on == 3);
            byte[] written = bytes(helper, buf -> Codecs.writeBrowserSettings(buf, settings));
            byte[] pinned = {1, 3, 0, 0, 0, 0};
            pinned[2 + on] = 1;
            same(helper, written, pinned, "the browser's settings");
            Codecs.BrowserSettings back = read(helper, written, Codecs::readBrowserSettings);
            helper.assertTrue(back.equals(settings), "sent " + settings + " but read " + back);
        }

        ServerPlayer player = TestPlayers.mock(helper);
        ServerConfig.Settings before = ServerConfig.get();
        CompassSearch compass = JesServer.compassSearch();
        try {
            // Anyone can locate and operators teleport, there's a compass to search with, and Pack tools is for operators.
            ServerConfig.set(new ServerConfig.Settings(Set.of(), Set.of(), 0, 3, true, ServerConfig.PackTools.level(4)));
            JesServer.setCompassSearch((who, structure) -> Component.empty());
            Codecs.BrowserSettings told = read(helper, only(helper, sent(player, JesNetwork.SETTINGS, () -> JesServer.onRequestCatalog(player)), "settings"),
                    Codecs::readBrowserSettings);
            helper.assertTrue(told.equals(new Codecs.BrowserSettings(0, 3, false, true, false, false)), "a player who can't use Pack tools, with a compass, was told " + told);

            // No compass, and Pack tools for everyone.
            ServerConfig.set(new ServerConfig.Settings(Set.of(), Set.of(), 2, 4, true, ServerConfig.PackTools.level(0)));
            JesServer.setCompassSearch(null);
            told = read(helper, only(helper, sent(player, JesNetwork.SETTINGS, () -> JesServer.onRequestCatalog(player)), "settings"), Codecs::readBrowserSettings);
            helper.assertTrue(told.equals(new Codecs.BrowserSettings(2, 4, false, false, true, false)), "a player who can use Pack tools, with no compass, was told " + told);
        } finally {
            JesServer.setCompassSearch(compass);
            ServerConfig.set(before);
            JesServer.left(player);
        }
        helper.succeed();
    }

    public static void overridesReadBack(GameTestHelper helper) {
        ResourceLocation igloo = Ids.parse("chests/igloo_chest");
        ResourceLocation tower = Ids.of("test", "chests/tower");
        ResourceLocation gone = Ids.of("test", "chests/gone");
        Map<ResourceLocation, LootOverrides.Status> statuses = new LinkedHashMap<>();
        statuses.put(igloo, LootOverrides.Status.ACTIVE);
        statuses.put(tower, LootOverrides.Status.ORIGINAL_CHANGED);
        statuses.put(gone, LootOverrides.Status.BROKEN);
        byte[] written = bytes(helper, buf -> Codecs.writeOverrides(buf, statuses));
        // Each status is sent as its number, so the numbers are pinned here too: the enum's order must not change.
        same(helper, written, bytes(helper, buf -> {
            buf.writeVarInt(3);
            buf.writeResourceLocation(igloo);
            buf.writeVarInt(1);
            buf.writeResourceLocation(tower);
            buf.writeVarInt(2);
            buf.writeResourceLocation(gone);
            buf.writeVarInt(4);
        }), "the overridden tables");
        Map<ResourceLocation, LootOverrides.Status> back = read(helper, written, Codecs::readOverrides);
        helper.assertTrue(back.equals(statuses), "sent " + statuses + " but read " + back);

        // A status only a newer version knows is skipped, and the rest still read.
        Map<ResourceLocation, LootOverrides.Status> known = read(helper, bytes(helper, buf -> {
            buf.writeVarInt(2);
            buf.writeResourceLocation(igloo);
            buf.writeVarInt(99);
            buf.writeResourceLocation(tower);
            buf.writeVarInt(1);
        }), Codecs::readOverrides);
        helper.assertTrue(known.equals(Map.of(tower, LootOverrides.Status.ACTIVE)), "with one that isn't known, read " + known);

        ServerPlayer player = TestPlayers.mock(helper);
        ServerConfig.Settings before = ServerConfig.get();
        try {
            ServerConfig.set(packToolsFor(0));
            Map<ResourceLocation, LootOverrides.Status> there = LootOverrides.statuses(helper.getLevel().getServer().getResourceManager());
            Map<ResourceLocation, LootOverrides.Status> told = read(helper, only(helper, sent(player, JesNetwork.OVERRIDES, () -> JesServer.onRequestOverrides(player)), "overrides"),
                    Codecs::readOverrides);
            helper.assertTrue(told.equals(there), "the server has " + there + " but told a player who can use Pack tools " + told);

            ServerConfig.set(packToolsFor(4));
            told = read(helper, only(helper, sent(player, JesNetwork.OVERRIDES, () -> JesServer.onRequestOverrides(player)), "overrides"), Codecs::readOverrides);
            helper.assertTrue(told.isEmpty(), "a player who can't use Pack tools was told " + told);
        } finally {
            ServerConfig.set(before);
        }
        helper.succeed();
    }

    public static void editRepliesReadBack(GameTestHelper helper) {
        ResourceLocation igloo = Ids.parse("chests/igloo_chest");
        Component saved = Component.translatable("screen.justenoughstructures.override.saved");
        LootOdds odds = new LootOdds(igloo, 2000, 12, List.of(
                new LootOdds.Row(new ItemStack(Items.DIAMOND), 40, 55, 1, 3, new TreeMap<>(Map.of("enchantment:minecraft:sharpness", 4)))));
        for (int with = 0; with < 4; with++) {
            Component message = (with & 1) != 0 ? saved : null;
            LootOdds given = (with & 2) != 0 ? odds : null;
            Codecs.EditReply reply = new Codecs.EditReply(7, message, given);
            byte[] written = bytes(helper, buf -> Codecs.writeEditReply(buf, reply));
            same(helper, written, bytes(helper, buf -> {
                buf.writeVarInt(7);
                buf.writeBoolean(message != null);
                if (message != null) {
                    Codecs.writeComponent(buf, message);
                }
                buf.writeBoolean(given != null);
                if (given != null) {
                    Codecs.writeOdds(buf, given);
                }
            }), "an edit's reply");
            Codecs.EditReply back = read(helper, written, Codecs::readEditReply);
            helper.assertTrue(back.requestId() == 7 && (back.message() == null) == (message == null) && (back.odds() == null) == (given == null),
                    "sent " + reply + " but read " + back);
            if (message != null) {
                helper.assertTrue(key(back.message()).equals(key(saved)), "the message read back as " + back.message());
            }
            if (given != null) {
                LootOdds.Row row = back.odds().rows().size() == 1 ? back.odds().rows().get(0) : null;
                helper.assertTrue(back.odds().tableId().equals(igloo) && back.odds().rolls() == 2000 && back.odds().emptyRolls() == 12 && row != null
                                && row.example().is(Items.DIAMOND) && row.hits() == 40 && row.total() == 55 && row.min() == 1 && row.max() == 3
                                && row.variants().equals(Map.of("enchantment:minecraft:sharpness", 4)),
                        "the chances read back as " + back.odds());
            }
            same(helper, bytes(helper, buf -> Codecs.writeEditReply(buf, back)), written, "an edit's reply, read and written again,");
        }

        // A player who can't use Pack tools is told so, in answer to the request they made.
        ServerPlayer player = TestPlayers.mock(helper);
        ServerConfig.Settings before = ServerConfig.get();
        try {
            ServerConfig.set(packToolsFor(4));
            Codecs.EditReply told = read(helper,
                    only(helper, sent(player, JesNetwork.EDIT_REPLY, () -> JesServer.onTableAction(player, 9, igloo, JesNetwork.ACTION_KEEP)), "edit reply"),
                    Codecs::readEditReply);
            helper.assertTrue(told.requestId() == 9 && key(told.message()).endsWith("override.no_permission") && told.odds() == null,
                    "a player who can't use Pack tools was answered with " + told);
        } finally {
            ServerConfig.set(before);
        }
        helper.succeed();
    }

    public static void locateRepliesReadBack(GameTestHelper helper) {
        Component found = Component.translatable("screen.justenoughstructures.locate_busy");
        Codecs.LocateReply reply = new Codecs.LocateReply(5, found);
        byte[] written = bytes(helper, buf -> Codecs.writeLocateReply(buf, reply));
        same(helper, written, bytes(helper, buf -> {
            buf.writeVarInt(5);
            Codecs.writeComponent(buf, found);
        }), "a locate's reply");
        Codecs.LocateReply back = read(helper, written, Codecs::readLocateReply);
        helper.assertTrue(back.requestId() == 5 && key(back.reply()).equals(key(found)), "sent " + reply + " but read " + back);

        // A hidden structure can't be located, and the player is told so in answer to the request they made.
        ServerPlayer player = TestPlayers.mock(helper);
        ResourceLocation igloo = Ids.parse("igloo");
        ServerConfig.Settings before = ServerConfig.get();
        try {
            ServerConfig.set(new ServerConfig.Settings(Set.of(igloo), Set.of(), 0, 2, true, ServerConfig.PackTools.level(4)));
            Codecs.LocateReply told = read(helper, only(helper, sent(player, JesNetwork.LOCATE, () -> JesServer.onRequestLocate(player, 11, igloo, false)), "locate"),
                    Codecs::readLocateReply);
            helper.assertTrue(told.requestId() == 11 && key(told.reply()).endsWith("locate_hidden"), "locating a hidden structure was answered with " + told);
        } finally {
            ServerConfig.set(before);
        }
        helper.succeed();
    }

    public static void openBrowserReadsBack(GameTestHelper helper) {
        ResourceLocation igloo = Ids.parse("igloo");
        byte[] onOne = bytes(helper, buf -> Codecs.writeOpenBrowser(buf, igloo));
        same(helper, onOne, bytes(helper, buf -> {
            buf.writeBoolean(true);
            buf.writeResourceLocation(igloo);
        }), "opening the browser on a structure");
        helper.assertTrue(igloo.equals(read(helper, onOne, Codecs::readOpenBrowser)), "the igloo didn't read back");

        byte[] asLeft = bytes(helper, buf -> Codecs.writeOpenBrowser(buf, null));
        same(helper, asLeft, new byte[]{0}, "opening the browser as it was left");
        helper.assertTrue(read(helper, asLeft, Codecs::readOpenBrowser) == null, "opening the browser on nothing read back as a structure");
        helper.succeed();
    }

    // ------------------------------------------------------------------ helpers

    private static byte[] bytes(GameTestHelper helper, Consumer<FriendlyByteBuf> writer) {
        return Blobs.toBytes(helper.getLevel().registryAccess(), writer);
    }

    /** Reads a whole message, which has to use up every byte of it. */
    private static <T> T read(GameTestHelper helper, byte[] message, Function<FriendlyByteBuf, T> reader) {
        FriendlyByteBuf buf = Blobs.fromBytes(helper.getLevel().registryAccess(), message);
        T value = reader.apply(buf);
        helper.assertTrue(!buf.isReadable(), buf.readableBytes() + " bytes of a message were left unread");
        return value;
    }

    private static void same(GameTestHelper helper, byte[] written, byte[] expected, String what) {
        helper.assertTrue(Arrays.equals(written, expected), what + " was written as " + hex(written) + ", not " + hex(expected)
                + ". If that's meant, JesNetwork.PROTOCOL has to go up");
    }

    private static String hex(byte[] bytes) {
        String all = HexFormat.of().formatHex(bytes);
        return all.length() > 160 ? all.substring(0, 160) + "..." : all;
    }

    /** Every message the server sends {@code player} on {@code channel} while {@code action} runs. Anyone else's go on as usual. */
    static List<byte[]> sent(ServerPlayer player, ResourceLocation channel, Runnable action) {
        List<byte[]> messages = new ArrayList<>();
        JesNetwork.ServerSender before = JesNetwork.serverSender();
        JesNetwork.setServerSender((target, to, buf) -> {
            if (target != player) {
                before.send(target, to, buf);
            } else if (to.equals(channel)) {
                messages.add(Blobs.bytes(buf));
            }
        });
        try {
            action.run();
        } finally {
            JesNetwork.setServerSender(before);
        }
        return messages;
    }

    static byte[] only(GameTestHelper helper, List<byte[]> messages, String what) {
        helper.assertTrue(messages.size() == 1, "the server sent " + messages.size() + " " + what + " messages, not one");
        return messages.get(0);
    }
}
