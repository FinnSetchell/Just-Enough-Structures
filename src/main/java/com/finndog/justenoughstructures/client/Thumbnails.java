package com.finndog.justenoughstructures.client;

import com.finndog.justenoughstructures.Ids;
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
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
//? if >=26.1 {
/*import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTexture;
*///?} else {
import com.finndog.justenoughstructures.client.render.StructureViewport;
//?}

/**
 * Small pictures of structures for the list, Pack tools, the found-in popup and recipe viewers. Each
 * is drawn at twice its shown size and halved smoothly, so edges stay clean at any GUI scale. A copy
 * saved to disk is reused, so the list doesn't need the server to generate every structure again,
 * while the structure's definition, the server's fingerprint (mods, datapacks, pools and templates),
 * the resource packs and the shown size all match. Render thread only.
 */
public final class Thumbnails {
    /** Bump when thumbnails are drawn differently, so saved ones aren't reused. */
    private static final int VERSION = 2;
    private static final int MAX = 256;
    /** Largest shown size in GUI units, used by the found-in popup and recipe viewers. The list and Pack tools show 16. */
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
    /**
     * Structures a thumbnail couldn't be made for. They're tried again when the structure list next
     * arrives, after a /reload or a change in Pack tools, as what failed may work then.
     */
    private static final Set<ResourceLocation> FAILED = new HashSet<>();
    /**
     * Structures the server couldn't capture for now, for example while low on memory. The value is
     * {retry time, failure count}; the wait doubles each time, up to 160 s.
     */
    private static final Map<ResourceLocation, long[]> LATER = new HashMap<>();
    private static final long FIRST_WAIT_MILLIS = 5_000L;
    private static final long LONGEST_WAIT_MILLIS = 160_000L;
    /** Thumbnails drawn but still being copied back from the GPU, which takes a frame or so from 26.1. */
    private static final Set<ResourceLocation> PENDING = new HashSet<>();
    /** Incremented by {@link #clear()}, so a GPU copy that finishes after it is discarded. */
    private static int generation;
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

    /** The thumbnail's texture, read from disk if there's a saved one, or null if there's none. */
    private static DynamicTexture texture(ResourceLocation id) {
        DynamicTexture texture = CACHE.get(id);
        if (texture == null && load(id)) {
            texture = CACHE.get(id);
        }
        return texture;
    }

    public static boolean has(ResourceLocation id) {
        return PENDING.contains(id) || texture(id) != null;
    }

    /** Draws the structure's thumbnail {@code size} square. Returns false, drawing nothing, if there isn't one. */
    public static boolean draw(GuiGraphics g, ResourceLocation id, int x, int y, int size) {
        DynamicTexture texture = texture(id);
        if (texture == null) {
            return false;
        }
        //? if >=26.1 {
        /*// Linear filtering, since it's drawn a little smaller than it was made.
        g.blit(texture.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR), x, y, x + size, y + size, 0f, 1f, 0f, 1f);
        *///?} else {
        StructureViewport.drawTexture(g, texture.getId(), x, y, size, size);
        //?}
        return true;
    }

    /**
     * Stores a thumbnail just drawn, halved so it comes out smooth, and saves a copy to disk. The
     * render target is freed once the picture is read back from it.
     */
    public static void put(ResourceLocation id, TextureTarget target) {
        //? if >=26.1 {
        /*// The GPU returns the picture a frame or so later. Until then it counts as present, so it isn't drawn again.
        PENDING.add(id);
        int drawnIn = generation;
        GpuTexture source = target.getColorTexture();
        int width = target.width;
        int height = target.height;
        int pixelSize = source.getFormat().pixelSize();
        CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
        GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "Just Enough Structures thumbnail",
                GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST, (long) width * height * pixelSize);
        encoder.copyTextureToBuffer(source, buffer, 0L, () -> {
            NativeImage drawn = new NativeImage(width, height, false);
            try (GpuBuffer.MappedView read = encoder.mapBuffer(buffer, true, false)) {
                for (int y = 0; y < height; y++) {
                    for (int x = 0; x < width; x++) {
                        // The texture's rows go from the bottom up, and the picture's from the top down.
                        drawn.setPixelABGR(x, height - y - 1, read.data().getInt((x + y * width) * pixelSize));
                    }
                }
            }
            buffer.close();
            target.destroyBuffers();
            if (generation != drawnIn) {
                drawn.close();
                return;
            }
            PENDING.remove(id);
            keep(id, drawn);
        }, 0);
        *///?} else {
        NativeImage drawn = new NativeImage(target.width, target.height, false);
        RenderSystem.bindTexture(target.getColorTextureId());
        drawn.downloadTexture(0, false);
        target.destroyBuffers();
        // Freeing a render target leaves the window bound instead of the game's main target, and
        // everything the screen draws after that is lost for the frame, which makes the preview flash.
        Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        keep(id, drawn);
        //?}
    }

    private static void keep(ResourceLocation id, NativeImage drawn) {
        NativeImage small = halve(drawn);
        drawn.close();

        NativeImage copy = new NativeImage(small.getWidth(), small.getHeight(), false);
        copy.copyFrom(small);
        DynamicTexture texture = texture(small);
        DynamicTexture old = CACHE.put(id, texture);
        if (old != null && old != texture) {
            old.close();
        }
        NOT_SAVED.remove(id);
        LATER.remove(id);
        save(id, copy);
    }

