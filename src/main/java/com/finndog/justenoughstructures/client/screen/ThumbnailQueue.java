package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.capture.StructureCapture;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.Thumbnails;
import com.finndog.justenoughstructures.client.render.SnapshotView;
import com.finndog.justenoughstructures.client.render.StructureViewport;
import com.mojang.blaze3d.pipeline.TextureTarget;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Makes thumbnails for structures in view in the list, one at a time and only while the main preview
 * isn't busy, so a list of 400 modded structures fills in with pictures as you scroll.
 */
final class ThumbnailQueue {
    private static final Set<ResourceLocation> FAILED = new HashSet<>();

    private ResourceLocation current;
    private StructureViewport viewport;
    private boolean waiting;

    /** Called every frame on the render thread. */
    void tick(List<ResourceLocation> visible, boolean busy) {
        if (viewport != null) {
            if (viewport.meshing()) {
                viewport.buildSome(3_000_000L);
                return;
            }
            TextureTarget thumbnail = viewport.renderThumbnail(64);
            if (thumbnail != null) {
                Thumbnails.put(current, thumbnail);
            }
            viewport.close();
            viewport = null;
            current = null;
            return;
        }
        if (waiting || busy) {
            return;
        }
        for (ResourceLocation id : visible) {
            if (!Thumbnails.has(id) && !FAILED.contains(id)) {
                start(id);
                return;
            }
        }
    }

    private void start(ResourceLocation id) {
        waiting = true;
        current = id;
        ClientRequests.capture(id, StructureCapture.defaultSeed(id)).thenAccept(reply -> {
            waiting = false;
            Minecraft mc = Minecraft.getInstance();
            if (!reply.result().succeeded() || mc.level == null) {
                FAILED.add(id);
                current = null;
                return;
            }
            SnapshotView view = new SnapshotView(reply.result().snapshot());
            view.createRenderables(mc.level);
            viewport = new StructureViewport();
            viewport.setView(view);
        });
    }

    void close() {
        if (viewport != null) {
            viewport.close();
            viewport = null;
        }
    }
}
