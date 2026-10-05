package com.finndog.justenoughstructures.forge;

import com.finndog.justenoughstructures.JustEnoughStructures;
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
 * Every packet goes through one Forge channel, carrying the id of the JES channel it's for, so the
 * handlers are the same as on Fabric. The protocol is part of the channel's name: a client and a
 * server on different versions of JES each see the other's channel as missing, which Forge lets
 * them join with, and then don't hear each other, as on Fabric.
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

    /** What the client does with a packet from the server. Set by the client's setup, so a server never loads it. */
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

    // Read where it arrives, as the handlers expect: from a player on the server, else on the client.
    private static void handle(Packet packet, ServerPlayer player) {
        if (player != null) {
            ServerPackets.Handler handler = ServerPackets.handlers().get(packet.channel());
            if (handler != null) {
                handler.handle(player.server, player, Blobs.fromBytes(player.server.registryAccess(), packet.data()));
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

        // Copied out, as the buffer it's read from is let go once this returns.
        static Packet read(FriendlyByteBuf buf) {
            ResourceLocation channel = buf.readResourceLocation();
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new Packet(channel, bytes);
        }
    }
}
