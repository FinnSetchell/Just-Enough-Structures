package com.finndog.justenoughstructures.client.render;

import com.mojang.blaze3d.platform.Window;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
//? if >=1.21 {
/*import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
*///?}
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;
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
//? if forge {
/*import net.minecraft.client.resources.model.BakedModel;
import net.minecraftforge.client.model.data.ModelData;
*///?}

/**
 * GPU meshes for a snapshot, one set per Y layer so the layer slider can hide the top of the
 * structure without rebuilding everything. The layer the slider cuts through gets its own "cap"
 * mesh, built as if everything above it were air, so its top faces aren't missing.
 *
 * <p>Layers are built a few at a time from the render thread, within a time budget, because 1.20.1
 * buffer builders can't be freed and so can't be handed to other threads freely. A layer too big for
 * one frame's budget is built in pieces, a few rows at a time, over as many frames as it takes.
 */
public final class SnapshotMesh implements AutoCloseable {
    //? if >=1.21 {
    /*private static final Map<RenderType, ByteBufferBuilder> BUILDERS = new HashMap<>();
    *///?} else {
    private static final Map<RenderType, BufferBuilder> BUILDERS = new HashMap<>();
    //?}

    private final SnapshotView view;
    /** Each finished layer, in the pieces it was built in. */
    private final List<List<Piece>> layers = new ArrayList<>();
    /** The pieces so far of the layer being built, and the row it's up to. */
    private final List<Piece> current = new ArrayList<>();
    private int nextRow;
    private List<Piece> cap = List.of();
    private int capY = -1;
    private boolean closed;
    // Transparent faces have to be drawn back to front, so their order is redone as the camera moves.
    //? if >=1.21 {
    /*private final Map<VertexBuffer, MeshData.SortState> translucent = new HashMap<>();
    *///?} else {
    private final Map<VertexBuffer, BufferBuilder.SortState> translucent = new HashMap<>();
    //?}
    private final Vector3f sortedFrom = new Vector3f(Float.NaN, 0, 0);

    public SnapshotMesh(SnapshotView view) {
        this.view = view;
    }

    public boolean building() {
        return layers.size() < view.size().getY();
    }

    public float progress() {
        if (view.size().getY() == 0) {
            return 1f;
        }
        return (layers.size() + (float) nextRow / Math.max(1, view.size().getZ())) / view.size().getY();
    }

    /** Builds layers until {@code budgetNanos} is used up. Translucent faces are sorted from {@code eye}. */
    public void buildSome(long budgetNanos, Vector3f eye) {
        long deadline = System.nanoTime() + budgetNanos;
        int keepSlice = view.sliceY();
        view.setSliceY(view.size().getY());
        try {
            while (building() && System.nanoTime() < deadline) {
                Piece piece = tesselate(layers.size(), nextRow, deadline, eye);
                current.add(piece);
                nextRow = piece.lastRow();
                if (nextRow >= view.size().getZ()) {
                    layers.add(List.copyOf(current));
                    current.clear();
                    nextRow = 0;
                }
            }
        } finally {
            view.setSliceY(keepSlice);
        }
    }

    public void draw(Matrix4f viewMatrix, Matrix4f projection, Vector3f eye) {
        // Re-sort see-through faces once the eye has moved far enough to change their order. From
        // far away that takes a bigger move, which keeps a spinning preview of a big structure
        // from re-sorting every frame.
        float fromCentre = eye.distance(view.size().getX() / 2f, view.size().getY() / 2f, view.size().getZ() / 2f);
        float threshold = Math.max(1.5f, fromCentre * 0.04f);
        if (Float.isNaN(sortedFrom.x()) || sortedFrom.distanceSquared(eye) > threshold * threshold) {
            resort(eye);
        }
        int slice = view.sliceY();
        boolean sliced = slice < view.size().getY();
        if (sliced && capY != slice - 1) {
            cap.forEach(this::closePiece);
            cap = List.of(tesselate(slice - 1, 0, Long.MAX_VALUE, eye));
            capY = slice - 1;
        }
        int fullLayers = Math.min(layers.size(), sliced ? slice - 1 : slice);
        // The cap, when there is one, is the layer straight above the full ones.
        int drawn = fullLayers + (sliced && layers.size() >= slice - 1 ? 1 : 0);
        for (RenderType type : RenderType.chunkBufferLayers()) {
            if (!anyOf(type, fullLayers, drawn)) {
                continue;
            }
            type.setupRenderState();
            ShaderInstance shader = RenderSystem.getShader();
            if (shader != null) {
                // Set up once for every layer of this type, as vanilla does for chunks, rather than
                // once per layer, which is hundreds of times a frame for a tall structure.
                prepare(shader, viewMatrix, projection);
                if (type == RenderType.translucent()) {
                    // See-through layers furthest from the eye first, so nearer ones blend over them
                    // from below as well as above, and the same for the pieces of each layer.
                    farthestFirst(drawn, (int) Math.floor(eye.y()), y -> {
                        List<Piece> pieces = layer(y, fullLayers);
                        farthestFirst(pieces.size(), pieceAt(pieces, eye.z()), i -> drawBuffer(pieces.get(i).buffers().get(type)));
                    });
                } else {
                    for (int y = 0; y < drawn; y++) {
                        for (Piece piece : layer(y, fullLayers)) {
                            drawBuffer(piece.buffers().get(type));
                        }
                    }
                }
                shader.clear();
            }
            type.clearRenderState();
        }
        VertexBuffer.unbind();
    }

