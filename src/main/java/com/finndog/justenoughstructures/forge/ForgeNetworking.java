package com.finndog.justenoughstructures.forge;

import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.network.JesNetwork;
import com.finndog.justenoughstructures.network.ServerPackets;
import io.netty.buffer.Unpooled;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * Every packet goes through one Forge channel, carrying the id of the JES channel it's for, so the
 * handlers are the same as on Fabric. The protocol is part of the channel's name: a client and a
 * server on different versions of JES each see the other's channel as missing, which Forge lets
 * them join with, and then don't hear each other, as on Fabric.
 */
final class ForgeNetworking {
    static final ResourceLocation NAME = JustEnoughStructures.id("v" + JesNetwork.PROTOCOL + "/forge");
    private static final String VERSION = "1";

    static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(NAME, () -> VERSION,
            NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));

    /** What the client does with a packet from the server. Set by the client's setup, so a server never loads it. */
    static volatile BiConsumer<ResourceLocation, FriendlyByteBuf> clientHandler = (channel, buf) -> {
    };

    private ForgeNetworking() {
    }

    static void register() {
        CHANNEL.registerMessage(0, Packet.class, Packet::write, Packet::read, ForgeNetworking::handle);
        JesNetwork.setServerSender((player, channel, buf) -> CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new Packet(channel, buf)));
        JesNetwork.setServerCanSend((player, channel) -> CHANNEL.isRemotePresent(player.connection.connection));
    }

    private static void handle(Packet packet, Supplier<NetworkEvent.Context> context) {
        NetworkEvent.Context ctx = context.get();
        ServerPlayer player = ctx.getSender();
        if (player != null) {
            ServerPackets.Handler handler = ServerPackets.handlers().get(packet.channel());
            if (handler != null) {
                handler.handle(player.server, player, packet.data());
            }
        } else {
            clientHandler.accept(packet.channel(), packet.data());
        }
        ctx.setPacketHandled(true);
    }

    /** One JES packet: the channel it's for and what was written to it. */
    record Packet(ResourceLocation channel, FriendlyByteBuf data) {
        void write(FriendlyByteBuf buf) {
            buf.writeResourceLocation(channel);
            buf.writeBytes(data, data.readerIndex(), data.readableBytes());
        }

        // Copied out, as the buffer it's read from is let go once this returns.
        static Packet read(FriendlyByteBuf buf) {
            ResourceLocation channel = buf.readResourceLocation();
            byte[] bytes = new byte[buf.readableBytes()];
            buf.readBytes(bytes);
            return new Packet(channel, new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes)));
        }
    }
}
