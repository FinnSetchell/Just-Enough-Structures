package com.finndog.justenoughstructures.client.render;

import com.finndog.justenoughstructures.Threads;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
//? if >=1.21 {
/*import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
*///?}
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;
//? if >=26.1 {
/*import com.finndog.justenoughstructures.JustEnoughStructures;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import org.joml.Vector3f;
import org.joml.Vector4f;
*///?} else {
import com.mojang.blaze3d.vertex.VertexBuffer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
//?}
//? if >=26.2 {
/*import net.minecraft.client.renderer.BindGroupLayouts;
*///?}

/**
 * Blocks tinted in the preview while a row about them is hovered: every block of a kind, a group of
 * containers or some spawners. The group's outer faces are filled and its edges outlined, as
 * Litematica shows a schematic. The mesh is built once and kept on the GPU, so even thousands of
 * blocks cost little each frame. It shows faintly through blocks in front of it, so buried ones can
 * still be found, and clearly where it's in view.
 *
 * <p>The blocks are found and the mesh is built off the render thread, since for thousands of blocks
 * that takes longer than a frame, and the tint shows once it's ready, a frame or two later. When the
 * layer slider moves, the tint stays as it was until the one for the new cut is ready.
 */
public final class Highlight implements AutoCloseable {
    /**
     * More blocks than this aren't tinted. A kind of block that makes up most of a big structure
     * says little about where it is, and its faces would take tens of megabytes.
     */
    public static final int MAX_BLOCKS = 30_000;
    // Just outside the blocks' faces, so the tint doesn't flicker against them.
    private static final float OUT = 0.004f;
    private static final float[] FILL = {1f, 0.82f, 0.25f, 0.25f};
    private static final float[] EDGE = {1f, 0.92f, 0.4f, 1f};
    /** Opacity of the parts hidden behind other blocks, relative to the visible parts. */
    private static final float THROUGH = 0.35f;
    /** Builds run here, one at a time, as they share the buffers below. */
    private static final ExecutorService BUILDS = Threads.single("Just Enough Structures highlight");
    /** The build that's running, or finished and not yet taken in, or null. Only the render thread uses it. */
    private static Build building;
    /**
     * Shared, since 1.20.1 never frees a buffer builder's memory. They grow to the largest highlight
     * drawn. Only a build uses them, and what it built is uploaded or dropped before the next starts.
     */
    //? if >=1.21 {
    /*// From 1.21 the memory is a separate object, and a new builder wraps it for each build.
    private static ByteBufferBuilder fillBytes;
    private static ByteBufferBuilder edgeBytes;
    *///?} else {
    private static BufferBuilder fillBuilder;
    private static BufferBuilder edgeBuilder;
    //?}

    //? if >=26.1 {
    /*// The tint's alpha replaces the preview's where it's drawn, so the backdrop behind the
    // preview shows through what's tinted, like coloured glass.
    private static final BlendFunction GLASS = new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE_MINUS_SRC_ALPHA, SourceFactor.ONE, DestFactor.ZERO);
    // Faces and edges, drawn once faintly through anything in front of them, then again where visible.
    private static final RenderPipeline FACES_SEEN = pipeline("fill", VertexFormat.Mode.QUADS, true);
    private static final RenderPipeline FACES_THROUGH = pipeline("fill_through", VertexFormat.Mode.QUADS, false);
    private static final RenderPipeline EDGES_SEEN = pipeline("edges", VertexFormat.Mode.DEBUG_LINES, true);
    private static final RenderPipeline EDGES_THROUGH = pipeline("edges_through", VertexFormat.Mode.DEBUG_LINES, false);
    *///?}

