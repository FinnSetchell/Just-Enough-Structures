package com.finndog.justenoughstructures.client;

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
 * Small pictures of structures for the list, found-in rows and JEI. Each one drawn is also saved to
 * disk, so next time the list has its pictures straight away instead of asking the server to
 * generate every structure again. A saved picture is only used while the structure's definition, its
 * mod's version and the resource packs are the same as when it was drawn. Render thread only.
 */
public final class Thumbnails {
    /** Goes up when thumbnails are drawn differently, so old ones aren't used. */
    private static final int VERSION = 1;
    private static final int MAX = 256;

    /** A thumbnail in memory: a render target drawn this session, or a texture read from disk. */
    private record Loaded(int textureId, Runnable release) {
    }

    private static final Map<ResourceLocation, Loaded> CACHE = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<ResourceLocation, Loaded> eldest) {
            if (size() > MAX) {
                eldest.getValue().release().run();
                return true;
            }
            return false;
        }
    };
    private static final Set<ResourceLocation> NOT_SAVED = new HashSet<>();
    private static Map<ResourceLocation, String> keys = Map.of();

    private Thumbnails() {
    }

    /** The thumbnail's texture, read from disk if there's a saved one, or -1 if there's none. */
    public static int textureId(ResourceLocation id) {
        Loaded loaded = CACHE.get(id);
        if (loaded == null && load(id)) {
            loaded = CACHE.get(id);
        }
        return loaded == null ? -1 : loaded.textureId();
    }

    public static boolean has(ResourceLocation id) {
        return textureId(id) >= 0;
    }

    /** Keeps a thumbnail just drawn and saves a copy to disk. */
    public static void put(ResourceLocation id, TextureTarget target) {
        Loaded old = CACHE.put(id, new Loaded(target.getColorTextureId(), target::destroyBuffers));
        if (old != null && old.textureId() != target.getColorTextureId()) {
            old.release().run();
        }
        save(id, target);
    }

    /** Works out what each structure's saved thumbnail must match, once the structure list arrives. */
    static void onCatalog(List<StructureCatalog.Entry> entries) {
        Map<String, String> mods = JustEnoughStructures.modVersions();
        String packs = String.join(",", Minecraft.getInstance().getResourcePackRepository().getSelectedIds());
        Map<ResourceLocation, String> out = new HashMap<>();
        for (StructureCatalog.Entry entry : entries) {
            Hasher hasher = Hashing.murmur3_128().newHasher();
            hasher.putInt(VERSION)
                    .putString(entry.id().toString(), StandardCharsets.UTF_8)
                    .putString(String.valueOf(entry.definition()), StandardCharsets.UTF_8)
                    .putString(String.valueOf(mods.get(entry.id().getNamespace())), StandardCharsets.UTF_8)
                    .putString(packs, StandardCharsets.UTF_8);
            out.put(entry.id(), hasher.hash().toString());
        }
        keys = out;
        NOT_SAVED.clear();
    }

    public static void clear() {
        CACHE.values().forEach(loaded -> loaded.release().run());
        CACHE.clear();
        NOT_SAVED.clear();
        keys = Map.of();
    }

    private static Path folder(ResourceLocation id) {
        return JustEnoughStructures.cacheDir().resolve("thumbnails").resolve(id.getNamespace()).resolve(id.getPath());
    }

    private static boolean load(ResourceLocation id) {
        String key = keys.get(id);
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
            CACHE.put(id, new Loaded(texture.getId(), texture::close));
            return true;
        } catch (IOException | RuntimeException e) {
            JustEnoughStructures.LOGGER.warn("Couldn't read the saved thumbnail {}", file, e);
            NOT_SAVED.add(id);
            return false;
        }
    }

    private static void save(ResourceLocation id, TextureTarget target) {
        String key = keys.get(id);
        if (key == null) {
            return;
        }
        NativeImage image = new NativeImage(target.width, target.height, false);
        RenderSystem.bindTexture(target.getColorTextureId());
        image.downloadTexture(0, false);
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
                JustEnoughStructures.LOGGER.warn("Couldn't save the thumbnail for {}", id, e);
            }
        });
    }
}
