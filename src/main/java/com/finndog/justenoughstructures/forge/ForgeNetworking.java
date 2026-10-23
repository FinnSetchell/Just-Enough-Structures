package com.finndog.justenoughstructures.forge;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.Players;
import com.finndog.justenoughstructures.network.Blobs;
import com.finndog.justenoughstructures.network.JesNetwork;
import com.finndog.justenoughstructures.network.ServerPackets;
import java.util.function.BiConsumer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
//? if >=1.21 {
/*import net.minecraftforge.event.network.CustomPayloadEvent;
import net.minecraftforge.network.ChannelBuilder;
import net.minecraftforge.network.SimpleChannel;
*///?} else {
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;
//?}

/**
 * All JES packets go through one Forge channel that carries the JES channel id, so the handlers are
 * the same as on Fabric. The channel name includes the protocol, so a client and server on different
 * protocols see each other's channel as missing. Forge lets them join anyway, and they exchange no
 * JES packets, as on Fabric.
 */
final class ForgeNetworking {
    static final ResourceLocation NAME = JustEnoughStructures.id("v" + JesNetwork.PROTOCOL + "/forge");

    //? if >=1.21 {
    /*static final SimpleChannel CHANNEL = ChannelBuilder.named(NAME).networkProtocolVersion(1).optional().simpleChannel();
    *///?} else {
    private static final String VERSION = "1";

    static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(NAME, () -> VERSION,
            NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));
    //?}

    /** The client's handler for packets from the server. Set by the client setup, so a server never loads it. */
    static volatile BiConsumer<ResourceLocation, byte[]> clientHandler = (channel, data) -> {
    };

    private ForgeNetworking() {
    }

    static void register() {
        //? if >=1.21 {
        /*CHANNEL.messageBuilder(Packet.class).encoder(Packet::write).decoder(Packet::read).consumerNetworkThread((BiConsumer<Packet, CustomPayloadEvent.Context>) ForgeNetworking::handle).add();
        JesNetwork.setServerSender((player, channel, buf) -> CHANNEL.send(new Packet(channel, Blobs.bytes(buf)), PacketDistributor.PLAYER.with(player)));
        JesNetwork.setServerCanSend((player, channel) -> CHANNEL.isRemotePresent(player.connection.getConnection()));
        *///?} else {
        CHANNEL.registerMessage(0, Packet.class, Packet::write, Packet::read, (packet, context) -> {
            handle(packet, context.get().getSender());
            context.get().setPacketHandled(true);
        });
        JesNetwork.setServerSender((player, channel, buf) -> CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Packet(channel, Blobs.bytes(buf))));
        JesNetwork.setServerCanSend((player, channel) -> CHANNEL.isRemotePresent(player.connection.connection));
        //?}
    }

    /** Sends a packet to the server, from the client. */
    static void sendToServer(ResourceLocation channel, FriendlyByteBuf buf) {
        //? if >=1.21 {
        /*CHANNEL.send(new Packet(channel, Blobs.bytes(buf)), PacketDistributor.SERVER.noArg());
        *///?} else {
        CHANNEL.sendToServer(new Packet(channel, Blobs.bytes(buf)));
        //?}
    }

    //? if >=1.21 {
    /*private static void handle(Packet packet, CustomPayloadEvent.Context context) {
        handle(packet, context.getSender());
        context.setPacketHandled(true);
    }
    *///?}

    // Read on the thread it arrives on, as the handlers expect: from a player on the server, otherwise on the client.
    private static void handle(Packet packet, ServerPlayer player) {
        if (player != null) {
            ServerPackets.Handler handler = ServerPackets.handlers().get(packet.channel());
            if (handler != null) {
                handler.handle(Players.server(player), player, Blobs.fromBytes(Players.server(player).registryAccess(), packet.data()));
            }
        } else {
            clientHandler.accept(packet.channel(), packet.data());
        }
    }

    /** One JES packet: the channel it's for and what was written to it. */
    record Packet(ResourceLocation channel, byte[] data) {
        void write(FriendlyByteBuf buf) {
            buf.writeResourceLocation(channel);
            buf.writeBytes(data);
        }

        // Copied out, since the buffer it's read from is released once this returns.
        static Packet read(FriendlyByteBuf buf) {
            ResourceLocation channel = buf.readResourceLocation();
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new Packet(channel, bytes);
        }
    }
}
