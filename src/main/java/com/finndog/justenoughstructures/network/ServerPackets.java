package com.finndog.justenoughstructures.network;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.server.JesServer;
import com.finndog.justenoughstructures.server.RequestLimits;
import com.finndog.justenoughstructures.server.ServerConfig;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * What the server does with each packet a client sends, the same on every loader. Each one is read
 * where it arrives, off the server thread, and then handed to the server thread for anything that
 * touches the game. The loaders only connect their own networking to these.
 *
 * <p>Each kind of request costs a player some of their {@link RequestLimits allowance}, the big
 * answers more, and one that comes when they have none left is ignored.
 */
public final class ServerPackets {
    private static final Map<ResourceLocation, Handler> HANDLERS = new LinkedHashMap<>();
    /** What most requests cost, what one for the whole structure list or loot index costs, and what a search of the world costs. */
    private static final double SMALL = 1;
    private static final double BIG = 10;
    private static final double SEARCH = 5;

    static {
        on(JesNetwork.REQUEST_CATALOG, BIG, (server, player, buf) -> server.execute(() -> JesServer.onRequestCatalog(player)));

        on(JesNetwork.REQUEST_CAPTURE, (server, player, buf) -> {
            int requestId = buf.readVarInt();
            ResourceLocation structure = buf.readResourceLocation();
            long seed = buf.readLong();
            boolean preview = buf.readBoolean();
            long kept = buf.readLong();
            server.execute(() -> JesServer.onRequestCapture(player, requestId, structure, seed, preview, kept));
        });

        on(JesNetwork.REQUEST_LOOT, (server, player, buf) -> {
            int requestId = buf.readVarInt();
            ResourceLocation table = buf.readResourceLocation();
            long seed = buf.readLong();
            int size = buf.readVarInt();
            server.execute(() -> JesServer.onRequestLoot(player, requestId, table, seed, size));
        });

        on(JesNetwork.REQUEST_ODDS, (server, player, buf) -> {
            int requestId = buf.readVarInt();
            ResourceLocation table = buf.readResourceLocation();
            server.execute(() -> JesServer.onRequestOdds(player, requestId, table));
        });

        on(JesNetwork.REQUEST_INDEX, BIG, (server, player, buf) -> server.execute(() -> JesServer.onRequestIndex(player)));

        on(JesNetwork.REQUEST_TABLE, (server, player, buf) -> {
            int requestId = buf.readVarInt();
            ResourceLocation table = buf.readResourceLocation();
            server.execute(() -> JesServer.onRequestTable(player, requestId, table));
        });

        on(JesNetwork.REQUEST_OVERRIDES, (server, player, buf) -> server.execute(() -> JesServer.onRequestOverrides(player)));

        on(JesNetwork.TABLE_ACTION, (server, player, buf) -> {
            int requestId = buf.readVarInt();
            ResourceLocation table = buf.readResourceLocation();
            int action = buf.readVarInt();
            server.execute(() -> JesServer.onTableAction(player, requestId, table, action));
        });

        on(JesNetwork.CONTAINER_ACTION, (server, player, buf) -> {
            int requestId = buf.readVarInt();
            ResourceLocation template = buf.readResourceLocation();
            BlockPos pos = buf.readBlockPos();
            ResourceLocation table = buf.readBoolean() ? buf.readResourceLocation() : null;
            server.execute(() -> JesServer.onContainerAction(player, requestId, template, pos, table));
        });

        on(JesNetwork.SPAWNER_ACTION, (server, player, buf) -> {
            int requestId = buf.readVarInt();
            ResourceLocation template = buf.readResourceLocation();
            BlockPos pos = buf.readBlockPos();
            String mob = null;
            ResourceLocation block = null;
            if (buf.readBoolean()) {
                mob = buf.readUtf(256);
                block = buf.readResourceLocation();
            }
            String setMob = mob;
            ResourceLocation setBlock = block;
            server.execute(() -> JesServer.onSpawnerAction(player, requestId, template, pos, setMob, setBlock));
        });

        on(JesNetwork.REQUEST_TOOLS, (server, player, buf) -> server.execute(() -> JesServer.onRequestTools(player)));

        on(JesNetwork.TOOLS_ACTION, (server, player, buf) -> {
            int requestId = buf.readVarInt();
            int action = buf.readVarInt();
            if (action == JesNetwork.TOOLS_RULES) {
                ServerConfig.Settings settings = Codecs.readSettings(buf);
                server.execute(() -> JesServer.onSaveRules(player, requestId, settings));
            } else if (action == JesNetwork.TOOLS_STRUCTURE) {
                ResourceLocation id = buf.readResourceLocation();
                String notes = buf.readUtf(Codecs.MAX_NOTES);
                boolean secret = buf.readBoolean();
                server.execute(() -> JesServer.onSaveStructure(player, requestId, id, notes, secret));
            } else if (action == JesNetwork.TOOLS_RELOAD) {
                server.execute(() -> JesServer.onReload(player, requestId));
            }
        });

        on(JesNetwork.UPLOAD, (server, player, buf) -> JesServer.onUploadPart(server, player, Blobs.Part.read(buf)));

        on(JesNetwork.REQUEST_COMPASS, SEARCH, (server, player, buf) -> {
            int requestId = buf.readVarInt();
            ResourceLocation structure = buf.readResourceLocation();
            JesServer.queueCompass(server, player, requestId, structure);
        });

        on(JesNetwork.REQUEST_LOCATE, SEARCH, (server, player, buf) -> {
            int requestId = buf.readVarInt();
            ResourceLocation structure = buf.readResourceLocation();
            boolean teleport = buf.readBoolean();
            JesServer.queueLocate(server, player, requestId, structure, teleport);
        });
    }

    private ServerPackets() {
    }

    private static void on(ResourceLocation channel, Handler handler) {
        on(channel, SMALL, handler);
    }

    private static void on(ResourceLocation channel, double cost, Handler handler) {
        HANDLERS.put(channel, (server, player, buf) -> {
            if (RequestLimits.request(player.getUUID(), cost)) {
                handler.handle(server, player, buf);
            } else {
                JesLog.warnOnce("requests:" + player.getUUID(), "{} is asking the server for things far faster than the browser does, so some are being ignored",
                        player.getName().getString());
            }
        });
    }

    /** Every channel a client can send on, and what's done with what arrives. */
    public static Map<ResourceLocation, Handler> handlers() {
        return Collections.unmodifiableMap(HANDLERS);
    }

    @FunctionalInterface
    public interface Handler {
        /** Called where the packet arrives, with it still readable, which may be off the server thread. */
        void handle(MinecraftServer server, ServerPlayer player, FriendlyByteBuf buf);
    }
}
