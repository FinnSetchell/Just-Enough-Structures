package com.finndog.justenoughstructures.client;

import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import com.finndog.justenoughstructures.network.JesNetwork;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * What the client does with each packet the server sends, the same on every loader. Each one is
 * read where it arrives, off the game thread, and then handed to the game thread. The loaders only
 * connect their own networking to these.
 */
public final class ClientPackets {
    private static final Map<ResourceLocation, Handler> HANDLERS = new LinkedHashMap<>();

    static {
        on(JesNetwork.TRANSFER, (client, buf) -> {
            Blobs.Part part = Blobs.Part.read(buf);
            client.execute(() -> ClientRequests.onTransferPart(part));
        });

        on(JesNetwork.LOOT, (client, buf) -> {
            int requestId = buf.readVarInt();
            List<ItemStack> items = Codecs.readItems(buf);
            client.execute(() -> ClientRequests.onLoot(requestId, items));
        });

        on(JesNetwork.ODDS, (client, buf) -> {
            int requestId = buf.readVarInt();
            LootOdds odds = Codecs.readOdds(buf);
            client.execute(() -> ClientRequests.onOdds(requestId, odds));
        });

        on(JesNetwork.INDEX_PROGRESS, (client, buf) -> {
            int done = buf.readVarInt();
            int total = buf.readVarInt();
            client.execute(() -> ClientRequests.onIndexProgress(done, total));
        });

        on(JesNetwork.LOCATE, (client, buf) -> {
            int requestId = buf.readVarInt();
            Component reply = buf.readComponent();
            client.execute(() -> ClientRequests.onLocate(requestId, reply));
        });

        on(JesNetwork.OPEN_BROWSER, (client, buf) -> {
            ResourceLocation structure = buf.readBoolean() ? buf.readResourceLocation() : null;
            client.execute(() -> JesClient.openBrowser(client, structure));
        });

        on(JesNetwork.SETTINGS, (client, buf) -> {
            int locate = buf.readVarInt();
            int teleport = buf.readVarInt();
            boolean reloaded = buf.readBoolean();
            boolean compass = buf.readBoolean();
            boolean packTools = buf.readBoolean();
            boolean structuresChanged = buf.readBoolean();
            client.execute(() -> ClientRequests.onSettings(locate, teleport, reloaded, compass, packTools, structuresChanged));
        });

        on(JesNetwork.OVERRIDES, (client, buf) -> {
            Map<ResourceLocation, LootOverrides.Status> statuses = new HashMap<>();
            LootOverrides.Status[] all = LootOverrides.Status.values();
            int count = buf.readVarInt();
            for (int i = 0; i < count; i++) {
                ResourceLocation id = buf.readResourceLocation();
                int status = buf.readVarInt();
                if (status >= 0 && status < all.length) {
                    statuses.put(id, all[status]);
                }
            }
            client.execute(() -> ClientRequests.onOverrides(statuses));
        });

        on(JesNetwork.EDIT_REPLY, (client, buf) -> {
            int requestId = buf.readVarInt();
            Component message = buf.readBoolean() ? buf.readComponent() : null;
            LootOdds odds = buf.readBoolean() ? Codecs.readOdds(buf) : null;
            client.execute(() -> ClientRequests.onEditReply(requestId, message, odds));
        });
    }

    private ClientPackets() {
    }

    private static void on(ResourceLocation channel, Handler handler) {
        HANDLERS.put(channel, handler);
    }

    /** Every channel the server can send on, and what's done with what arrives. */
    public static Map<ResourceLocation, Handler> handlers() {
        return Collections.unmodifiableMap(HANDLERS);
    }

    @FunctionalInterface
    public interface Handler {
        /** Called where the packet arrives, with it still readable, which may be off the game thread. */
        void handle(Minecraft client, FriendlyByteBuf buf);
    }
}
