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
 * while the structure's definition and the server's fingerprint (mods, datapacks, pools and
 * templates) match. One is kept for each shown size and set of resource packs, so going back to a GUI
 * scale or packs used before finds its pictures; a change to the structure itself drops them all.
 * Render thread only.
 */
public final class Thumbnails {
    /** Bump when thumbnails are drawn differently, so saved ones aren't reused. */
    private static final int VERSION = 2;
    private static final int MAX = 256;
    /** Largest shown size in GUI units, used by the found-in popup and recipe viewers. The list and Pack tools show 16. */
    private static final int SHOWN_AT = 18;

    /**
     * Pictures in memory made for a GUI scale or resource packs other than the current ones. They're
     * still shown until a picture for the current ones is read or drawn, but don't count as there.
     */
    private static final Set<ResourceLocation> STALE = new HashSet<>();
    private static final Map<ResourceLocation, DynamicTexture> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ResourceLocation, DynamicTexture> eldest) {
            if (size() > MAX) {
                retire(eldest.getValue());
                STALE.remove(eldest.getKey());
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
    /** Incremented whenever the pictures in memory go out of date, so a GPU copy that finishes after that is discarded. */
    private static int generation;
    private static Map<ResourceLocation, String> keys = Map.of();
    /** The shown size and resource packs the pictures in memory were made with. */
    private static int lookSize;
    private static Object lookPacks;
    private static String lookPackIds = "";

    private Thumbnails() {
    }

    /** How big to draw a thumbnail: twice the pixels it takes on screen at the current GUI scale. */
    public static int renderSize() {
        return shownSize() * 2;
    }

    private static int shownSize() {
        return Math.max(16, (int) Math.ceil(SHOWN_AT * Minecraft.getInstance().getWindow().getGuiScale()));
    }

    /**
     * The thumbnail's texture for the current GUI scale and resource packs, read from disk if there's
     * a saved one, or null if there's none.
     */
    private static DynamicTexture texture(ResourceLocation id) {
        checkLook();
        DynamicTexture texture = CACHE.get(id);
        if ((texture == null || STALE.contains(id)) && load(id)) {
            return CACHE.get(id);
        }
        return STALE.contains(id) ? null : texture;
    }

    /** Whether the structure has a thumbnail for the current GUI scale and resource packs, or one on its way. */
    public static boolean has(ResourceLocation id) {
        checkLook();
        return PENDING.contains(id) || texture(id) != null;
    }

    /**
     * Marks the pictures in memory out of date when the GUI scale or the resource packs have changed
     * since they were made, so each is read from disk or drawn again for the new ones. The selected
     * packs list is a new object after every reload, so its ids are only compared then. Not while packs
     * are still loading, when a picture drawn could have the old textures.
     */
    private static void checkLook() {
        Minecraft mc = Minecraft.getInstance();
        int size = shownSize();
        Object packs = mc.getResourcePackRepository().getSelectedPacks();
        if (size == lookSize && packs == lookPacks || mc.getOverlay() != null) {
            return;
        }
        String packIds = String.join(",", mc.getResourcePackRepository().getSelectedIds());
        boolean changed = size != lookSize || !packIds.equals(lookPackIds);
        lookSize = size;
        lookPacks = packs;
        lookPackIds = packIds;
        if (changed) {
            STALE.addAll(CACHE.keySet());
            PENDING.clear();
            generation++;
            NOT_SAVED.clear();
        }
    }

    /**
     * Draws the structure's thumbnail {@code size} square, or the one from before a GUI scale or
     * resource pack change until it's replaced. Returns false, drawing nothing, if there isn't one.
     */
    public static boolean draw(GuiGraphics g, ResourceLocation id, int x, int y, int size) {
        DynamicTexture texture = texture(id);
        if (texture == null) {
            texture = CACHE.get(id);
        }
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
        checkLook();
        if (small.getWidth() != lookSize) {
            // Drawn for a GUI scale that has changed since. It'll be drawn again at the new one.
            small.close();
            return;
        }

        NativeImage copy = new NativeImage(small.getWidth(), small.getHeight(), false);
        copy.copyFrom(small);
        DynamicTexture texture = texture(small);
        DynamicTexture old = CACHE.put(id, texture);
        if (old != null && old != texture) {
            retire(old);
        }
        STALE.remove(id);
        NOT_SAVED.remove(id);
        LATER.remove(id);
        save(id, copy);
    }

    /**
     * Frees a picture that's been replaced, which may already be queued to draw this frame. From 26.1
     * the screen is drawn at the end of the frame and a closed picture can't be drawn, so it's freed
     * once the frame is done.
     */
    private static void retire(DynamicTexture texture) {
        //? if >=26.1 {
        /*RenderSystem.queueFencedTask(texture::close);
        *///?} else {
        texture.close();
        //?}
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
            for (StructureCatalog.Entry entry : entries) {
                out.put(entry.id(), entry.id() + "|" + entry.definition() + "|" + fingerprint);
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
        STALE.clear();
        PENDING.clear();
        generation++;
        NOT_SAVED.clear();
        FAILED.clear();
        LATER.clear();
        keys = Map.of();
    }

    /**
     * The start of the saved files' names for the structure's current state, or null without one.
     * Pictures of the structure from before it changed don't start with it.
     */
    private static String statePrefix(ResourceLocation id) {
        String base = keys.get(id);
        if (base == null) {
            return null;
        }
        Hasher hasher = Hashing.murmur3_128().newHasher();
        hasher.putInt(VERSION).putString(base, StandardCharsets.UTF_8);
        return hasher.hash() + "-";
    }

    /** The saved file's name for the structure's current state at the current shown size and resource packs, or null. */
    private static String fileName(ResourceLocation id) {
        String prefix = statePrefix(id);
        if (prefix == null) {
            return null;
        }
        Hasher hasher = Hashing.murmur3_32_fixed().newHasher();
        hasher.putInt(lookSize).putString(lookPackIds, StandardCharsets.UTF_8);
        return prefix + hasher.hash() + ".png";
    }

    /**
     * The folder for a structure's pictures, or null for an id that would reach outside the cache.
     * Structure ids come from the server, and saving a picture deletes others in its folder.
     */
    private static Path folder(ResourceLocation id) {
        return Ids.fileSafe(id) ? JustEnoughStructures.cacheDir().resolve("thumbnails").resolve(id.getNamespace()).resolve(id.getPath()) : null;
    }

    private static boolean load(ResourceLocation id) {
        String name = fileName(id);
        if (name == null || NOT_SAVED.contains(id)) {
            return false;
        }
        Path dir = folder(id);
        Path file = dir == null ? null : dir.resolve(name);
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
            DynamicTexture old = CACHE.put(id, texture(image));
            if (old != null) {
                retire(old);
            }
            STALE.remove(id);
            return true;
        } catch (IOException | RuntimeException e) {
            JesLog.debug("Couldn't read the saved thumbnail {}", file, e);
            NOT_SAVED.add(id);
            return false;
        }
    }

    /** The width in pixels of the structure's picture in memory, or 0 with none there. Test hook for the scripted scenarios. */
    public static int widthInMemory(ResourceLocation id) {
        DynamicTexture texture = CACHE.get(id);
        NativeImage pixels = texture == null ? null : texture.getPixels();
        return pixels == null ? 0 : pixels.getWidth();
    }

    /** The structure's picture in memory, or null, to tell one picture from the next. Test hook for the scripted scenarios. */
    public static Object inMemory(ResourceLocation id) {
        return CACHE.get(id);
    }

    /** The structures with a picture in memory, out of date or not. Test hook for the scripted scenarios. */
    public static Set<ResourceLocation> inMemory() {
        return Set.copyOf(CACHE.keySet());
    }

    /** How wide a picture is at the current GUI scale. Test hook for the scripted scenarios. */
    public static int widthNow() {
        return shownSize();
    }

    /** The folder the structure's pictures are saved in. Test hook for the scripted scenarios. */
    public static Path savedIn(ResourceLocation id) {
        return folder(id);
    }

    /** Writes the picture to disk off the render thread, then frees it. */
    private static void save(ResourceLocation id, NativeImage image) {
        String prefix = statePrefix(id);
        String name = fileName(id);
        Path dir = folder(id);
        if (name == null || dir == null) {
            image.close();
            return;
        }
        //? if <26.1 {
        image.flipY();
        //?}
        Util.ioPool().execute(() -> {
            try (image) {
                Files.createDirectories(dir);
                // Keep the pictures for the structure's current state, at every GUI scale and with
                // every set of resource packs, and none from before it changed.
                try (Stream<Path> old = Files.list(dir)) {
                    for (Path file : old.filter(p -> p.toString().endsWith(".png") && !p.getFileName().toString().startsWith(prefix)).toList()) {
                        Files.deleteIfExists(file);
                    }
                }
                image.writeToFile(dir.resolve(name));
            } catch (IOException | RuntimeException e) {
                JesLog.debug("Couldn't save the thumbnail for {}", id, e);
            }
        });
    }
}
