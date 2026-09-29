package com.finndog.justenoughstructures.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * GPU meshes for a snapshot, one set per Y layer so the layer slider can hide the top of the
 * structure without rebuilding everything. The layer the slider cuts through gets its own "cap"
 * mesh, built as if everything above it were air, so its top faces aren't missing.
 *
 * <p>Layers are built a few at a time from the render thread, within a time budget, because 1.20.1
 * buffer builders can't be freed and so can't be handed to other threads freely.
 */
public final class SnapshotMesh implements AutoCloseable {
    private static final Map<RenderType, BufferBuilder> BUILDERS = new HashMap<>();

    private final SnapshotView view;
    private final List<Map<RenderType, VertexBuffer>> layers = new ArrayList<>();
    private Map<RenderType, VertexBuffer> cap = Map.of();
    private int capY = -1;
    private boolean closed;
    // Transparent faces have to be drawn back to front, so their order is redone as the camera moves.
    private final Map<VertexBuffer, BufferBuilder.SortState> translucent = new HashMap<>();
    private final Vector3f sortedFrom = new Vector3f(Float.NaN, 0, 0);

    public SnapshotMesh(SnapshotView view) {
        this.view = view;
    }

    public boolean building() {
        return layers.size() < view.size().getY();
    }

    public float progress() {
        return view.size().getY() == 0 ? 1f : (float) layers.size() / view.size().getY();
    }

    /** Builds layers until {@code budgetNanos} is used up. Translucent faces are sorted from {@code eye}. */
    public void buildSome(long budgetNanos, Vector3f eye) {
        long deadline = System.nanoTime() + budgetNanos;
        int keepSlice = view.sliceY();
        view.setSliceY(view.size().getY());
        try {
            while (building() && System.nanoTime() < deadline) {
                layers.add(tesselate(layers.size(), eye));
            }
        } finally {
            view.setSliceY(keepSlice);
        }
    }

    public void draw(Matrix4f viewMatrix, Matrix4f projection, Vector3f eye) {
        if (Float.isNaN(sortedFrom.x()) || sortedFrom.distanceSquared(eye) > 2.25f) {
            resort(eye);
        }
        int slice = view.sliceY();
        boolean sliced = slice < view.size().getY();
        if (sliced && capY != slice - 1) {
            cap.values().forEach(this::closeBuffer);
            cap = tesselate(slice - 1, eye);
            capY = slice - 1;
        }
        int fullLayers = Math.min(layers.size(), sliced ? slice - 1 : slice);
        for (RenderType type : RenderType.chunkBufferLayers()) {
            type.setupRenderState();
            ShaderInstance shader = RenderSystem.getShader();
            if (shader != null) {
                if (shader.CHUNK_OFFSET != null) {
                    shader.CHUNK_OFFSET.set(0f, 0f, 0f);
                }
                for (int y = 0; y < fullLayers; y++) {
                    drawBuffer(layers.get(y).get(type), viewMatrix, projection, shader);
                }
                if (sliced && layers.size() >= slice - 1) {
                    drawBuffer(cap.get(type), viewMatrix, projection, shader);
                }
            }
            type.clearRenderState();
        }
        VertexBuffer.unbind();
    }

    private static void drawBuffer(VertexBuffer buffer, Matrix4f viewMatrix, Matrix4f projection, ShaderInstance shader) {
        if (buffer != null) {
            buffer.bind();
            buffer.drawWithShader(viewMatrix, projection, shader);
        }
    }

    private Map<RenderType, VertexBuffer> tesselate(int y, Vector3f eye) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        Map<RenderType, BufferBuilder> started = new HashMap<>();
        PoseStack pose = new PoseStack();
        RandomSource random = RandomSource.create();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int z = 0; z < view.size().getZ(); z++) {
            for (int x = 0; x < view.size().getX(); x++) {
                pos.set(x, y, z);
                BlockState state = view.getBlockState(pos);
                if (state.isAir()) {
                    continue;
                }
                FluidState fluid = state.getFluidState();
                if (!fluid.isEmpty()) {
                    BufferBuilder builder = begin(started, ItemBlockRenderTypes.getRenderLayer(fluid));
                    dispatcher.renderLiquid(pos, view, new OffsetConsumer(builder).at(x, y, z), state, fluid);
                }
                if (state.getRenderShape() == RenderShape.MODEL) {
                    BufferBuilder builder = begin(started, ItemBlockRenderTypes.getChunkRenderType(state));
                    pose.pushPose();
                    pose.translate(x, y, z);
                    dispatcher.renderBatched(state, pos, view, pose, builder, true, random);
                    pose.popPose();
                }
            }
        }

        Map<RenderType, VertexBuffer> out = new HashMap<>();
        for (Map.Entry<RenderType, BufferBuilder> e : started.entrySet()) {
            BufferBuilder builder = e.getValue();
            BufferBuilder.SortState sortState = null;
            if (e.getKey() == RenderType.translucent()) {
                builder.setQuadSorting(VertexSorting.byDistance(eye.x(), eye.y(), eye.z()));
                sortState = builder.getSortState();
            }
            BufferBuilder.RenderedBuffer rendered = builder.endOrDiscardIfEmpty();
            if (rendered == null) {
                continue;
            }
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(rendered);
            out.put(e.getKey(), buffer);
            if (sortState != null) {
                translucent.put(buffer, sortState);
            }
        }
        VertexBuffer.unbind();
        return out;
    }

    /** Re-sorts every transparent layer from {@code eye}, the way vanilla does for chunk sections. */
    private void resort(Vector3f eye) {
        sortedFrom.set(eye);
        if (translucent.isEmpty()) {
            return;
        }
        BufferBuilder builder = BUILDERS.computeIfAbsent(RenderType.translucent(), k -> new BufferBuilder(256 * 1024));
        for (Map.Entry<VertexBuffer, BufferBuilder.SortState> e : translucent.entrySet()) {
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            builder.restoreSortState(e.getValue());
            builder.setQuadSorting(VertexSorting.byDistance(eye.x(), eye.y(), eye.z()));
            e.setValue(builder.getSortState());
            BufferBuilder.RenderedBuffer rendered = builder.end();
            e.getKey().bind();
            e.getKey().upload(rendered);
        }
        VertexBuffer.unbind();
    }

    private void closeBuffer(VertexBuffer buffer) {
        translucent.remove(buffer);
        buffer.close();
    }

    private static BufferBuilder begin(Map<RenderType, BufferBuilder> started, RenderType type) {
        return started.computeIfAbsent(type, t -> {
            BufferBuilder builder = BUILDERS.computeIfAbsent(t, k -> new BufferBuilder(256 * 1024));
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            return builder;
        });
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        layers.forEach(layer -> layer.values().forEach(this::closeBuffer));
        layers.clear();
        cap.values().forEach(this::closeBuffer);
        cap = Map.of();
        translucent.clear();
    }
}
