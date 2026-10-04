package com.finndog.justenoughstructures.network;

import com.finndog.justenoughstructures.JustEnoughStructures;
import java.util.function.BiPredicate;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * Channel ids and the hooks each loader fills in to actually send packets.
 *
 * <p>The client asks for things with small requests. Big answers (the structure list and captured
 * structures) come back as compressed blobs split into {@link #TRANSFER} parts, since a single
 * packet can't carry more than 1 MB.
 */
public final class JesNetwork {
    /**
     * Goes up whenever what's sent changes in a way the other end couldn't read. It's part of every
     * channel's name, so a client and a server on different versions don't hear each other at all,
     * rather than misreading what they hear.
     */
    public static final int PROTOCOL = 2;

    public static final ResourceLocation REQUEST_CATALOG = channel("request_catalog");
    public static final ResourceLocation REQUEST_CAPTURE = channel("request_capture");
    public static final ResourceLocation REQUEST_LOOT = channel("request_loot");
    public static final ResourceLocation REQUEST_ODDS = channel("request_odds");
    public static final ResourceLocation REQUEST_INDEX = channel("request_index");
    public static final ResourceLocation REQUEST_LOCATE = channel("request_locate");
    public static final ResourceLocation REQUEST_COMPASS = channel("request_compass");
    public static final ResourceLocation REQUEST_TABLE = channel("request_table");
    public static final ResourceLocation TABLE_ACTION = channel("table_action");
    public static final ResourceLocation CONTAINER_ACTION = channel("container_action");
    public static final ResourceLocation SPAWNER_ACTION = channel("spawner_action");
    /** Parts of something bigger than one packet from the client, like an edited loot table. */
    public static final ResourceLocation UPLOAD = channel("upload");

    public static final ResourceLocation TRANSFER = channel("transfer");
    public static final ResourceLocation LOOT = channel("loot");
    public static final ResourceLocation ODDS = channel("odds");
    public static final ResourceLocation INDEX_PROGRESS = channel("index_progress");
    public static final ResourceLocation LOCATE = channel("locate");
    public static final ResourceLocation SETTINGS = channel("settings");
    public static final ResourceLocation EDIT_REPLY = channel("edit_reply");
    /** Asks which loot tables have an override, and the answer: each table and how it stands. */
    public static final ResourceLocation REQUEST_OVERRIDES = channel("request_overrides");
    public static final ResourceLocation OVERRIDES = channel("overrides");
    /** Asks for everything Pack tools shows, which comes back as a {@link #KIND_TOOLS} transfer. */
    public static final ResourceLocation REQUEST_TOOLS = channel("request_tools");
    /** Opens a player's browser, on a structure if one is given, for /jes open. */
    public static final ResourceLocation OPEN_BROWSER = channel("open_browser");
    /** Pack tools changing the server's rules or what's said about a structure, or running /reload. */
    public static final ResourceLocation TOOLS_ACTION = channel("tools_action");

    public static final int KIND_CATALOG = 0;
    public static final int KIND_CAPTURE = 1;
    public static final int KIND_INDEX = 2;
    public static final int KIND_TABLE = 3;
    /** Uploads: an edited loot table to roll, and one to save. */
    public static final int KIND_DRAFT = 4;
    public static final int KIND_SAVE = 5;
    public static final int KIND_TOOLS = 6;
    /** An upload: an edited loot table to fill one container from, answered like {@link #LOOT}. */
    public static final int KIND_DRAFT_ROLL = 7;

    /** What {@link #TABLE_ACTION} asks for. */
    public static final int ACTION_KEEP = 0;
    public static final int ACTION_REMOVE = 1;

    /** What {@link #TOOLS_ACTION} asks for. */
    public static final int TOOLS_RULES = 0;
    public static final int TOOLS_STRUCTURE = 1;
    public static final int TOOLS_RELOAD = 2;

    private static ServerSender serverSender = (player, channel, buf) -> {
    };
    private static BiPredicate<ServerPlayer, ResourceLocation> serverCanSend = (player, channel) -> false;

    private JesNetwork() {
    }

    private static ResourceLocation channel(String name) {
        return JustEnoughStructures.id("v" + PROTOCOL + "/" + name);
    }

    /** Whether a channel is one of ours from any version, to tell a server on another version from one without the mod. */
    public static boolean isJesChannel(ResourceLocation channel) {
        return channel.getNamespace().equals(JustEnoughStructures.MOD_ID);
    }

    public static void setServerSender(ServerSender sender) {
        serverSender = sender;
    }

    public static void setServerCanSend(BiPredicate<ServerPlayer, ResourceLocation> canSend) {
        serverCanSend = canSend;
    }

    /** Whether the player's game listens on this channel, so has the mod, and the same version of it. */
    public static boolean canSend(ServerPlayer player, ResourceLocation channel) {
        try {
            return serverCanSend.test(player, channel);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static void send(ServerPlayer player, ResourceLocation channel, FriendlyByteBuf buf) {
        serverSender.send(player, channel, buf);
    }

    @FunctionalInterface
    public interface ServerSender {
        void send(ServerPlayer player, ResourceLocation channel, FriendlyByteBuf buf);
    }
}
