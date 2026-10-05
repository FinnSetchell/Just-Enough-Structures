package com.finndog.justenoughstructures.network;

//? if >=1.21 {
/*import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// One JES packet in the payload system 1.20.5 brought in: a type of its own for each JES channel,
// carrying the bytes written for it, so the handlers stay the same on every loader.
public record JesPayload(CustomPacketPayload.Type<JesPayload> type, byte[] data) implements CustomPacketPayload {
    private static final Map<ResourceLocation, CustomPacketPayload.Type<JesPayload>> TYPES = new ConcurrentHashMap<>();

    public static CustomPacketPayload.Type<JesPayload> type(ResourceLocation channel) {
        return TYPES.computeIfAbsent(channel, CustomPacketPayload.Type::new);
    }

    public static StreamCodec<RegistryFriendlyByteBuf, JesPayload> codec(CustomPacketPayload.Type<JesPayload> type) {
        return StreamCodec.of((buf, payload) -> buf.writeBytes(payload.data()), buf -> {
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            return new JesPayload(type, data);
        });
    }
}
*///?}
