package com.finndog.justenoughstructures.server;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * How much each player may ask of the server, and how much they may be sent. The browser asks for
 * far less than this, so only a modified client meets these limits: one asking for loot odds by the
 * thousand, or for the same big answers over and over without reading them, which would otherwise
 * take up the server's time or fill its memory with answers waiting to go out.
 */
public final class RequestLimits {
    /** Requests a player can make in a burst, and how many more they get each second. */
    private static final double REQUESTS = 200;
    private static final double REQUESTS_PER_SECOND = 40;
    /** Bytes of big answers, like the structure list and previews, a player can be sent in a burst, and how many more each second. */
    private static final double BYTES = 32 << 20;
    private static final double BYTES_PER_SECOND = 4 << 20;

    private static final Map<UUID, Bucket> REQUESTED = new ConcurrentHashMap<>();
    private static final Map<UUID, Bucket> SENT = new ConcurrentHashMap<>();

    private RequestLimits() {
    }

    /** Whether a player may make a request that costs this much now. If so, it's taken from what they have. */
    public static boolean request(UUID player, double cost) {
        return REQUESTED.computeIfAbsent(player, id -> new Bucket(REQUESTS, REQUESTS_PER_SECOND)).take(cost);
    }

    /**
     * Whether a player may be sent a big answer of this many bytes now. If so, it's taken from what
     * they have. One bigger than the whole burst can still be sent once they've had a rest.
     */
    public static boolean send(UUID player, long bytes) {
        return SENT.computeIfAbsent(player, id -> new Bucket(BYTES, BYTES_PER_SECOND)).take(bytes);
    }

    /** When a player leaves. */
    public static void forget(UUID player) {
        REQUESTED.remove(player);
        SENT.remove(player);
    }

    /** When the server stops. */
    public static void clear() {
        REQUESTED.clear();
        SENT.clear();
    }

    /** An allowance that builds back up at a steady rate, to a most. */
    private static final class Bucket {
        private final double most;
        private final double perSecond;
        private double left;
        private long last = System.nanoTime();

        Bucket(double most, double perSecond) {
            this.most = most;
            this.perSecond = perSecond;
            this.left = most;
        }

        synchronized boolean take(double cost) {
            long now = System.nanoTime();
            left = Math.min(most, left + (now - last) / 1e9 * perSecond);
            last = now;
            // More than the most there can be needs it all, and leaves the allowance owing.
            if (left < Math.min(cost, most)) {
                return false;
            }
            left -= cost;
            return true;
        }
    }
}