    private boolean anyOf(RenderType type, int fullLayers, int drawn) {
        for (int y = 0; y < drawn; y++) {
            for (Piece piece : layer(y, fullLayers)) {
                if (piece.buffers().containsKey(type)) {
                    return true;
                }
            }
        }
        return false;
    }

    private List<Piece> layer(int y, int fullLayers) {
        return y < fullLayers ? layers.get(y) : cap;
    }

    /** Which of a layer's pieces has the row {@code z} in it, or the nearest one. */
    private static int pieceAt(List<Piece> pieces, float z) {
        for (int i = 0; i < pieces.size(); i++) {
            if (z < pieces.get(i).lastRow()) {
                return i;
            }
        }
        return pieces.size() - 1;
    }

    /** Calls {@code draw} with 0 to {@code count - 1}, furthest from {@code nearest} first. */
    private static void farthestFirst(int count, int nearest, IntConsumer draw) {
        nearest = Math.max(0, Math.min(count - 1, nearest));
        int low = 0;
        int high = count - 1;
        while (low <= high) {
            // Whichever end is further goes next.
            if (nearest - low >= high - nearest) {
                draw.accept(low++);
            } else {
                draw.accept(high--);
            }
        }
    }

    /** What vanilla's VertexBuffer.drawWithShader sets before every draw, done once for a whole type. */
    private static void prepare(ShaderInstance shader, Matrix4f viewMatrix, Matrix4f projection) {
        for (int i = 0; i < 12; i++) {
            shader.setSampler("Sampler" + i, RenderSystem.getShaderTexture(i));
        }
        if (shader.MODEL_VIEW_MATRIX != null) {
            shader.MODEL_VIEW_MATRIX.set(viewMatrix);
        }
        if (shader.PROJECTION_MATRIX != null) {
            shader.PROJECTION_MATRIX.set(projection);
        }
        //? if <1.21 {
        if (shader.INVERSE_VIEW_ROTATION_MATRIX != null) {
            shader.INVERSE_VIEW_ROTATION_MATRIX.set(RenderSystem.getInverseViewRotationMatrix());
        }
        //?}
        if (shader.COLOR_MODULATOR != null) {
            shader.COLOR_MODULATOR.set(RenderSystem.getShaderColor());
        }
        if (shader.GLINT_ALPHA != null) {
            shader.GLINT_ALPHA.set(RenderSystem.getShaderGlintAlpha());
        }
        if (shader.FOG_START != null) {
            shader.FOG_START.set(RenderSystem.getShaderFogStart());
        }
        if (shader.FOG_END != null) {
            shader.FOG_END.set(RenderSystem.getShaderFogEnd());
        }
        if (shader.FOG_COLOR != null) {
            shader.FOG_COLOR.set(RenderSystem.getShaderFogColor());
        }
        if (shader.FOG_SHAPE != null) {
            shader.FOG_SHAPE.set(RenderSystem.getShaderFogShape().getIndex());
        }
        if (shader.TEXTURE_MATRIX != null) {
            shader.TEXTURE_MATRIX.set(RenderSystem.getTextureMatrix());
        }
        if (shader.GAME_TIME != null) {
            shader.GAME_TIME.set(RenderSystem.getShaderGameTime());
        }
        if (shader.SCREEN_SIZE != null) {
            Window window = Minecraft.getInstance().getWindow();
            shader.SCREEN_SIZE.set((float) window.getWidth(), (float) window.getHeight());
        }
        if (shader.CHUNK_OFFSET != null) {
            shader.CHUNK_OFFSET.set(0f, 0f, 0f);
        }
        RenderSystem.setupShaderLights(shader);
        shader.apply();
    }

    private static void drawBuffer(VertexBuffer buffer) {
        if (buffer != null) {
            buffer.bind();
            buffer.draw();
        }
    }

