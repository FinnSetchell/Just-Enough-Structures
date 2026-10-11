package com.finndog.justenoughstructures.gametest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import net.minecraft.resources.ResourceLocation;

/**
 * Stops the game in the middle of loading one saved piece, for the test of what else can go on
 * meanwhile. The game keeps its pieces locked for as long as a load takes.
 */
public final class HeldLoads {
    private static volatile Hold holding;

    private HeldLoads() {
    }

    /** One piece whose next load waits to be let go. */
    public static final class Hold {
        private final ResourceLocation piece;
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch letGo = new CountDownLatch(1);

        private Hold(ResourceLocation piece) {
            this.piece = piece;
        }

        /** Waits for the game to start loading the piece, and says whether it did. */
        public boolean started(long seconds) throws InterruptedException {
            return started.await(seconds, TimeUnit.SECONDS);
        }

        /** Lets the load carry on, and any after it go straight through. */
        public void letGo() {
            letGo.countDown();
            holding = null;
        }
    }

    public static Hold hold(ResourceLocation piece) {
        Hold hold = new Hold(piece);
        holding = hold;
        return hold;
    }

    /** Called as the game starts loading a piece. */
    public static void loading(ResourceLocation piece) {
        Hold hold = holding;
        if (hold == null || !hold.piece.equals(piece)) {
            return;
        }
        hold.started.countDown();
        try {
            // Never for good, whatever becomes of the test.
            hold.letGo.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
