package elocindev.item_obliterator.fabric_quilt.config;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * A stand-in for Item Obliterator's config, with the real one's public fields that say what it
 * removes (see util.Utils). Nothing is removed until a test lists it. Read from other threads, as the
 * loot index is built on its own.
 */
public class ConfigEntries {
    public List<String> blacklisted_items = new CopyOnWriteArrayList<>();
    public List<String> blacklisted_nbt = new CopyOnWriteArrayList<>();
    public boolean use_hashmap_optimizations;
}
