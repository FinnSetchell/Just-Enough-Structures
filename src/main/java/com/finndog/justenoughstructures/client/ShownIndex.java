package com.finndog.justenoughstructures.client;

import com.finndog.justenoughstructures.loot.LootIndex;

/**
 * The loot index a recipe viewer shows, and the one it's about to show, so it can tell whether an
 * index that arrives is new to it. Each viewer keeps one. The server sends its index again after
 * each check that it's still valid, twice after a /reload, and every viewer hears of each one, but
 * only one that differs from what it shows is worth taking: JEI keeps every recipe it's given, even
 * once hidden, and EMI loads all over again. What a viewer shows is its own, as it loses it when it
 * restarts, as JEI and EMI do on a /reload, so this isn't decided once for all of them.
 *
 * <p>Indexes are compared by what they hold. Safe from any thread. Uses no game classes, so game
 * tests can run it on a server.
 */
public final class ShownIndex {
    private volatile LootIndex shown;
    private volatile LootIndex coming;

    /** Whether {@code index} is one to take: there is one, and it's neither the one shown nor the one on its way. */
    public boolean isNew(LootIndex index) {
        return index != null && !index.equals(shown) && !index.equals(coming);
    }

    /** The viewer now shows {@code index}, or no index for null, as after it restarts. */
    public void showing(LootIndex index) {
        shown = index;
    }

    /**
     * The viewer is about to show {@code index}, as EMI is once asked to reload for it, or null once
     * nothing is on its way, as when that load starts.
     */
    public void coming(LootIndex index) {
        coming = index;
    }

    /** The index the viewer shows, or null. */
    public LootIndex shown() {
        return shown;
    }
}
