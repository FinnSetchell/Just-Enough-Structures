package com.finndog.justenoughstructures.client;

import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import com.finndog.justenoughstructures.network.JesNetwork;
import com.finndog.justenoughstructures.overrides.LootOverrides;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Handles each packet the server sends, the same on every loader. Each one is read on the thread the
 * loader delivers it on, then handed to the game thread. The loaders only connect their networking
 * to these.
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
            Codecs.IndexProgress progress = Codecs.readIndexProgress(buf);
            client.execute(() -> ClientRequests.onIndexProgress(progress.done(), progress.total(), progress.failed(), progress.chestItems()));
        });

        on(JesNetwork.LOCATE, (client, buf) -> {
            Codecs.LocateReply reply = Codecs.readLocateReply(buf);
            client.execute(() -> ClientRequests.onLocate(reply.requestId(), reply.reply()));
        });

        on(JesNetwork.OPEN_BROWSER, (client, buf) -> {
            ResourceLocation structure = Codecs.readOpenBrowser(buf);
            client.execute(() -> JesClient.openBrowser(client, structure));
        });

        on(JesNetwork.SETTINGS, (client, buf) -> {
            Codecs.BrowserSettings settings = Codecs.readBrowserSettings(buf);
            client.execute(() -> ClientRequests.onSettings(settings));
        });

        on(JesNetwork.OVERRIDES, (client, buf) -> {
            Map<ResourceLocation, LootOverrides.Status> statuses = Codecs.readOverrides(buf);
            client.execute(() -> ClientRequests.onOverrides(statuses));
        });

        on(JesNetwork.EDIT_REPLY, (client, buf) -> {
            Codecs.EditReply reply = Codecs.readEditReply(buf);
            client.execute(() -> ClientRequests.onEditReply(reply.requestId(), reply.message(), reply.odds()));
        });
    }

    private ClientPackets() {
    }

    private static void on(ResourceLocation channel, Handler handler) {
        HANDLERS.put(channel, handler);
    }

    /** Every channel the server can send on, and its handler. */
    public static Map<ResourceLocation, Handler> handlers() {
        return Collections.unmodifiableMap(HANDLERS);
    }

    @FunctionalInterface
    public interface Handler {
        /** Called where the packet arrives, with it still readable, which may be off the game thread. */
        void handle(Minecraft client, FriendlyByteBuf buf);
    }
}
