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
    /**
     * Structures a thumbnail couldn't be made for. They're tried again when the structure list next
     * arrives, after a /reload or a change in Pack tools, as what failed may work then.
     */
    private static final Set<ResourceLocation> FAILED = new HashSet<>();
    /** Thumbnails drawn but still being copied back from the GPU, which takes a frame or so from 26.1. */
    private static final Set<ResourceLocation> PENDING = new HashSet<>();
    /** Goes up on {@link #clear()}, so a copy that finishes after it isn't kept. */
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
        /*// Smooth rather than blocky, as it's drawn a little smaller than it was made.
        g.blit(texture.getTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR), x, y, x + size, y + size, 0f, 1f, 0f, 1f);
        *///?} else {
        StructureViewport.drawTexture(g, texture.getId(), x, y, size, size);
        //?}
        return true;
    }

    /**
     * Keeps a thumbnail just drawn, at half the size it was drawn at so it comes out smooth, and
     * saves a copy to disk. The render target is freed once the picture is read back from it.
     */
    public static void put(ResourceLocation id, TextureTarget target) {
        //? if >=26.1 {
        /*// The GPU hands the picture back a frame or so later. Until then it counts as there, so it isn't drawn again.
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
        // everything the screen drew after that was lost for the frame, which made the preview flash.
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
        save(id, copy);
    }

    private static DynamicTexture texture(NativeImage image) {
        //? if >=26.1 {
        /*return new DynamicTexture(() -> "Just Enough Structures thumbnail", image);
        *///?} else {
        DynamicTexture texture = new DynamicTexture(image);
        // Smooth rather than blocky when the list draws it a little smaller than it was made.
        texture.setFilter(true, false);
        return texture;
        //?}
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

    // Alpha is the top byte whichever way round the colours are, which is all halve() needs to know.
    private static int pixel(NativeImage image, int x, int y) {
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
        FAILED.clear();
    }

    /** Whether a thumbnail couldn't be made for this structure, so isn't worth asking for again yet. */
    public static boolean failed(ResourceLocation id) {
        return FAILED.contains(id);
    }

    public static void fail(ResourceLocation id) {
        FAILED.add(id);
    }

    public static void clear() {
        CACHE.values().forEach(DynamicTexture::close);
        CACHE.clear();
        PENDING.clear();
        generation++;
        NOT_SAVED.clear();
        FAILED.clear();
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
        if (key == null) {
            image.close();
            return;
        }
        //? if <26.1 {
        image.flipY();
        //?}
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
