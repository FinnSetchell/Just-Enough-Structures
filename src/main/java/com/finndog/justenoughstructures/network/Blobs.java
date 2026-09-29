package com.finndog.justenoughstructures.network;

import io.netty.buffer.Unpooled;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;
import net.minecraft.network.FriendlyByteBuf;

/** Compression and splitting for payloads that can be bigger than one packet. */
public final class Blobs {
    /** Leaves room under the 1 MB clientbound limit for the part header. */
    public static final int PART_SIZE = 900_000;

    private Blobs() {
    }

    public static byte[] toBytes(Consumer<FriendlyByteBuf> writer) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        try {
            writer.accept(buf);
            byte[] out = new byte[buf.readableBytes()];
            buf.readBytes(out);
            return out;
        } finally {
            buf.release();
        }
    }

    public static FriendlyByteBuf fromBytes(byte[] bytes) {
        return new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
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
        List<byte[]> parts = new ArrayList<>();
        for (int start = 0; start < data.length || parts.isEmpty(); start += PART_SIZE) {
            int end = Math.min(data.length, start + PART_SIZE);
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
