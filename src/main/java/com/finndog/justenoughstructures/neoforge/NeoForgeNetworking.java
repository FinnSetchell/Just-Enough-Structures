package com.finndog.justenoughstructures.neoforge;

import com.finndog.justenoughstructures.Players;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.JesNetwork;
import com.finndog.justenoughstructures.network.JesPayload;
import com.finndog.justenoughstructures.network.ServerPackets;
import java.util.function.BiConsumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.HandlerThread;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Each JES channel is a separate optional payload type, as on Fabric, carrying what the handlers
 * read. As they're optional, players without JES can join a server with it, and the reverse. Each is
 * read on the thread it arrives on, as the handlers expect.
 */
final class NeoForgeNetworking {
    /** The client's handler for packets from the server. Set by the client setup, so a server never loads it. */
    static volatile BiConsumer<ResourceLocation, FriendlyByteBuf> clientHandler = (channel, buf) -> {
    };

    private NeoForgeNetworking() {
    }

    static void register(IEventBus modBus) {
        modBus.addListener((RegisterPayloadHandlersEvent event) -> {
            PayloadRegistrar registrar = event.registrar("1").optional().executesOn(HandlerThread.NETWORK);
            ServerPackets.handlers().forEach((channel, handler) -> registrar.playToServer(JesPayload.type(channel),
                    JesPayload.codec(JesPayload.type(channel)), (payload, context) -> {
                        ServerPlayer player = (ServerPlayer) context.player();
                        handler.handle(Players.server(player), player, Blobs.fromBytes(Players.server(player).registryAccess(), payload.data()));
                    }));
            for (ResourceLocation channel : JesNetwork.CLIENTBOUND) {
                registrar.playToClient(JesPayload.type(channel), JesPayload.codec(JesPayload.type(channel)),
                        (payload, context) -> clientHandler.accept(channel, Blobs.fromBytes(context.player().level().registryAccess(), payload.data())));
            }
            //? if >=26.1 {
            /*// From 26.1 a payload sent both ways has a separate handler on each side. Both do nothing here.
            registrar.playBidirectional(JesPayload.type(JesNetwork.PRESENT), JesPayload.codec(JesPayload.type(JesNetwork.PRESENT)),
                    (payload, context) -> {
                    }, (payload, context) -> {
                    });
            *///?} else {
            registrar.playBidirectional(JesPayload.type(JesNetwork.PRESENT), JesPayload.codec(JesPayload.type(JesNetwork.PRESENT)),
                    (payload, context) -> {
                    });
            //?}
        });
        JesNetwork.setServerSender((player, channel, buf) -> PacketDistributor.sendToPlayer(player, new JesPayload(JesPayload.type(channel), Blobs.bytes(buf))));
        JesNetwork.setServerCanSend((player, channel) -> player.connection.hasChannel(JesPayload.type(channel)));
    }
}