    /** Finds the positions off the render thread, or null once they're known. */
    private Supplier<LongSet> find;
    private LongSet positions;
    /** False for mobs, which their markers light up instead: a block-sized box would hide them. */
    private final boolean tinted;
    //? if >=26.1 {
    /*private Mesh fill;
    private Mesh edges;
    *///?} else {
    private VertexBuffer fill;
    private VertexBuffer edges;
    //?}
    /** The slice the mesh on the GPU was built for, or -1 before the first is in. */
    private int builtForSlice = -1;
    private boolean empty = true;
    private boolean closed;

    /**
     * {@code positions} are {@link BlockPos#asLong} of positions in the structure. With {@code tinted}
     * false, nothing is drawn: the positions only light up markers.
     */
    public Highlight(LongSet positions, boolean tinted) {
        this.positions = positions;
        this.tinted = tinted;
    }

    private Highlight(Supplier<LongSet> find) {
        this.find = find;
        this.positions = LongSets.EMPTY_SET;
        this.tinted = true;
    }

    /**
     * A tint over the positions {@code find} gives, found off the render thread, as finding every
     * block of a kind means going through every block of the structure. Until they're found it has no
     * positions, and it never has any if there are none or more than {@link #MAX_BLOCKS}. {@code find}
     * must read nothing the render thread changes.
     */
    public static Highlight finding(Supplier<LongSet> find) {
        return new Highlight(find);
    }

    public boolean contains(BlockPos pos) {
        return positions.contains(pos.asLong());
    }

    /** The tinted positions, as {@link BlockPos#asLong}. Empty while they're still being found. */
    public LongSet positions() {
        return positions;
    }

    /** Whether the positions are known and the tint drawn is the one for {@code slice}. Test hook for the scripted scenarios. */
    public boolean ready(int slice) {
        return find == null && (!tinted || builtForSlice == slice);
    }

    //? if >=26.1 {
    /*void draw(GpuTextureView color, GpuTextureView depth, Matrix4f viewMatrix, int slice) {
        if (!tinted) {
            return;
        }
        update(slice);
        if (empty || fill == null) {
            return;
        }
        GpuBufferSlice through = RenderSystem.getDynamicUniforms()
                .writeTransform(viewMatrix, new Vector4f(1f, 1f, 1f, THROUGH), new Vector3f(), new Matrix4f());
        GpuBufferSlice inSight = RenderSystem.getDynamicUniforms()
                .writeTransform(viewMatrix, new Vector4f(1f, 1f, 1f, 1f), new Vector3f(), new Matrix4f());
        RenderSystem.AutoStorageIndexBuffer quads = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        GpuBuffer quadIndices = quads.getBuffer(fill.count());
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "Just Enough Structures highlight", color, OptionalInt.empty(), depth, OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            for (boolean seen : new boolean[]{false, true}) {
                pass.setUniform("DynamicTransforms", seen ? inSight : through);
                pass.setPipeline(seen ? FACES_SEEN : FACES_THROUGH);
                pass.setVertexBuffer(0, fill.vertices());
                pass.setIndexBuffer(quadIndices, quads.type());
                pass.drawIndexed(0, 0, fill.count(), 1);
                pass.setPipeline(seen ? EDGES_SEEN : EDGES_THROUGH);
                pass.setVertexBuffer(0, edges.vertices());
                pass.draw(0, edges.count());
            }
        }
    }
    *///?}

    //? if >=26.2 {
    /*private static RenderPipeline pipeline(String name, VertexFormat.Mode mode, boolean depthTest) {
        return RenderPipeline.builder()
                .withLocation(JustEnoughStructures.id("pipeline/highlight_" + name))
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                .withVertexShader("core/position_color")
                .withFragmentShader("core/position_color")
                .withColorTargetState(new ColorTargetState(GLASS))
                .withVertexBinding(0, DefaultVertexFormat.POSITION_COLOR)
                .withPrimitiveTopology(mode)
                // Only the sides facing the camera, so a block's far faces don't stack on its near ones.
                .withCull(true)
                // From 26.2 depth is reversed, with the nearest depth the largest.
                .withDepthStencilState(depthTest ? Optional.of(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false)) : Optional.empty())
                .build();
    }
    *///?} else if >=26.1 {
    /*private static RenderPipeline pipeline(String name, VertexFormat.Mode mode, boolean depthTest) {
        return RenderPipeline.builder()
                .withLocation(JustEnoughStructures.id("pipeline/highlight_" + name))
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withVertexShader("core/position_color")
                .withFragmentShader("core/position_color")
                .withColorTargetState(new ColorTargetState(GLASS))
                .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, mode)
                // Only the sides facing the camera, so a block's far faces don't stack on its near ones.
                .withCull(true)
                .withDepthStencilState(depthTest ? Optional.of(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false)) : Optional.empty())
                .build();
    }
    *///?}

