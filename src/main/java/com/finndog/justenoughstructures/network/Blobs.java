package com.finndog.justenoughstructures.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
//? if >=1.21 {
/*import net.minecraft.network.RegistryFriendlyByteBuf;
*///?}

/** Compression and splitting for payloads that can be bigger than one packet. */
public final class Blobs {
    /** Leaves room under the 1 MB clientbound limit for the part header. */
    public static final int PART_SIZE = 900_000;
    /** The same under the 32 KB limit on what a client can send in one packet. */
    public static final int UPLOAD_PART_SIZE = 30_000;

    private Blobs() {
    }

    /**
     * A buffer to write a packet into. Items and text need the game's registries to be written from
     * 1.20.5, so every buffer is made with them, though older versions don't use them.
     */
    public static FriendlyByteBuf buffer(RegistryAccess registries) {
        return wrap(Unpooled.buffer(), registries);
    }

    public static byte[] toBytes(RegistryAccess registries, Consumer<FriendlyByteBuf> writer) {
        FriendlyByteBuf buf = buffer(registries);
        try {
            writer.accept(buf);
            return bytes(buf);
        } finally {
            buf.release();
        }
    }

    public static FriendlyByteBuf fromBytes(RegistryAccess registries, byte[] bytes) {
        return wrap(Unpooled.wrappedBuffer(bytes), registries);
    }

    /** What's left to read in {@code buf}, copied out. */
    public static byte[] bytes(FriendlyByteBuf buf) {
        byte[] out = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), out);
        return out;
    }

    private static FriendlyByteBuf wrap(ByteBuf bytes, RegistryAccess registries) {
        //? if >=1.21 {
        /*return new RegistryFriendlyByteBuf(bytes, registries);
        *///?} else {
        return new FriendlyByteBuf(bytes);
        //?}
    }

    public static byte[] deflate(byte[] raw) {
        Deflater deflater = new Deflater(Deflater.BEST_SPEED);
        try {
            deflater.setInput(raw);
            deflater.finish();
            ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, raw.length / 4));
            byte[] chunk = new byte[64 * 1024];
            while (!deflater.finished()) {
                out.write(chunk, 0, deflater.deflate(chunk));
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    public static byte[] inflate(byte[] compressed) {
        Inflater inflater = new Inflater();
        try {
            inflater.setInput(compressed);
            ByteArrayOutputStream out = new ByteArrayOutputStream(compressed.length * 4);
            byte[] chunk = new byte[64 * 1024];
            while (!inflater.finished()) {
                int n = inflater.inflate(chunk);
                if (n == 0 && (inflater.needsInput() || inflater.needsDictionary())) {
                    throw new IllegalStateException("Truncated payload");
                }
                out.write(chunk, 0, n);
            }
            return out.toByteArray();
        } catch (DataFormatException e) {
            throw new IllegalStateException("Corrupt payload", e);
        } finally {
            inflater.end();
        }
    }

    public static List<byte[]> split(byte[] data) {
        return split(data, PART_SIZE);
    }

    public static List<byte[]> split(byte[] data, int size) {
        List<byte[]> parts = new ArrayList<>();
        for (int start = 0; start < data.length || parts.isEmpty(); start += size) {
            int end = Math.min(data.length, start + size);
            byte[] part = new byte[end - start];
            System.arraycopy(data, start, part, 0, part.length);
            parts.add(part);
        }
        return parts;
    }

    /** One {@link JesNetwork#TRANSFER} packet. */
    public record Part(int transferId, int kind, int requestId, int index, int count, byte[] data) {
        public void write(FriendlyByteBuf buf) {
            buf.writeVarInt(transferId);
            buf.writeByte(kind);
            buf.writeVarInt(requestId);
            buf.writeVarInt(index);
            buf.writeVarInt(count);
            buf.writeByteArray(data);
        }

        public static Part read(FriendlyByteBuf buf) {
            return new Part(buf.readVarInt(), buf.readByte(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readByteArray(PART_SIZE + 16));
        }
    }
}
