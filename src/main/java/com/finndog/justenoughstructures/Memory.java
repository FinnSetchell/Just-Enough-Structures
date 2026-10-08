package com.finndog.justenoughstructures;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;

/** How much memory the game has to spare. */
public final class Memory {
    private Memory() {
    }

    /**
     * Whether too little memory is free to start a capture, as a big structure can need hundreds of
     * megabytes while it generates. Judged by what was still in use after the last garbage
     * collection, so garbage waiting to be collected doesn't count against it.
     */
    public static boolean low() {
        try {
            long max = Runtime.getRuntime().maxMemory();
            if (max == Long.MAX_VALUE) {
                return false;
            }
            long live = 0;
            for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
                if (pool.getType() != MemoryType.HEAP) {
                    continue;
                }
                MemoryUsage afterGc = pool.getCollectionUsage();
                live += afterGc != null ? afterGc.getUsed() : pool.getUsage().getUsed();
            }
            return max - live < Math.max(384L << 20, max / 5);
        } catch (RuntimeException e) {
            return false;
        }
    }
}
