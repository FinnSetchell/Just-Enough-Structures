package com.finndog.justenoughstructures.fabric;

import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.loot.LootOdds;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.Codecs;
import com.finndog.justenoughstructures.network.JesNetwork;
import java.util.List;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

final class FabricClientNetworking {
    private FabricClientNetworking() {
    }

    static void registerClient() {
        ClientRequests.setSender(new ClientRequests.ClientSender() {
            @Override
            public boolean canSend(ResourceLocation channel) {
                return ClientPlayNetworking.canSend(channel);
            }

            @Override
            public void send(ResourceLocation channel, FriendlyByteBuf buf) {
                ClientPlayNetworking.send(channel, buf);
            }
        });

        ClientPlayNetworking.registerGlobalReceiver(JesNetwork.TRANSFER, (client, handler, buf, responder) -> {
            Blobs.Part part = Blobs.Part.read(buf);
            client.execute(() -> ClientRequests.onTransferPart(part));
        });
        ClientPlayNetworking.registerGlobalReceiver(JesNetwork.LOOT, (client, handler, buf, responder) -> {
            int requestId = buf.readVarInt();
            List<ItemStack> items = Codecs.readItems(buf);
            client.execute(() -> ClientRequests.onLoot(requestId, items));
        });
        ClientPlayNetworking.registerGlobalReceiver(JesNetwork.ODDS, (client, handler, buf, responder) -> {
            int requestId = buf.readVarInt();
            LootOdds odds = Codecs.readOdds(buf);
            client.execute(() -> ClientRequests.onOdds(requestId, odds));
        });

        ClientPlayNetworking.registerGlobalReceiver(JesNetwork.INDEX_PROGRESS, (client, handler, buf, responder) -> {
            int done = buf.readVarInt();
            int total = buf.readVarInt();
            client.execute(() -> ClientRequests.onIndexProgress(done, total));
        });

        ClientPlayNetworking.registerGlobalReceiver(JesNetwork.LOCATE, (client, handler, buf, responder) -> {
            int requestId = buf.readVarInt();
            Component reply = buf.readComponent();
            client.execute(() -> ClientRequests.onLocate(requestId, reply));
        });

        ClientPlayNetworking.registerGlobalReceiver(JesNetwork.SETTINGS, (client, handler, buf, responder) -> {
            int locate = buf.readVarInt();
            int teleport = buf.readVarInt();
            boolean reloaded = buf.readBoolean();
            boolean compass = buf.readBoolean();
            int edit = buf.readVarInt();
            client.execute(() -> ClientRequests.onSettings(locate, teleport, reloaded, compass, edit));
        });

        ClientPlayNetworking.registerGlobalReceiver(JesNetwork.EDIT_REPLY, (client, handler, buf, responder) -> {
            int requestId = buf.readVarInt();
            Component message = buf.readBoolean() ? buf.readComponent() : null;
            LootOdds odds = buf.readBoolean() ? Codecs.readOdds(buf) : null;
            client.execute(() -> ClientRequests.onEditReply(requestId, message, odds));
        });

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ClientRequests::reset));
    }
}
