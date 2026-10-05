package com.finndog.justenoughstructures.fabric;

import com.finndog.justenoughstructures.client.ClientPackets;
import com.finndog.justenoughstructures.client.ClientRequests;
import java.util.Collection;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
//? if >=1.21 {
/*import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.JesPayload;
*///?}

final class FabricClientNetworking {
    private FabricClientNetworking() {
    }

    static void registerClient() {
        ClientRequests.setSender(new ClientRequests.ClientSender() {
            @Override
            public boolean canSend(ResourceLocation channel) {
                //? if >=1.21 {
                /*return ClientPlayNetworking.canSend(JesPayload.type(channel));
                *///?} else {
                return ClientPlayNetworking.canSend(channel);
                //?}
            }

            @Override
            public Collection<ResourceLocation> sendable() {
                return ClientPlayNetworking.getSendable();
            }

            @Override
            public void send(ResourceLocation channel, FriendlyByteBuf buf) {
                //? if >=1.21 {
                /*ClientPlayNetworking.send(new JesPayload(JesPayload.type(channel), Blobs.bytes(buf)));
                *///?} else {
                ClientPlayNetworking.send(channel, buf);
                //?}
            }
        });

        //? if >=1.21 {
        /*ClientPackets.handlers().forEach((channel, handler) -> ClientPlayNetworking.registerGlobalReceiver(JesPayload.type(channel),
                (payload, context) -> handler.handle(context.client(), Blobs.fromBytes(context.player().level().registryAccess(), payload.data()))));
        *///?} else {
        ClientPackets.handlers().forEach((channel, handler) -> ClientPlayNetworking.registerGlobalReceiver(channel,
                (client, listener, buf, responder) -> handler.handle(client, buf)));
        //?}

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(ClientRequests::reset));
    }
}
