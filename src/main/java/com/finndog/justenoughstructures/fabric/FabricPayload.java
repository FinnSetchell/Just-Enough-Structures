package com.finndog.justenoughstructures.fabric;

//? if >=1.21 {
/*import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

// One JES packet in the payload system 1.20.5 brought in: a type of its own for each JES channel,
// carrying the bytes written for it, so the handlers stay the same as before.
record FabricPayload(CustomPacketPayload.Type<FabricPayload> type, byte[] data) implements CustomPacketPayload {
    private static final Map<ResourceLocation, CustomPacketPayload.Type<FabricPayload>> TYPES = new ConcurrentHashMap<>();

    static CustomPacketPayload.Type<FabricPayload> type(ResourceLocation channel) {
        return TYPES.computeIfAbsent(channel, CustomPacketPayload.Type::new);
    }

    static StreamCodec<RegistryFriendlyByteBuf, FabricPayload> codec(CustomPacketPayload.Type<FabricPayload> type) {
        return StreamCodec.of((buf, payload) -> buf.writeBytes(payload.data()), buf -> {
            byte[] data = new byte[buf.readableBytes()];
            buf.readBytes(data);
            return new FabricPayload(type, data);
        });
    }
}
*///?}
