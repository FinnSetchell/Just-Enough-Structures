package com.finndog.justenoughstructures.server;

import com.finndog.justenoughstructures.network.Blobs;
import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Puts back together what a client sends in parts, like an edited loot table, since one packet
 * from a client can't carry more than 32 KB. Limited per player, so nobody can fill the server's
 * memory with half-sent uploads.
 */
final class Uploads {
    /** About 3 MB, compressed. A loot table is a small fraction of that. */
    private static final int MAX_PARTS = 100;
    private static final int MAX_PENDING = 4;

    private static final Map<UUID, Map<Integer, Pending>> PENDING = new ConcurrentHashMap<>();

    record Done(int kind, int requestId, byte[] bytes) {
    }

    private static final class Pending {
        final byte[][] parts;
        int received;

        Pending(int count) {
            parts = new byte[count][];
        }
    }

    private Uploads() {
    }

    /** Takes one part. Returns the whole upload once its last part is in, otherwise null. */
    static synchronized Done accept(UUID player, Blobs.Part part) {
        if (part.count() <= 0 || part.count() > MAX_PARTS || part.index() < 0 || part.index() >= part.count()) {
            return null;
        }
        Map<Integer, Pending> mine = PENDING.computeIfAbsent(player, id -> new HashMap<>());
        Pending pending = mine.get(part.transferId());
        if (pending == null) {
            if (mine.size() >= MAX_PENDING) {
                // Something's gone wrong with earlier uploads; start over rather than pile up.
                mine.clear();
            }
            pending = new Pending(part.count());
            mine.put(part.transferId(), pending);
        }
        if (pending.parts.length != part.count() || pending.parts[part.index()] != null) {
            return null;
        }
        pending.parts[part.index()] = part.data();
        if (++pending.received < pending.parts.length) {
            return null;
        }
        mine.remove(part.transferId());
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] bytes : pending.parts) {
            out.writeBytes(bytes);
        }
        return new Done(part.kind(), part.requestId(), out.toByteArray());
    }

    static void clear() {
        PENDING.clear();
    }
}
