package com.finndog.justenoughstructures.network;

import com.finndog.justenoughstructures.JustEnoughStructures;
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
    public static final ResourceLocation REQUEST_CATALOG = JustEnoughStructures.id("request_catalog");
    public static final ResourceLocation REQUEST_CAPTURE = JustEnoughStructures.id("request_capture");
    public static final ResourceLocation REQUEST_LOOT = JustEnoughStructures.id("request_loot");
    public static final ResourceLocation REQUEST_ODDS = JustEnoughStructures.id("request_odds");
    public static final ResourceLocation REQUEST_INDEX = JustEnoughStructures.id("request_index");
    public static final ResourceLocation REQUEST_LOCATE = JustEnoughStructures.id("request_locate");
    public static final ResourceLocation REQUEST_COMPASS = JustEnoughStructures.id("request_compass");
    public static final ResourceLocation REQUEST_TABLE = JustEnoughStructures.id("request_table");
    public static final ResourceLocation TABLE_ACTION = JustEnoughStructures.id("table_action");
    public static final ResourceLocation CONTAINER_ACTION = JustEnoughStructures.id("container_action");
    /** Parts of something bigger than one packet from the client, like an edited loot table. */
    public static final ResourceLocation UPLOAD = JustEnoughStructures.id("upload");

    public static final ResourceLocation TRANSFER = JustEnoughStructures.id("transfer");
    public static final ResourceLocation LOOT = JustEnoughStructures.id("loot");
    public static final ResourceLocation ODDS = JustEnoughStructures.id("odds");
    public static final ResourceLocation INDEX_PROGRESS = JustEnoughStructures.id("index_progress");
    public static final ResourceLocation LOCATE = JustEnoughStructures.id("locate");
    public static final ResourceLocation SETTINGS = JustEnoughStructures.id("settings");
    public static final ResourceLocation EDIT_REPLY = JustEnoughStructures.id("edit_reply");
    /** Asks which loot tables have an override, and the answer: each table and how it stands. */
    public static final ResourceLocation REQUEST_OVERRIDES = JustEnoughStructures.id("request_overrides");
    public static final ResourceLocation OVERRIDES = JustEnoughStructures.id("overrides");
    /** Asks for everything Pack tools shows, which comes back as a {@link #KIND_TOOLS} transfer. */
    public static final ResourceLocation REQUEST_TOOLS = JustEnoughStructures.id("request_tools");
    /** Pack tools changing the server's rules or what's said about a structure, or running /reload. */
    public static final ResourceLocation TOOLS_ACTION = JustEnoughStructures.id("tools_action");

    public static final int KIND_CATALOG = 0;
    public static final int KIND_CAPTURE = 1;
    public static final int KIND_INDEX = 2;
    public static final int KIND_TABLE = 3;
    /** Uploads: an edited loot table to roll, and one to save. */
    public static final int KIND_DRAFT = 4;
    public static final int KIND_SAVE = 5;
    public static final int KIND_TOOLS = 6;

    /** What {@link #TABLE_ACTION} asks for. */
    public static final int ACTION_KEEP = 0;
    public static final int ACTION_REMOVE = 1;

    /** What {@link #TOOLS_ACTION} asks for. */
    public static final int TOOLS_RULES = 0;
    public static final int TOOLS_STRUCTURE = 1;
    public static final int TOOLS_RELOAD = 2;

    private static ServerSender serverSender = (player, channel, buf) -> {
    };

    private JesNetwork() {
    }

    public static void setServerSender(ServerSender sender) {
        serverSender = sender;
    }

    public static void send(ServerPlayer player, ResourceLocation channel, FriendlyByteBuf buf) {
        serverSender.send(player, channel, buf);
    }

    @FunctionalInterface
    public interface ServerSender {
        void send(ServerPlayer player, ResourceLocation channel, FriendlyByteBuf buf);
    }
}
