package com.finndog.justenoughstructures.client.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.joml.Matrix4f;

/**
 * Blocks tinted in the preview while a row about them is hovered: every block of a kind, a group of
 * containers or some spawners. The group's outer faces are filled in and its edges outlined, the way
 * Litematica shows a schematic, worked out once and kept on the GPU so even thousands of blocks cost
 * little each frame. They show faintly through whatever is in front of them, so buried ones can
 * still be found, and clearly where they're in sight.
 */
public final class Highlight implements AutoCloseable {
    /**
     * More blocks than this aren't tinted. A kind of block that makes up most of a big structure
     * says little about where it is, and its faces would take tens of megabytes.
     */
    public static final int MAX_BLOCKS = 30_000;
    // Just off the blocks' own faces, so they don't flicker against them.
    private static final float OUT = 0.004f;
    private static final float[] FILL = {1f, 0.82f, 0.25f, 0.25f};
    private static final float[] EDGE = {1f, 0.92f, 0.4f, 1f};
    /** How strongly it all shows through what's in front of it, against how it looks in sight. */
    private static final float THROUGH = 0.35f;
    /** Shared, as 1.20.1 never frees a buffer builder's memory. They grow to the biggest highlight drawn. */
    private static BufferBuilder fillBuilder;
    private static BufferBuilder edgeBuilder;

    private final LongSet positions;
    /** False for mobs, which their markers light up instead: a block-sized box would hide them. */
    private final boolean tinted;
    private VertexBuffer fill;
    private VertexBuffer edges;
    private int builtForSlice = -1;
    private boolean empty;

    /** {@code positions} are {@link BlockPos#asLong} of positions in the structure. */
    public Highlight(LongSet positions) {
        this(positions, true);
    }

    /** With {@code tinted} false, nothing is drawn: the positions only light up markers. */
    public Highlight(LongSet positions, boolean tinted) {
        this.positions = positions;
        this.tinted = tinted;
    }

    public boolean contains(BlockPos pos) {
        return positions.contains(pos.asLong());
    }

    /** The tinted positions, as {@link BlockPos#asLong}. */
    public LongSet positions() {
        return positions;
    }

    void draw(Matrix4f viewMatrix, Matrix4f projection, int slice) {
        if (!tinted) {
            return;
        }
        if (builtForSlice != slice) {
            build(slice);
        }
        ShaderInstance shader = GameRenderer.getPositionColorShader();
        if (empty || shader == null) {
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

    /**
     * Fills each face of a block below the slice that doesn't touch another of them, and outlines
     * the edges where that surface turns a corner or ends. Edges between two faces lying flat in
     * the same plane are left out, so a wall of them reads as one shape rather than a grid.
     */
    private void build(int slice) {
        builtForSlice = slice;
        if (fillBuilder == null) {
            fillBuilder = new BufferBuilder(256 * 1024);
            edgeBuilder = new BufferBuilder(256 * 1024);
        }
        fillBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        edgeBuilder.begin(VertexFormat.Mode.DEBUG_LINES, DefaultVertexFormat.POSITION_COLOR);
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
                if (has(x + face.getStepX(), y + face.getStepY(), z + face.getStepZ(), slice)) {
                    continue;
                }
                faces++;
                fillFace(face, min, max);
                for (Direction side : Direction.values()) {
                    if (side.getAxis() == face.getAxis()) {
                        continue;
                    }
                    int nx = x + side.getStepX(), ny = y + side.getStepY(), nz = z + side.getStepZ();
                    boolean flat = has(nx, ny, nz, slice) && !has(nx + face.getStepX(), ny + face.getStepY(), nz + face.getStepZ(), slice);
                    if (!flat) {
                        edge(face, side, min, max);
                    }
                }
            }
        }
        BufferBuilder.RenderedBuffer fillRendered = fillBuilder.endOrDiscardIfEmpty();
        BufferBuilder.RenderedBuffer edgeRendered = edgeBuilder.endOrDiscardIfEmpty();
        empty = faces == 0 || fillRendered == null || edgeRendered == null;
        if (empty) {
            return;
        }
        if (fill == null) {
            fill = new VertexBuffer(VertexBuffer.Usage.STATIC);
            edges = new VertexBuffer(VertexBuffer.Usage.STATIC);
        }
        fill.bind();
        fill.upload(fillRendered);
        edges.bind();
        edges.upload(edgeRendered);
        VertexBuffer.unbind();
    }

    private boolean has(int x, int y, int z, int slice) {
        return y < slice && positions.contains(BlockPos.asLong(x, y, z));
    }

    /** The face of the box on one side, as a quad across the other two axes. */
    private static void fillFace(Direction face, float[] min, float[] max) {
        int axis = face.getAxis().ordinal();
        int a = (axis + 1) % 3;
        int b = (axis + 2) % 3;
        float[] p = new float[3];
        p[axis] = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? max[axis] : min[axis];
        float[][] corners = {{min[a], min[b]}, {max[a], min[b]}, {max[a], max[b]}, {min[a], max[b]}};
        // Round anticlockwise as seen from outside, so it's the side facing out that's drawn.
        boolean outwards = face.getAxisDirection() == Direction.AxisDirection.POSITIVE;
        for (int i = 0; i < 4; i++) {
            float[] c = corners[outwards ? i : 3 - i];
            p[a] = c[0];
            p[b] = c[1];
            fillBuilder.vertex(p[0], p[1], p[2]).color(FILL[0], FILL[1], FILL[2], FILL[3]).endVertex();
        }
    }

    /** The edge where a face of the box meets its side towards {@code side}. */
    private static void edge(Direction face, Direction side, float[] min, float[] max) {
        int faceAxis = face.getAxis().ordinal();
        int sideAxis = side.getAxis().ordinal();
        int along = 3 - faceAxis - sideAxis;
        float[] p = new float[3];
        p[faceAxis] = face.getAxisDirection() == Direction.AxisDirection.POSITIVE ? max[faceAxis] : min[faceAxis];
        p[sideAxis] = side.getAxisDirection() == Direction.AxisDirection.POSITIVE ? max[sideAxis] : min[sideAxis];
        p[along] = min[along];
        edgeBuilder.vertex(p[0], p[1], p[2]).color(EDGE[0], EDGE[1], EDGE[2], EDGE[3]).endVertex();
        p[along] = max[along];
        edgeBuilder.vertex(p[0], p[1], p[2]).color(EDGE[0], EDGE[1], EDGE[2], EDGE[3]).endVertex();
    }

    @Override
    public void close() {
        if (fill != null) {
            fill.close();
            edges.close();
            fill = null;
            edges = null;
        }
    }
}
