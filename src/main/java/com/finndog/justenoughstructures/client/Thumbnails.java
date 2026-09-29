package com.finndog.justenoughstructures.client;

import com.mojang.blaze3d.pipeline.TextureTarget;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;

/** Small pictures of structures already previewed this session, shown in the list. Render thread only. */
public final class Thumbnails {
    private static final int MAX = 256;
    private static final Map<ResourceLocation, TextureTarget> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ResourceLocation, TextureTarget> eldest) {
            if (size() > MAX) {
                eldest.getValue().destroyBuffers();
                return true;
            }
            return false;
        }
    };

    private Thumbnails() {
    }

    public static TextureTarget get(ResourceLocation id) {
        return CACHE.get(id);
    }

    public static boolean has(ResourceLocation id) {
        return CACHE.containsKey(id);
    }

    public static void put(ResourceLocation id, TextureTarget target) {
        TextureTarget old = CACHE.put(id, target);
        if (old != null && old != target) {
            old.destroyBuffers();
        }
    }

    public static void clear() {
        CACHE.values().forEach(TextureTarget::destroyBuffers);
        CACHE.clear();
    }
}
