package com.finndog.justenoughstructures.client;

import com.finndog.justenoughstructures.JesLog;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * Small pictures of structures for the list, found-in rows and JEI. Each one is drawn at twice the
 * size it's shown at and shrunk smoothly, so edges come out clean at any GUI scale, and a copy is
 * saved to disk so next time the list has its pictures straight away instead of asking the server
 * to generate every structure again. A saved picture is only used while the structure's definition,
 * its mod's version, the resource packs and the size it's shown at are the same. Render thread only.
 */
public final class Thumbnails {
    /** Goes up when thumbnails are drawn differently, so old ones aren't used. */
    private static final int VERSION = 2;
    private static final int MAX = 256;
    /** The biggest a thumbnail is shown, in GUI units: the found-in rows and JEI. The list shows 16. */
    private static final int SHOWN_AT = 18;

    private static final Map<ResourceLocation, DynamicTexture> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ResourceLocation, DynamicTexture> eldest) {
            if (size() > MAX) {
                eldest.getValue().close();
                return true;
            }
            return false;
        }
    };
    private static final Set<ResourceLocation> NOT_SAVED = new HashSet<>();
    private static Map<ResourceLocation, String> keys = Map.of();
    private static int savedSize;

    private Thumbnails() {
    }

    /** How big to draw a thumbnail: twice the pixels it takes on screen at the current GUI scale. */
    public static int renderSize() {
        return shownSize() * 2;
    }

    private static int shownSize() {
        return Math.max(16, (int) Math.ceil(SHOWN_AT * Minecraft.getInstance().getWindow().getGuiScale()));
    }

    /** The thumbnail's texture, read from disk if there's a saved one, or -1 if there's none. */
    public static int textureId(ResourceLocation id) {
        DynamicTexture texture = CACHE.get(id);
        if (texture == null && load(id)) {
            texture = CACHE.get(id);
        }
        return texture == null ? -1 : texture.getId();
    }

    public static boolean has(ResourceLocation id) {
        return textureId(id) >= 0;
    }

    /**
     * Keeps a thumbnail just drawn, at half the size it was drawn at so it comes out smooth, and
     * saves a copy to disk. The render target is freed straight away.
     */
    public static void put(ResourceLocation id, TextureTarget target) {
        NativeImage drawn = new NativeImage(target.width, target.height, false);
        RenderSystem.bindTexture(target.getColorTextureId());
        drawn.downloadTexture(0, false);
        target.destroyBuffers();
        // Freeing a render target leaves the window bound instead of the game's main target, and
        // everything the screen drew after that was lost for the frame, which made the preview flash.
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        NativeImage small = halve(drawn);
        drawn.close();

        NativeImage copy = new NativeImage(small.getWidth(), small.getHeight(), false);
        copy.copyFrom(small);
        DynamicTexture texture = new DynamicTexture(small);
        // Smooth rather than blocky when the list draws it a little smaller than it was made.
        texture.setFilter(true, false);
        DynamicTexture old = CACHE.put(id, texture);
        if (old != null && old != texture) {
            old.close();
        }
        NOT_SAVED.remove(id);
        save(id, copy);
    }

    /**
     * Shrinks the image to half its width and height, each pixel the average of the four it covers.
     * Colour is weighted by how solid each pixel is, so see-through background doesn't darken edges.
     */
    private static NativeImage halve(NativeImage image) {
        int w = Math.max(1, image.getWidth() / 2);
        int h = Math.max(1, image.getHeight() / 2);
        NativeImage out = new NativeImage(w, h, false);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                long a = 0, c0 = 0, c1 = 0, c2 = 0;
                for (int dy = 0; dy < 2; dy++) {
                    for (int dx = 0; dx < 2; dx++) {
                        int p = image.getPixelRGBA(Math.min(x * 2 + dx, image.getWidth() - 1), Math.min(y * 2 + dy, image.getHeight() - 1));
                        int alpha = p >>> 24;
                        a += alpha;
                        c0 += (long) (p & 0xFF) * alpha;
                        c1 += (long) ((p >> 8) & 0xFF) * alpha;
                        c2 += (long) ((p >> 16) & 0xFF) * alpha;
                    }
                }
                int pixel = 0;
                if (a > 0) {
                    pixel = (int) (a / 4) << 24 | (int) (c2 / a) << 16 | (int) (c1 / a) << 8 | (int) (c0 / a);
                }
                out.setPixelRGBA(x, y, pixel);
            }
        }
        return out;
    }

    /** Works out what each structure's saved thumbnail must match, once the structure list arrives. */
    static void onCatalog(List<StructureCatalog.Entry> entries) {
        Map<String, String> mods = JustEnoughStructures.modVersions();
        String packs = String.join(",", Minecraft.getInstance().getResourcePackRepository().getSelectedIds());
        Map<ResourceLocation, String> out = new HashMap<>();
        for (StructureCatalog.Entry entry : entries) {
            out.put(entry.id(), entry.id() + "|" + entry.definition() + "|" + mods.get(entry.id().getNamespace()) + "|" + packs);
        }
        keys = out;
        NOT_SAVED.clear();
    }

    public static void clear() {
        CACHE.values().forEach(DynamicTexture::close);
        CACHE.clear();
        NOT_SAVED.clear();
        keys = Map.of();
    }

    /** The saved file's name for the structure as it is now, at the size thumbnails are shown at now. */
    private static String fileKey(ResourceLocation id) {
        String base = keys.get(id);
        if (base == null) {
            return null;
        }
        int size = shownSize();
        if (size != savedSize) {
            // The GUI scale changed, so what's saved is the wrong size and has to be looked for again.
            savedSize = size;
            NOT_SAVED.clear();
        }
        Hasher hasher = Hashing.murmur3_128().newHasher();
        hasher.putInt(VERSION).putInt(size).putString(base, StandardCharsets.UTF_8);
        return hasher.hash().toString();
    }

    private static Path folder(ResourceLocation id) {
        return JustEnoughStructures.cacheDir().resolve("thumbnails").resolve(id.getNamespace()).resolve(id.getPath());
    }

    private static boolean load(ResourceLocation id) {
        String key = fileKey(id);
        if (key == null || NOT_SAVED.contains(id)) {
            return false;
        }
        Path file = folder(id).resolve(key + ".png");
        if (!Files.exists(file)) {
            NOT_SAVED.add(id);
            return false;
        }
        try (InputStream in = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(in);
            // Stored the right way up; textures drawn into render targets are upside down.
            image.flipY();
            DynamicTexture texture = new DynamicTexture(image);
            texture.setFilter(true, false);
            CACHE.put(id, texture);
            return true;
        } catch (IOException | RuntimeException e) {
            JesLog.debug("Couldn't read the saved thumbnail {}", file, e);
            NOT_SAVED.add(id);
            return false;
        }
    }

    /** Writes the picture to disk off the render thread, then frees it. */
    private static void save(ResourceLocation id, NativeImage image) {
        String key = fileKey(id);
        if (key == null) {
            image.close();
            return;
        }
        image.flipY();
        Path dir = folder(id);
        Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(dir);
                // Only the picture for the structure as it is now is worth keeping.
                try (Stream<Path> old = Files.list(dir)) {
                    for (Path file : old.filter(p -> p.toString().endsWith(".png")).toList()) {
                        Files.deleteIfExists(file);
                    }
                }
                image.writeToFile(dir.resolve(key + ".png"));
            } catch (IOException | RuntimeException e) {
                JesLog.debug("Couldn't save the thumbnail for {}", id, e);
            }
        });
    }
}
