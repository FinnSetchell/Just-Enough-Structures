package com.finndog.justenoughstructures.fabric;

import com.finndog.justenoughstructures.client.ClientPackets;
import com.finndog.justenoughstructures.client.ClientRequests;
import java.util.Collection;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

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
            public Collection<ResourceLocation> sendable() {
                return ClientPlayNetworking.getSendable();
            }

            @Override
            public void send(ResourceLocation channel, FriendlyByteBuf buf) {
                ClientPlayNetworking.send(channel, buf);
            }
        });

        ClientPackets.handlers().forEach((channel, handler) -> ClientPlayNetworking.registerGlobalReceiver(channel,
                (client, listener, buf, responder) -> handler.handle(client, buf)));

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ClientRequests::reset));
    }
}