    //? if >=26.1 {
    /*// A mesh on the GPU, and how many indices (for faces) or vertices (for edges) drawing it takes.
    private record Mesh(GpuBuffer vertices, int count) implements AutoCloseable {
        static Mesh upload(MeshData mesh) {
            GpuBuffer vertices = RenderSystem.getDevice()
                    .createBuffer(() -> "Just Enough Structures highlight", GpuBuffer.USAGE_VERTEX, mesh.vertexBuffer());
            return new Mesh(vertices, mesh.drawState().mode() == VertexFormat.Mode.QUADS ? mesh.drawState().indexCount() : mesh.drawState().vertexCount());
        }

        @Override
        public void close() {
            vertices.close();
        }
    }
    *///?} else {
    void draw(Matrix4f viewMatrix, Matrix4f projection, int slice) {
        if (!tinted) {
            return;
        }
        update(slice);
        ShaderInstance shader = GameRenderer.getPositionColorShader();
        if (empty || fill == null || shader == null) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // Only the sides facing the camera, so a block's far faces don't stack on its near ones.
        RenderSystem.enableCull();
        RenderSystem.depthMask(false);
        try {
            RenderSystem.disableDepthTest();
            RenderSystem.setShaderColor(1f, 1f, 1f, THROUGH);
            drawBoth(viewMatrix, projection, shader);
            RenderSystem.enableDepthTest();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            drawBoth(viewMatrix, projection, shader);
        } finally {
            VertexBuffer.unbind();
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    private void drawBoth(Matrix4f viewMatrix, Matrix4f projection, ShaderInstance shader) {
        fill.bind();
        fill.drawWithShader(viewMatrix, projection, shader);
        edges.bind();
        edges.drawWithShader(viewMatrix, projection, shader);
    }
    //?}

    /**
     * Takes in a finished build, and starts one when the positions aren't found yet or the mesh drawn
     * isn't for {@code slice}. Until it's in, the last mesh built is drawn.
     */
    private void update(int slice) {
        collect();
        if ((find != null || builtForSlice != slice) && building == null) {
            start(slice);
        }
    }

    private void start(int slice) {
        Supplier<LongSet> finder = find;
        LongSet known = positions;
        building = new Build(this, slice, CompletableFuture.supplyAsync(() -> {
            try {
                LongSet found = finder != null ? finder.get() : known;
                if (found.isEmpty() || found.size() > MAX_BLOCKS) {
                    return new Built(LongSets.EMPTY_SET, null, null);
                }
                return build(found, slice);
            } catch (Throwable e) {
                // The build buffers may have been left part way through a build, so new ones are made.
                spoilt();
                throw e;
            }
        }, BUILDS));
    }

    /**
     * Takes in the build that's running, if it has finished: uploads it for its highlight, or drops it
     * if that highlight has been closed since.
     */
    private static void collect() {
        if (building == null || !building.result().isDone()) {
            return;
        }
        Build done = building;
        building = null;
        Built built;
        try {
            built = done.result().join();
        } catch (CompletionException | CancellationException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            RenderFailures.rethrowIfFatal(cause);
            RenderFailures.failed("Building a highlight", "the hovered row", cause);
            // Nothing is tinted rather than trying again every frame.
            built = new Built(LongSets.EMPTY_SET, null, null);
        }
        if (done.owner().closed) {
            built.drop();
        } else {
            done.owner().take(done.slice(), built);
        }
    }

    /** Puts what a build made on the GPU, for {@code slice}. */
    private void take(int slice, Built built) {
        if (find != null) {
            find = null;
            positions = built.positions();
        }
        builtForSlice = slice;
        empty = built.fill() == null || built.edges() == null;
        if (empty) {
            built.drop();
            return;
        }
        //? if >=26.1 {
        /*try (MeshData fillRendered = built.fill(); MeshData edgeRendered = built.edges()) {
            closeMeshes();
            fill = Mesh.upload(fillRendered);
            edges = Mesh.upload(edgeRendered);
        }
        *///?} else {
        if (fill == null) {
            fill = new VertexBuffer(VertexBuffer.Usage.STATIC);
            edges = new VertexBuffer(VertexBuffer.Usage.STATIC);
        }
        // Uploading frees what was built for the next build.
        fill.bind();
        fill.upload(built.fill());
        edges.bind();
        edges.upload(built.edges());
        VertexBuffer.unbind();
        //?}
    }

    /** A build for {@code owner}, cut at {@code slice}. */
    private record Build(Highlight owner, int slice, CompletableFuture<Built> result) {
    }

    /** What a build found and made, not yet on the GPU, or no meshes when there's nothing to tint. */
    //? if >=1.21 {
    /*private record Built(LongSet positions, MeshData fill, MeshData edges) {
        /^* Frees what was built without uploading it. ^/
        void drop() {
            if (fill != null) {
                fill.close();
            }
            if (edges != null) {
                edges.close();
            }
        }
    }
    *///?} else {
    private record Built(LongSet positions, BufferBuilder.RenderedBuffer fill, BufferBuilder.RenderedBuffer edges) {
        /** Frees what was built without uploading it. */
        void drop() {
            if (fill != null) {
                fill.release();
            }
            if (edges != null) {
                edges.release();
            }
        }
    }
    //?}

    /** Forgets the build buffers after a build failed partway, so the next build makes new ones. */
    private static void spoilt() {
        //? if >=1.21 {
        /*if (fillBytes != null) {
            fillBytes.close();
            edgeBytes.close();
        }
        fillBytes = null;
        edgeBytes = null;
        *///?} else {
        fillBuilder = null;
        edgeBuilder = null;
        //?}
    }

    /**
     * Builds the mesh off the render thread: fills each face of a highlighted block below the slice
     * that doesn't touch another highlighted block, and outlines the edges where that surface turns a
     * corner or ends. Edges between two faces in the same plane are left out, so a wall reads as one
     * shape, not a grid.
     */
    private static Built build(LongSet positions, int slice) {
        //? if >=1.21 {
        /*if (fillBytes == null) {
            fillBytes = new ByteBufferBuilder(256 * 1024);
            edgeBytes = new ByteBufferBuilder(256 * 1024);
        }
        BufferBuilder fills = new BufferBuilder(fillBytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        BufferBuilder lines = new BufferBuilder(edgeBytes, VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        *///?} else {
        if (fillBuilder == null) {
            fillBuilder = new BufferBuilder(256 * 1024);
            edgeBuilder = new BufferBuilder(256 * 1024);
        }
        BufferBuilder fills = fillBuilder;
        BufferBuilder lines = edgeBuilder;
        fills.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        lines.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
        //?}
        float[] min = new float[3];
        float[] max = new float[3];
        int faces = 0;
        for (LongIterator it = positions.iterator(); it.hasNext(); ) {
            long packed = it.nextLong();
            int x = BlockPos.getX(packed);
            int y = BlockPos.getY(packed);
            int z = BlockPos.getZ(packed);
            if (y >= slice) {
                continue;
            }
            min[0] = x - OUT;
            min[1] = y - OUT;
            min[2] = z - OUT;
            max[0] = x + 1 + OUT;
            max[1] = y + 1 + OUT;
            max[2] = z + 1 + OUT;
            for (Direction face : Direction.values()) {
                if (has(positions, x + face.getStepX(), y + face.getStepY(), z + face.getStepZ(), slice)) {
                    continue;
                }
                faces++;
                fillFace(fills, face, min, max);
                for (Direction side : Direction.values()) {
                    if (side.getAxis() == face.getAxis()) {
                        continue;
                    }
                    int nx = x + side.getStepX(), ny = y + side.getStepY(), nz = z + side.getStepZ();
                    boolean flat = has(positions, nx, ny, nz, slice) && !has(positions, nx + face.getStepX(), ny + face.getStepY(), nz + face.getStepZ(), slice);
                    if (!flat) {
                        edge(lines, face, side, min, max);
                    }
                }
            }
        }
        //? if >=1.21 {
        /*Built built = new Built(positions, fills.build(), lines.build());
        *///?} else {
        Built built = new Built(positions, fills.endOrDiscardIfEmpty(), lines.endOrDiscardIfEmpty());
        //?}
        if (faces == 0 || built.fill() == null || built.edges() == null) {
            built.drop();
            return new Built(positions, null, null);
        }
        return built;
    }

    private static boolean has(LongSet positions, int x, int y, int z, int slice) {
        return y < slice && positions.contains(BlockPos.asLong(x, y, z));
    }

    /** Adds the box's face on one side, as a quad across the other two axes. */
    private static void fillFace(BufferBuilder builder, Direction face, float[] min, float[] max) {
        int axis = face.getAxis().ordinal();
        int a = (axis + 1) % 3;
        int b = (axis + 2) % 3;
        float[] p = new float[3];
        p[axis] = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? max[axis] : min[axis];
        float[][] corners = {{min[a], min[b]}, {max[a], min[b]}, {max[a], max[b]}, {min[a], max[b]}};
        // Anticlockwise as seen from outside, so the side facing out is the one drawn.
        boolean outwards = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        for (int i = 0; i < 4; i++) {
            float[] c = corners[outwards ? i : 3 - i];
            p[a] = c[0];
            p[b] = c[1];
            vertex(builder, p, FILL);
        }
    }

    /** Adds the edge where a face of the box meets its face towards {@code side}. */
    private static void edge(BufferBuilder builder, Direction face, Direction side, float[] min, float[] max) {
        int faceAxis = face.getAxis().ordinal();
        int sideAxis = side.getAxis().ordinal();
        int along = 3 - faceAxis - sideAxis;
        float[] p = new float[3];
        p[faceAxis] = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? max[faceAxis] : min[faceAxis];
        p[sideAxis] = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? max[sideAxis] : min[sideAxis];
        p[along] = min[along];
        vertex(builder, p, EDGE);
        p[along] = max[along];
        vertex(builder, p, EDGE);
    }

    private static void vertex(BufferBuilder builder, float[] p, float[] colour) {
        //? if >=1.21 {
        /*builder.addVertex(p[0], p[1], p[2]).setColor(colour[0], colour[1], colour[2], colour[3]);
        *///?} else {
        builder.vertex(p[0], p[1], p[2]).color(colour[0], colour[1], colour[2], colour[3]).endVertex();
        //?}
    }

    private void closeMeshes() {
        if (fill != null) {
            fill.close();
            edges.close();
            fill = null;
            edges = null;
        }
    }

    /**
     * Frees the mesh. A build still running for it is dropped when it finishes. That build is kept until
     * the next highlight takes it in, so this lets go of what the positions were to be found in, like a
     * structure's every block: the build has its own hold on that while it runs.
     */
    @Override
    public void close() {
        closed = true;
        find = null;
        closeMeshes();
        collect();
    }
}
