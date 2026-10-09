package com.finndog.justenoughstructures.client.screen;

import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.Thumbnails;
import com.finndog.justenoughstructures.client.render.SnapshotView;
import com.finndog.justenoughstructures.client.render.StructureViewport;
import com.mojang.blaze3d.pipeline.TextureTarget;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * Makes thumbnails for structures in view in the list, one at a time and only while the main preview
 * isn't busy, so a list of 400 modded structures fills in with pictures as you scroll.
 */
final class ThumbnailQueue {

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
            TextureTarget thumbnail = viewport.renderThumbnail(Thumbnails.renderSize());
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
            if (!Thumbnails.has(id) && !Thumbnails.failed(id)) {
                start(id);
                return;
            }
        }
    }

    private void start(ResourceLocation id) {
        waiting = true;
        current = id;
        // Laid out off the render thread, as the preview is.
        ClientRequests.picture(id)
                .thenApplyAsync(reply -> reply.result().succeeded() ? new Made(new SnapshotView(reply.result().snapshot()), false)
                        : new Made(null, reply.result().temporary()), Util.backgroundExecutor())
                .whenCompleteAsync((made, error) -> {
                    waiting = false;
                    Minecraft mc = Minecraft.getInstance();
                    SnapshotView view = made == null ? null : made.view();
                    if (view == null || mc.level == null) {
                        // Leaving the server cancels it or takes the level away, which says nothing about this structure.
                        if (mc.level != null && !cancelled(error)) {
                            Thumbnails.fail(id, made != null && made.temporary());
                        }
                        current = null;
                        return;
                    }
                    view.createRenderables(mc.level);
                    viewport = new StructureViewport();
                    viewport.setView(view);
                }, Minecraft.getInstance());
    }

    /** What came back: the structure ready to draw, or nothing and whether the server may manage it later. */
    private record Made(SnapshotView view, boolean temporary) {
    }

    private static boolean cancelled(Throwable error) {
        return error instanceof CancellationException
                || error instanceof CompletionException && error.getCause() instanceof CancellationException;
    }

    void close() {
        if (viewport != null) {
            viewport.close();
            viewport = null;
        }
    }
}