    private static DynamicTexture texture(NativeImage image) {
        //? if >=26.1 {
        /*return new DynamicTexture(() -> "Just Enough Structures thumbnail", image);
        *///?} else {
        DynamicTexture texture = new DynamicTexture(image);
        // Linear filtering, since the list draws it a little smaller than it was made.
        texture.setFilter(true, false);
        return texture;
        //?}
    }

    /**
     * Shrinks the image to half its width and height, each pixel the average of the four it covers.
     * Colour is weighted by alpha, so a transparent background doesn't darken edges.
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
                        int p = pixel(image, Math.min(x * 2 + dx, image.getWidth() - 1), Math.min(y * 2 + dy, image.getHeight() - 1));
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
                setPixel(out, x, y, pixel);
            }
        }
        return out;
    }

    // Alpha is the top byte in either colour order, which is all halve() relies on.
    public static int pixel(NativeImage image, int x, int y) {
        //? if >=26.1 {
        /*return image.getPixel(x, y);
        *///?} else {
        return image.getPixelRGBA(x, y);
        //?}
    }

    private static void setPixel(NativeImage image, int x, int y, int pixel) {
        //? if >=26.1 {
        /*image.setPixel(x, y, pixel);
        *///?} else {
        image.setPixelRGBA(x, y, pixel);
        //?}
    }

    /**
     * Sets what each structure's saved thumbnail must match, when the structure list arrives. Without
     * the server's fingerprint, which it sends once it has computed it, nothing saved is used and
     * nothing new is saved, since there'd be no way to tell what a picture shows.
     */
    static void onCatalog(String fingerprint, List<StructureCatalog.Entry> entries) {
        Map<ResourceLocation, String> out = new HashMap<>();
        if (fingerprint != null) {
            String packs = String.join(",", Minecraft.getInstance().getResourcePackRepository().getSelectedIds());
            for (StructureCatalog.Entry entry : entries) {
                out.put(entry.id(), entry.id() + "|" + entry.definition() + "|" + fingerprint + "|" + packs);
            }
        }
        keys = out;
        NOT_SAVED.clear();
        FAILED.clear();
        LATER.clear();
    }

    /** Whether this structure's thumbnail failed and shouldn't be requested again yet. */
    public static boolean failed(ResourceLocation id) {
        long[] later = LATER.get(id);
        return FAILED.contains(id) || later != null && Util.getMillis() < later[0];
    }

    /**
     * Records a failed thumbnail. A temporary failure is retried after a growing wait; any other waits
     * for the next structure list.
     */
    public static void fail(ResourceLocation id, boolean temporary) {
        if (!temporary) {
            FAILED.add(id);
            return;
        }
        long[] later = LATER.computeIfAbsent(id, k -> new long[2]);
        long wait = Math.min(LONGEST_WAIT_MILLIS, FIRST_WAIT_MILLIS << Math.min(later[1], 10));
        later[0] = Util.getMillis() + wait;
        later[1]++;
    }

    public static void clear() {
        CACHE.values().forEach(DynamicTexture::close);
        CACHE.clear();
        PENDING.clear();
        generation++;
        NOT_SAVED.clear();
        FAILED.clear();
        LATER.clear();
        keys = Map.of();
    }

    /** The saved file's name for the structure's current state at the current shown size. */
    private static String fileKey(ResourceLocation id) {
        String base = keys.get(id);
        if (base == null) {
            return null;
        }
        int size = shownSize();
        if (size != savedSize) {
            // The GUI scale changed, so saved pictures are the wrong size. Look for them again.
            savedSize = size;
            NOT_SAVED.clear();
        }
        Hasher hasher = Hashing.murmur3_128().newHasher();
        hasher.putInt(VERSION).putInt(size).putString(base, StandardCharsets.UTF_8);
        return hasher.hash().toString();
    }

    /**
     * The folder for a structure's pictures, or null for an id that would reach outside the cache.
     * Structure ids come from the server, and saving a picture deletes the others in its folder.
     */
    private static Path folder(ResourceLocation id) {
        return Ids.fileSafe(id) ? JustEnoughStructures.cacheDir().resolve("thumbnails").resolve(id.getNamespace()).resolve(id.getPath()) : null;
    }

    private static boolean load(ResourceLocation id) {
        String key = fileKey(id);
        if (key == null || NOT_SAVED.contains(id)) {
            return false;
        }
        Path dir = folder(id);
        Path file = dir == null ? null : dir.resolve(key + ".png");
        if (file == null || !Files.exists(file)) {
            NOT_SAVED.add(id);
            return false;
        }
        try (InputStream in = Files.newInputStream(file)) {
            NativeImage image = NativeImage.read(in);
            //? if <26.1 {
            // Stored the right way up; textures drawn into render targets are upside down.
            image.flipY();
            //?}
            CACHE.put(id, texture(image));
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
        Path dir = folder(id);
        if (key == null || dir == null) {
            image.close();
            return;
        }
        //? if <26.1 {
        image.flipY();
        //?}
        Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(dir);
                // Keep only the picture for the structure's current state.
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