    /**
     * Builds layer {@code y} from row {@code fromRow}, a row at a time until the end of the layer or
     * {@code deadline}, whichever comes first, but always at least one row.
     */
    private Piece tesselate(int y, int fromRow, long deadline, Vector3f eye) {
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        Map<RenderType, BufferBuilder> started = new HashMap<>();
        PoseStack pose = new PoseStack();
        RandomSource random = RandomSource.create();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        int z = fromRow;
        while (z < view.size().getZ()) {
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
                    //? if forge {
                    /*// Forge models can say which layers they draw in themselves, often in their json,
                    // which the vanilla lookup doesn't know about. This is how Forge builds chunks.
                    BakedModel model = dispatcher.getBlockModel(state);
                    random.setSeed(state.getSeed(pos));
                    for (RenderType type : model.getRenderTypes(state, random, ModelData.EMPTY)) {
                        BufferBuilder builder = begin(started, type);
                        pose.pushPose();
                        pose.translate(x, y, z);
                        dispatcher.renderBatched(state, pos, view, pose, builder, true, random, ModelData.EMPTY, type);
                        pose.popPose();
                    }
                    *///?} else {
                    BufferBuilder builder = begin(started, ItemBlockRenderTypes.getChunkRenderType(state));
                    pose.pushPose();
                    pose.translate(x, y, z);
                    dispatcher.renderBatched(state, pos, view, pose, builder, true, random);
                    pose.popPose();
                    //?}
                }
            }
            z++;
            if (deadline != Long.MAX_VALUE && System.nanoTime() >= deadline) {
                break;
            }
        }

        Map<RenderType, VertexBuffer> out = new HashMap<>();
        for (Map.Entry<RenderType, BufferBuilder> e : started.entrySet()) {
            //? if >=1.21 {
            /*MeshData mesh = e.getValue().build();
            if (mesh == null) {
                continue;
            }
            // Sorted into the same buffer the mesh was built in, as vanilla sorts chunk sections.
            MeshData.SortState sortState = e.getKey() == RenderType.translucent()
                    ? mesh.sortQuads(BUILDERS.get(RenderType.translucent()), VertexSorting.byDistance(eye.x(), eye.y(), eye.z())) : null;
            VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
            buffer.bind();
            buffer.upload(mesh);
            *///?} else {
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
            //?}
            out.put(e.getKey(), buffer);
            if (sortState != null) {
                translucent.put(buffer, sortState);
            }
        }
        VertexBuffer.unbind();
        return new Piece(z, out);
    }

    /** Re-sorts every transparent layer from {@code eye}, the way vanilla does for chunk sections. */
    private void resort(Vector3f eye) {
        sortedFrom.set(eye);
        if (translucent.isEmpty()) {
            return;
        }
        //? if >=1.21 {
        /*ByteBufferBuilder sorting = BUILDERS.computeIfAbsent(RenderType.translucent(), k -> new ByteBufferBuilder(256 * 1024));
        for (Map.Entry<VertexBuffer, MeshData.SortState> e : translucent.entrySet()) {
            ByteBufferBuilder.Result indices = e.getValue().buildSortedIndexBuffer(sorting, VertexSorting.byDistance(eye.x(), eye.y(), eye.z()));
            if (indices != null) {
                e.getKey().bind();
                e.getKey().uploadIndexBuffer(indices);
            }
        }
        *///?} else {
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
        //?}
        VertexBuffer.unbind();
    }

    private void closeBuffer(VertexBuffer buffer) {
        translucent.remove(buffer);
        buffer.close();
    }

    private void closePiece(Piece piece) {
        piece.buffers().values().forEach(this::closeBuffer);
    }

    /** Part of a layer, built in one go: its rows up to {@code lastRow} (not included). */
    private record Piece(int lastRow, Map<RenderType, VertexBuffer> buffers) {
    }

    private static BufferBuilder begin(Map<RenderType, BufferBuilder> started, RenderType type) {
        return started.computeIfAbsent(type, t -> {
            //? if >=1.21 {
            /*return new BufferBuilder(BUILDERS.computeIfAbsent(t, k -> new ByteBufferBuilder(256 * 1024)), VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            *///?} else {
            BufferBuilder builder = BUILDERS.computeIfAbsent(t, k -> new BufferBuilder(256 * 1024));
            builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            return builder;
            //?}
        });
    }

    @Override
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        layers.forEach(layer -> layer.forEach(this::closePiece));
        layers.clear();
        current.forEach(this::closePiece);
        current.clear();
        cap.forEach(this::closePiece);
        cap = List.of();
        translucent.clear();
    }
}
