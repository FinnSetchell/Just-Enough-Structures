package com.finndog.justenoughstructures.client.render;

import com.finndog.justenoughstructures.JesLog;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.Lighting;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
//? if <26.2 {
import net.minecraft.client.renderer.MultiBufferSource;
//?}
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
//? if >=1.21 {
/*import org.joml.Matrix4fStack;
*///?}
import org.joml.Vector3f;
import org.joml.Vector4f;
//? if >=26.1 {
/*import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.textures.GpuTextureView;
import java.nio.ByteBuffer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.SubmitNodeStorage;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.feature.FeatureRenderDispatcher;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Quaternionf;
import org.lwjgl.system.MemoryStack;
*///?} else {
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
//?}
//? if >=26.2 {
/*import com.mojang.blaze3d.GpuFormat;
*///?} else if >=26.1 {
/*import net.minecraft.client.renderer.ShapeRenderer;
*///?}
//? if >=26.3 {
/*import com.mojang.renderpearl.api.commands.RenderPass;
import java.util.OptionalDouble;
*///?}

/**
 * Draws a {@link SnapshotView} with an orbit camera into its own render target, then blits that
 * into the screen. Also turns mouse positions into the block or entity under the cursor.
 */
public final class StructureViewport implements AutoCloseable {
    private static final float FOV = 50f;
    /**
     * How far out from the middle of the view a structure may reach when it's fitted, and from how
     * many angles round it that's checked: every 15 degrees, so it stays in view as it spins.
     */
    private static final float FIT_EDGE = 0.9f;
    private static final int FIT_ANGLES = 24;
    /** How see-through the ground is under the structure. Round it, it fades out to nothing. */
    private static final float GROUND_ALPHA = 0.35f;
    /** The least and most time a frame spends building the mesh, while there's some to build. */
    private static final long MIN_BUILD_NANOS = 6_000_000L;
    private static final long MAX_BUILD_NANOS = 25_000_000L;
    /** How long a frame may spend drawing block entities and entities it hasn't drawn before. */
    private static final long FIRST_DRAWS_NANOS = 4_000_000L;

    private final Minecraft minecraft = Minecraft.getInstance();
    private SnapshotView view;
    private SnapshotMesh mesh;
    private TextureTarget target;
    /** Block entities and entities in this view whose renderer threw. Skipped from then on, as it would throw every frame. */
    private final Set<Object> failed = Collections.newSetFromMap(new IdentityHashMap<>());
    /** How many of the view's block entities, then its entities, have been drawn at least once. */
    private int firstDrawn;
    /** When the last frame was drawn, and how much of it went on building the mesh. */
    private long lastRender;
    private long lastBuild;

    private float yaw;
    private float pitch;
    private float distance;
    private final Vector3f focus = new Vector3f();
    /** Where the camera is gliding to, or null, and when it set off and last moved. */
    private Camera glide;
    private long glideStarted;
    private long glidedAt;
    private float homeDistance;

    private int x, y, width, height;
    private int groundY = -1;
    private final Matrix4f viewMatrix = new Matrix4f();
    private final Matrix4f projection = new Matrix4f();
    private final Vector3f eye = new Vector3f();
    //? if >=26.1 {
    /*// The light the game would give these positions in the real world is somewhere else entirely,
    // so everything is lit as the blocks are: fully.
    private static final int FULL_BRIGHT = 15728880;
    private static final VoxelShape OUTLINE = Shapes.create(-0.002, -0.002, -0.002, 1.002, 1.002, 1.002);
    // Fog that never starts, so a big structure seen from afar isn't lost in it.
    private static GpuBuffer noFog;
    // Made when first drawn, and again after close(): the browser closes its preview whenever another
    // screen opens over it, and draws it again on coming back.
    private ProjectionMatrixBuffer projectionBuffer;
    *///?}
    //? if >=26.2 {
    /*// What the preview's chests, mobs, ground and outlines queue up to be drawn.
    private SubmitNodeStorage nodes = new SubmitNodeStorage();
    *///?}

    public void setView(SnapshotView newView) {
        if (mesh != null) {
            mesh.close();
        }
        this.view = newView;
        this.mesh = newView == null ? null : new SnapshotMesh(newView);
        failed.clear();
        firstDrawn = 0;
        resetCamera();
    }

    /** Draws a see-through plane where the ground was, or nothing when {@code localY} is negative. */
    public void setGround(int localY) {
        groundY = localY;
    }

    /** Takes the ground's corners in turn, four to a quad, each with how see-through it is there. */
    @FunctionalInterface
    private interface GroundCorner {
        void at(float x, float z, float alpha);
    }

    /**
     * The ground as quads: solid under the structure, then fading out to nothing across a margin
     * round it, so where the edge of the preview cuts it off doesn't show. Only the solid part is
     * kept in view when the camera fits the structure.
     */
    private void groundQuads(GroundCorner corner) {
        float sx = view.size().getX(), sz = view.size().getZ();
        float m = Math.max(2f, Math.min(sx, sz) * 0.15f);
        float a = GROUND_ALPHA;
        float[][] quads = {
                {0, 0, a, 0, sz, a, sx, sz, a, sx, 0, a},
                {-m, -m, 0, -m, sz + m, 0, 0, sz, a, 0, 0, a},
                {sx, 0, a, sx, sz, a, sx + m, sz + m, 0, sx + m, -m, 0},
                {-m, -m, 0, 0, 0, a, sx, 0, a, sx + m, -m, 0},
                {0, sz, a, -m, sz + m, 0, sx + m, sz + m, 0, sx, sz, a}};
        for (float[] quad : quads) {
            for (int i = 0; i < quad.length; i += 3) {
                corner.at(quad[i], quad[i + 1], quad[i + 2]);
            }
        }
    }

    public SnapshotView view() {
        return view;
    }

    public boolean meshing() {
        return mesh != null && mesh.building();
    }

    /** Builds more of the mesh without drawing, for previews made off screen. */
    public void buildSome(long nanos) {
        if (mesh != null && mesh.building()) {
            mesh.buildSome(nanos, eye);
        }
    }

    public float meshProgress() {
        return mesh == null ? 0f : mesh.progress();
    }

    /** Where the camera is looking from, to put it back later. */
    public record Camera(float yaw, float pitch, float distance, float focusX, float focusY, float focusZ) {
    }

    public Camera camera() {
        return new Camera(yaw, pitch, distance, focus.x, focus.y, focus.z);
    }

    /**
     * Whether a solid block below {@code slice} stands between where {@code camera} sees from and the
     * point it looks at, leaving out the block at {@code target}, which is what's being looked at: a
     * shulker in an End City room, seen from outside, is behind the roof.
     */
    public boolean blocked(Camera camera, BlockPos target, int slice) {
        if (view == null) {
            return false;
        }
        Vector3f eye = new Matrix4f()
                .translate(0, 0, -camera.distance())
                .rotateX((float) Math.toRadians(camera.pitch()))
                .rotateY((float) Math.toRadians(camera.yaw()))
                .translate(-camera.focusX(), -camera.focusY(), -camera.focusZ())
                .invert().transformPosition(new Vector3f());
        Vec3 from = new Vec3(eye.x(), eye.y(), eye.z());
        Vec3 to = new Vec3(camera.focusX(), camera.focusY(), camera.focusZ());
        Boolean hit = BlockGetter.traverseBlocks(from, to, (Object) null, (ctx, pos) -> {
            if (pos.getY() >= slice || pos.equals(target)) {
                return null;
            }
            BlockState state = view.rawState(pos.getX(), pos.getY(), pos.getZ());
            //? if >=1.21.2 {
            /*return state.isSolidRender() ? Boolean.TRUE : null;
            *///?} else {
            return state.isSolidRender(view, pos) ? Boolean.TRUE : null;
            //?}
        }, ctx -> null);
        return hit != null;
    }

    /** Moves the camera smoothly, over a fraction of a second, to where {@code to} has it. */
    public void glideTo(Camera to) {
        glide = to;
        glideStarted = glidedAt = System.nanoTime();
    }

    /** Puts the camera back where {@link #camera()} found it, instead of fitting the structure to the view. */
    public void setCamera(Camera camera) {
        glide = null;
        yaw = camera.yaw();
        pitch = camera.pitch();
        distance = camera.distance();
        focus.set(camera.focusX(), camera.focusY(), camera.focusZ());
        needsFit = false;
    }

    public void resetCamera() {
        glide = null;
        yaw = 225f;
        pitch = 30f;
        if (view == null) {
            return;
        }
        float sx = view.size().getX(), sy = view.size().getY(), sz = view.size().getZ();
        focus.set(sx / 2f, sy / 2f, sz / 2f);
        float radius = (float) Math.sqrt(sx * sx + sy * sy + sz * sz) / 2f;
        homeDistance = Math.max(6f, radius / (float) Math.sin(Math.toRadians(FOV / 2f)));
        distance = homeDistance;
        needsFit = true;
    }

    private boolean needsFit;

    /** Zooms to fit again at the current angle, for when the preview changes size. */
    public void refit() {
        if (view != null) {
            needsFit = true;
        }
    }

    /**
     * Pulls the camera in as close as it can while the structure stays in view from every side it
     * spins through, so flat structures like villages fill the view instead of floating in it. It
     * goes by the blocks themselves rather than the bounding box, whose corners are often empty, and
     * keeps the solid part of the ground in view too.
     */
    private void fitToView() {
        float[] points = fitPointsWithGround();
        float keepYaw = yaw;
        float low = 2f;
        float high = homeDistance;
        // The bounding sphere only just fits at the home distance, so it may need to go further.
        for (int i = 0; i < 8 && !fitsAt(points, keepYaw, high); i++) {
            high *= 1.25f;
        }
        for (int i = 0; i < 24; i++) {
            float mid = (low + high) / 2f;
            if (fitsAt(points, keepYaw, mid)) {
                high = mid;
            } else {
                low = mid;
            }
        }
        yaw = keepYaw;
        distance = high;
        homeDistance = Math.max(homeDistance, high);
        updateMatrices();
    }

    /** Whether every point stays inside the middle of the view from {@code at}, all the way round. */
    private boolean fitsAt(float[] points, float startYaw, float at) {
        Vector4f v = new Vector4f();
        distance = at;
        for (int step = 0; step < FIT_ANGLES; step++) {
            yaw = startYaw + step * 360f / FIT_ANGLES;
            updateMatrices();
            Matrix4f combined = new Matrix4f(projection).mul(viewMatrix);
            for (int p = 0; p < points.length; p += 3) {
                combined.transform(v.set(points[p], points[p + 1], points[p + 2], 1f));
                if (v.w() <= 0 || Math.abs(v.x() / v.w()) > FIT_EDGE || Math.abs(v.y() / v.w()) > FIT_EDGE) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The structure's fit points, plus the corners of the solid part of the ground when it shows. */
    private float[] fitPointsWithGround() {
        float[] points = view.fitPoints();
        if (groundY < 0 || groundY >= view.sliceY()) {
            return points;
        }
        float sx = view.size().getX(), sz = view.size().getZ();
        float[] ground = {0, groundY, 0, sx, groundY, 0, 0, groundY, sz, sx, groundY, sz};
        float[] all = Arrays.copyOf(points, points.length + ground.length);
        System.arraycopy(ground, 0, all, points.length, ground.length);
        return all;
    }

    public void rotate(double dx, double dy) {
        glide = null;
        yaw += (float) dx * 0.6f;
        pitch = Math.max(-89f, Math.min(89f, pitch + (float) dy * 0.6f));
    }

    public void spin(float degrees) {
        yaw += degrees;
    }

    public void pan(double dx, double dy) {
        glide = null;
        float scale = distance * (float) Math.tan(Math.toRadians(FOV / 2f)) * 2f / Math.max(1, height);
        Matrix4f inverse = new Matrix4f(viewMatrix).invert();
        Vector3f right = inverse.transformDirection(new Vector3f(1, 0, 0)).normalize();
        Vector3f up = inverse.transformDirection(new Vector3f(0, 1, 0)).normalize();
        focus.add(right.mul((float) -dx * scale)).add(up.mul((float) dy * scale));
    }

    public void zoom(double amount) {
        glide = null;
        distance = Math.max(2f, Math.min(homeDistance * 4f, distance * (float) Math.pow(0.88, amount)));
    }

    /**
     * One frame of a glide, at the same pace whatever the frame rate: most of the way in about a fifth
     * of a second. It's over after half a second, as spinning keeps the angle from ever arriving.
     */
    private void glideStep() {
        long now = System.nanoTime();
        float step = 1f - (float) Math.exp(-(now - glidedAt) / 1e9 * 12);
        glidedAt = now;
        yaw += Mth.wrapDegrees(glide.yaw() - yaw) * step;
        pitch += (glide.pitch() - pitch) * step;
        distance += (glide.distance() - distance) * step;
        focus.lerp(new Vector3f(glide.focusX(), glide.focusY(), glide.focusZ()), step);
        if (now - glideStarted > 500_000_000L) {
            pitch = glide.pitch();
            distance = glide.distance();
            focus.set(glide.focusX(), glide.focusY(), glide.focusZ());
            glide = null;
        }
    }

    public boolean contains(double mouseX, double mouseY) {
        return mouseX >= x && mouseY >= y && mouseX < x + width && mouseY < y + height;
    }

    /**
     * Renders into the target and draws it at the given GUI rectangle. {@code outlines} are block
     * positions to draw a box around, and {@code highlight} blocks to tint, or null.
     */
    public void render(GuiGraphics graphics, int x, int y, int width, int height, float partialTick, Collection<BlockPos> outlines,
                       Highlight highlight) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        if (view == null || width <= 0 || height <= 0) {
            return;
        }
        if (needsFit) {
            needsFit = false;
            fitToView();
        }
        if (glide != null) {
            glideStep();
        }
        updateMatrices();

        double scale = minecraft.getWindow().getGuiScale();
        int pixelWidth = Math.max(1, (int) Math.round(width * scale));
        int pixelHeight = Math.max(1, (int) Math.round(height * scale));
        if (target == null) {
            target = newTarget(pixelWidth, pixelHeight);
        } else if (target.width != pixelWidth || target.height != pixelHeight) {
            //? if >=26.1 {
            /*target.resize(pixelWidth, pixelHeight);
            *///?} else {
            target.resize(pixelWidth, pixelHeight, Minecraft.ON_OSX);
            //?}
        }
        long now = System.nanoTime();
        if (mesh.building()) {
            // Building gets up to half as long as the rest of the frame took, so a frame that's
            // already slow, like one full of chests, still leaves the mesh a fair share, without
            // building ever making a frame much more than half as long again.
            long rest = lastRender == 0 ? 0 : now - lastRender - lastBuild;
            long budget = Math.max(MIN_BUILD_NANOS, Math.min(MAX_BUILD_NANOS, rest / 2));
            mesh.buildSome(budget, eye);
            lastBuild = System.nanoTime() - now;
        } else {
            lastBuild = 0;
        }
        lastRender = now;
        drawScene(target, partialTick, outlines, highlight, false);
        blit(graphics);
    }

    private static TextureTarget newTarget(int width, int height) {
        //? if >=26.3 {
        /*return new TextureTarget("Just Enough Structures preview", width, height, GpuFormat.RGBA8_UNORM, GpuFormat.D32_FLOAT);
        *///?} else if >=26.2 {
        /*return new TextureTarget("Just Enough Structures preview", width, height, true, GpuFormat.RGBA8_UNORM);
        *///?} else if >=26.1 {
        /*return new TextureTarget("Just Enough Structures preview", width, height, true);
        *///?} else {
        return new TextureTarget(width, height, true, Minecraft.ON_OSX);
        //?}
    }

    //? if >=26.1 {
    /*/^* With {@code everything}, every block entity and entity is drawn, even ones not drawn before. ^/
    private void drawScene(TextureTarget into, float partialTick, Collection<BlockPos> outlines, Highlight highlight, boolean everything) {
        GpuTextureView color = into.getColorTextureView();
        GpuTextureView depth = into.getDepthTextureView();
        clear(into);
        if (projectionBuffer == null) {
            projectionBuffer = new ProjectionMatrixBuffer("Just Enough Structures preview");
        }
        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(projectionBuffer.getBuffer(projection), ProjectionType.PERSPECTIVE);
        // The camera turns the world in the model-view matrix, as when the game draws the world, so
        // mobs are lit and things that face the camera turn to it as they would there.
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(viewMatrix);
        GpuBufferSlice keepFog = RenderSystem.getShaderFog();
        GpuBufferSlice keepLights = RenderSystem.getShaderLights();
        RenderSystem.setShaderFog(noFog());
        try {
            mesh.draw(color, depth, viewMatrix, eye);
            drawDynamic(color, depth, partialTick, outlines, everything);
            if (highlight != null) {
                highlight.draw(color, depth, viewMatrix, view.sliceY());
            }
        } finally {
            RenderSystem.setShaderFog(keepFog);
            RenderSystem.setShaderLights(keepLights);
            modelView.popMatrix();
            RenderSystem.restoreProjectionMatrix();
        }
    }

    private static GpuBufferSlice noFog() {
        if (noFog == null) {
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer buffer = stack.malloc(FogRenderer.FOG_UBO_SIZE);
                Std140Builder.intoBuffer(buffer).putVec4(0f, 0f, 0f, 0f)
                        .putFloat(Float.MAX_VALUE).putFloat(Float.MAX_VALUE).putFloat(Float.MAX_VALUE)
                        .putFloat(Float.MAX_VALUE).putFloat(Float.MAX_VALUE).putFloat(Float.MAX_VALUE);
                noFog = RenderSystem.getDevice().createBuffer(() -> "Just Enough Structures fog", GpuBuffer.USAGE_UNIFORM, buffer.flip());
            }
        }
        return noFog.slice(0, FogRenderer.FOG_UBO_SIZE);
    }

    // Where the camera is, for things the game turns towards it, like name tags.
    private CameraRenderState cameraState() {
        CameraRenderState camera = new CameraRenderState();
        camera.pos = new Vec3(eye.x(), eye.y(), eye.z());
        camera.blockPos = BlockPos.containing(camera.pos);
        camera.xRot = pitch;
        camera.yRot = yaw;
        camera.orientation = new Quaternionf().rotationX((float) Math.toRadians(pitch)).rotateY((float) Math.toRadians(yaw)).conjugate();
        camera.initialized = true;
        return camera;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void submitBlockEntity(BlockEntity be, int slice, float partialTick, PoseStack pose, SubmitNodeStorage nodes, CameraRenderState camera) {
        BlockPos pos = be.getBlockPos();
        if (pos.getY() >= slice || failed.contains(be)) {
            return;
        }
        BlockEntityRenderDispatcher dispatcher = minecraft.getBlockEntityRenderDispatcher();
        BlockEntityRenderer renderer = dispatcher.getRenderer(be);
        if (renderer == null) {
            return;
        }
        pose.pushPose();
        pose.translate(pos.getX(), pos.getY(), pos.getZ());
        try {
            BlockEntityRenderState state = (BlockEntityRenderState) renderer.createRenderState();
            renderer.extractRenderState(be, state, partialTick, camera.pos, null);
            state.lightCoords = FULL_BRIGHT;
            dispatcher.submit(state, pose, nodes, camera);
        } catch (RuntimeException | LinkageError e) {
            failed.add(be);
            RenderFailures.failed("Drawing a block entity", be.getBlockState().getBlock(), e);
        } finally {
            pose.popPose();
        }
    }

    private void submitEntity(Entity entity, int slice, EntityRenderDispatcher entities, PoseStack pose, SubmitNodeStorage nodes, CameraRenderState camera) {
        if (entity.getY() >= slice || failed.contains(entity)) {
            return;
        }
        try {
            // Always at their current pose. They never tick, so their "last tick" rotations stay
            // at whatever loading left them (0 for a mob's head and body), and blending towards
            // those by a different amount each frame made them shake.
            EntityRenderState state = entities.extractEntity(entity, 1f);
            state.lightCoords = FULL_BRIGHT;
            // A shadow would fall on whatever the real world has at these positions.
            state.shadowPieces.clear();
            entities.submit(state, camera, entity.getX(), entity.getY(), entity.getZ(), pose, nodes);
        } catch (RuntimeException | LinkageError e) {
            failed.add(entity);
            RenderFailures.failed("Drawing an entity", EntityType.getKey(entity.getType()), e);
        }
    }

    private void blit(GuiGraphics graphics) {
        // Drawn into the texture from the bottom up, so read from the top down.
        graphics.blit(target.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST),
                x, y, x + width, y + height, 0f, 1f, 1f, 0f);
    }
    *///?} else {
    /** With {@code everything}, every block entity and entity is drawn, even ones not drawn before. */
    private void drawScene(TextureTarget into, float partialTick, Collection<BlockPos> outlines, Highlight highlight, boolean everything) {
        into.setClearColor(0f, 0f, 0f, 0f);
        into.clear(Minecraft.ON_OSX);
        into.bindWrite(true);

        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(projection, VertexSorting.DISTANCE_TO_ORIGIN);
        //? if >=1.21 {
        /*Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        *///?} else {
        PoseStack modelView = RenderSystem.getModelViewStack();
        modelView.pushPose();
        modelView.setIdentity();
        //?}
        RenderSystem.applyModelViewMatrix();
        float fogStart = RenderSystem.getShaderFogStart();
        RenderSystem.setShaderFogStart(Float.MAX_VALUE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        LightTexture lightTexture = minecraft.gameRenderer.lightTexture();
        lightTexture.turnOnLightLayer();
        try {
            // Fabulous graphics sends see-through blocks and items to the world's own framebuffers and
            // then switches back to the main one, which would take them, and everything drawn after
            // them, out of the preview. Vanilla draws the player in the inventory as Fancy for the same reason.
            RenderSystem.runAsFancy(() -> {
                mesh.draw(viewMatrix, projection, eye);
                drawDynamic(partialTick, outlines, everything);
                if (highlight != null) {
                    highlight.draw(viewMatrix, projection, view.sliceY());
                }
            });
        } finally {
            lightTexture.turnOffLightLayer();
            RenderSystem.setShaderFogStart(fogStart);
            //? if >=1.21 {
            /*modelView.popMatrix();
            *///?} else {
            modelView.popPose();
            //?}
            RenderSystem.applyModelViewMatrix();
            RenderSystem.restoreProjectionMatrix();
            minecraft.getMainRenderTarget().bindWrite(true);
            Lighting.setupFor3DItems();
        }
    }

    private void drawBlockEntity(BlockEntity be, int slice, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        BlockPos pos = be.getBlockPos();
        if (pos.getY() >= slice || failed.contains(be)) {
            return;
        }
        BlockEntityRenderer<BlockEntity> renderer = minecraft.getBlockEntityRenderDispatcher().getRenderer(be);
        if (renderer == null) {
            return;
        }
        pose.pushPose();
        pose.translate(pos.getX(), pos.getY(), pos.getZ());
        try {
            renderer.render(be, partialTick, pose, buffers, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        } catch (RuntimeException | LinkageError e) {
            failed.add(be);
            RenderFailures.failed("Drawing a block entity", be.getBlockState().getBlock(), e);
        }
        pose.popPose();
    }

    private void drawEntity(Entity entity, int slice, EntityRenderDispatcher entities, PoseStack pose, MultiBufferSource buffers) {
        if (entity.getY() >= slice || failed.contains(entity)) {
            return;
        }
        try {
            // Always at their current pose. They never tick, so their "last tick" rotations stay
            // at whatever loading left them (0 for a mob's head and body), and blending towards
            // those by a different amount each frame made them shake.
            entities.render(entity, entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), 1f, pose, buffers, LightTexture.FULL_BRIGHT);
        } catch (RuntimeException | LinkageError e) {
            failed.add(entity);
            RenderFailures.failed("Drawing an entity", EntityType.getKey(entity.getType()), e);
        }
    }
    //?}

    //? if >=26.2 {
    /*// From 26.2 the nearest depth is the greatest, so the depth starts out at the least.
    private static void clear(TextureTarget into) {
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(into.getColorTexture(), new Vector4f(0f, 0f, 0f, 0f), into.getDepthTexture(), 0.0);
    }

    private void drawDynamic(GpuTextureView color, GpuTextureView depth, float partialTick, Collection<BlockPos> outlines, boolean everything) {
        // The camera is in the model-view matrix, so these are drawn where they are in the structure.
        PoseStack pose = new PoseStack();
        minecraft.gameRenderer.lighting().setupFor(Lighting.Entry.LEVEL);
        FeatureRenderDispatcher features = minecraft.gameRenderer.featureRenderDispatcher();
        CameraRenderState camera = cameraState();
        int slice = view.sliceY();

        // Drawing something for the first time can load its textures and models, and doing that for
        // every chest, banner and mob of a big structure in one frame froze the game for a moment, so
        // they come in a few at a time, in the same order every frame.
        long firstDraws = 0;
        int index = 0;
        for (BlockEntity be : view.blockEntities().values()) {
            boolean first = !everything && index++ >= firstDrawn;
            if (first && firstDraws > FIRST_DRAWS_NANOS) {
                break;
            }
            long started = first ? System.nanoTime() : 0;
            submitBlockEntity(be, slice, partialTick, pose, nodes, camera);
            if (first) {
                firstDraws += System.nanoTime() - started;
                firstDrawn++;
            }
        }

        EntityRenderDispatcher entities = minecraft.getEntityRenderDispatcher();
        index = view.blockEntities().size();
        for (Entity entity : view.entities()) {
            boolean first = !everything && index++ >= firstDrawn;
            if (first && firstDraws > FIRST_DRAWS_NANOS) {
                break;
            }
            long started = first ? System.nanoTime() : 0;
            submitEntity(entity, slice, entities, pose, nodes, camera);
            if (first) {
                firstDraws += System.nanoTime() - started;
                firstDrawn++;
            }
        }

        if (groundY >= 0 && groundY < slice) {
            float gy = groundY + 0.002f;
            nodes.submitCustomGeometry(pose, RenderTypes.debugQuads(), (at, quads) -> groundQuads((gx, gz, alpha) ->
                    quads.addVertex(at.pose(), gx, gy, gz).setColor(0.55f, 0.68f, 0.42f, alpha)));
        }

        float width = minecraft.getWindow().getAppropriateLineWidth();
        for (BlockPos pos : outlines) {
            pose.pushPose();
            pose.translate(pos.getX(), pos.getY(), pos.getZ());
            nodes.submitShapeOutline(pose, OUTLINE, RenderTypes.lines(), 0xFFFFFFFF, width, false);
            pose.popPose();
        }
        try {
            renderFeatures(features, color, depth);
        } catch (RuntimeException | LinkageError e) {
            // One of them couldn't be drawn after all. What it left behind goes with the old queue.
            nodes = new SubmitNodeStorage();
            endFrame(features);
            RenderFailures.failed("Drawing block entities and mobs", "a preview", e);
        }
    }

    // A failure while the game's feature drawing was getting a frame ready leaves that frame open, and
    // it won't open another until it's closed, so the world itself could no longer be drawn. The frame
    // isn't reachable any other way, so it's looked up once, and a game where it can't be found says so.
    private static final java.lang.reflect.Field PREPARED_FRAME = preparedFrame();

    private static java.lang.reflect.Field preparedFrame() {
        try {
            java.lang.reflect.Field frame = FeatureRenderDispatcher.class.getDeclaredField("preparedFrame");
            frame.setAccessible(true);
            return frame;
        } catch (ReflectiveOperationException | RuntimeException e) {
            JesLog.warnOnce("prepared-frame", "Couldn't find the frame the game's feature drawing keeps, so a renderer that fails in a preview could stop the world being drawn: {}",
                    e.toString());
            return null;
        }
    }

    private static void endFrame(FeatureRenderDispatcher features) {
        if (PREPARED_FRAME == null) {
            return;
        }
        try {
            if (PREPARED_FRAME.get(features) instanceof FeatureRenderDispatcher.PreparedFrame frame) {
                frame.close();
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            // Already closed: the failure came once it was ready, and drawing it closed it.
        }
    }
    *///?} else if >=26.1 {
    /*private static void clear(TextureTarget into) {
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(into.getColorTexture(), 0, into.getDepthTexture(), 1.0);
    }

    private void drawDynamic(GpuTextureView color, GpuTextureView depth, float partialTick, Collection<BlockPos> outlines, boolean everything) {
        // The camera is in the model-view matrix, so these are drawn where they are in the structure.
        PoseStack pose = new PoseStack();
        minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.LEVEL);
        FeatureRenderDispatcher features = minecraft.gameRenderer.getFeatureRenderDispatcher();
        SubmitNodeStorage nodes = features.getSubmitNodeStorage();
        CameraRenderState camera = cameraState();
        int slice = view.sliceY();

        // Drawing something for the first time can load its textures and models, and doing that for
        // every chest, banner and mob of a big structure in one frame froze the game for a moment, so
        // they come in a few at a time, in the same order every frame.
        long firstDraws = 0;
        int index = 0;
        for (BlockEntity be : view.blockEntities().values()) {
            boolean first = !everything && index++ >= firstDrawn;
            if (first && firstDraws > FIRST_DRAWS_NANOS) {
                break;
            }
            long started = first ? System.nanoTime() : 0;
            submitBlockEntity(be, slice, partialTick, pose, nodes, camera);
            if (first) {
                firstDraws += System.nanoTime() - started;
                firstDrawn++;
            }
        }

        EntityRenderDispatcher entities = minecraft.getEntityRenderDispatcher();
        index = view.blockEntities().size();
        for (Entity entity : view.entities()) {
            boolean first = !everything && index++ >= firstDrawn;
            if (first && firstDraws > FIRST_DRAWS_NANOS) {
                break;
            }
            long started = first ? System.nanoTime() : 0;
            submitEntity(entity, slice, entities, pose, nodes, camera);
            if (first) {
                firstDraws += System.nanoTime() - started;
                firstDrawn++;
            }
        }
        // What the game draws itself, like chests and mobs, goes into the preview too.
        GpuTextureView keepColor = RenderSystem.outputColorTextureOverride;
        GpuTextureView keepDepth = RenderSystem.outputDepthTextureOverride;
        RenderSystem.outputColorTextureOverride = color;
        RenderSystem.outputDepthTextureOverride = depth;
        try {
            try {
                features.renderAllFeatures();
            } catch (RuntimeException | LinkageError e) {
                // One of them couldn't be drawn after all. What it left behind mustn't end up in the world.
                features.clearSubmitNodes();
                RenderFailures.failed("Drawing block entities and mobs", "a preview", e);
            }

            MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
            if (groundY >= 0 && groundY < slice) {
                float gy = groundY + 0.002f;
                Matrix4f m = pose.last().pose();
                VertexConsumer quads = buffers.getBuffer(RenderTypes.debugQuads());
                groundQuads((gx, gz, alpha) -> quads.addVertex(m, gx, gy, gz).setColor(0.55f, 0.68f, 0.42f, alpha));
            }

            if (!outlines.isEmpty()) {
                VertexConsumer lines = buffers.getBuffer(RenderTypes.lines());
                float width = minecraft.getWindow().getAppropriateLineWidth();
                for (BlockPos pos : outlines) {
                    ShapeRenderer.renderShape(pose, lines, OUTLINE, pos.getX(), pos.getY(), pos.getZ(), 0xFFFFFFFF, width);
                }
            }
            buffers.endBatch();
        } finally {
            RenderSystem.outputColorTextureOverride = keepColor;
            RenderSystem.outputDepthTextureOverride = keepDepth;
        }
    }
    *///?}

    //? if >=26.3 {
    /*// From 26.3 the game draws them into a pass it's handed.
    private void renderFeatures(FeatureRenderDispatcher features, GpuTextureView color, GpuTextureView depth) {
        try (FeatureRenderDispatcher.PreparedFrame frame = features.prepareFrame(nodes);
             RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                     .createRenderPass(() -> "Just Enough Structures preview", color, Optional.empty(), depth, OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            FeatureRenderDispatcher.renderAllFeatures(pass, frame);
        }
    }
    *///?} else if >=26.2 {
    /*// What the game draws itself goes where it's told to, so into the preview too.
    private void renderFeatures(FeatureRenderDispatcher features, GpuTextureView color, GpuTextureView depth) {
        GpuTextureView keepColor = RenderSystem.outputColorTextureOverride;
        GpuTextureView keepDepth = RenderSystem.outputDepthTextureOverride;
        RenderSystem.outputColorTextureOverride = color;
        RenderSystem.outputDepthTextureOverride = depth;
        try {
            features.renderAllFeatures(nodes);
        } finally {
            RenderSystem.outputColorTextureOverride = keepColor;
            RenderSystem.outputDepthTextureOverride = keepDepth;
        }
    }
    *///?}

    /**
     * Draws the finished structure from the default angle into a new square texture for the list.
     * Returns null while the mesh is still being built.
     */
    public TextureTarget renderThumbnail(int pixels) {
        if (view == null || mesh == null || mesh.building()) {
            return null;
        }
        float keepYaw = yaw, keepPitch = pitch, keepDistance = distance, keepHome = homeDistance;
        Vector3f keepFocus = new Vector3f(focus);
        int keepWidth = width, keepHeight = height;
        TextureTarget thumbnail = newTarget(pixels, pixels);
        try {
            yaw = 225f;
            pitch = 30f;
            focus.set(view.size().getX() / 2f, view.size().getY() / 2f, view.size().getZ() / 2f);
            width = pixels;
            height = pixels;
            fitToView();
            drawScene(thumbnail, 0f, List.of(), null, true);
        } finally {
            yaw = keepYaw;
            pitch = keepPitch;
            distance = keepDistance;
            homeDistance = keepHome;
            focus.set(keepFocus);
            width = keepWidth;
            height = keepHeight;
            updateMatrices();
        }
        return thumbnail;
    }

    //? if <26.1 {
    private void drawDynamic(float partialTick, Collection<BlockPos> outlines, boolean everything) {
        PoseStack pose = new PoseStack();
        //? if >=1.21 {
        /*pose.mulPose(viewMatrix);
        Lighting.setupLevel();
        *///?} else {
        pose.mulPoseMatrix(viewMatrix);
        Lighting.setupLevel(new Matrix4f(viewMatrix));
        //?}
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        int slice = view.sliceY();

        // Drawing something for the first time can load its textures and models, and doing that for
        // every chest, banner and mob of a big structure in one frame froze the game for a moment, so
        // they come in a few at a time, in the same order every frame.
        long firstDraws = 0;
        int index = 0;
        for (BlockEntity be : view.blockEntities().values()) {
            boolean first = !everything && index++ >= firstDrawn;
            if (first && firstDraws > FIRST_DRAWS_NANOS) {
                break;
            }
            long started = first ? System.nanoTime() : 0;
            drawBlockEntity(be, slice, partialTick, pose, buffers);
            if (first) {
                firstDraws += System.nanoTime() - started;
                firstDrawn++;
            }
        }

        EntityRenderDispatcher entities = minecraft.getEntityRenderDispatcher();
        entities.setRenderShadow(false);
        index = view.blockEntities().size();
        for (Entity entity : view.entities()) {
            boolean first = !everything && index++ >= firstDrawn;
            if (first && firstDraws > FIRST_DRAWS_NANOS) {
                break;
            }
            long started = first ? System.nanoTime() : 0;
            drawEntity(entity, slice, entities, pose, buffers);
            if (first) {
                firstDraws += System.nanoTime() - started;
                firstDrawn++;
            }
        }
        entities.setRenderShadow(true);

        if (groundY >= 0 && groundY < slice) {
            float gy = groundY + 0.002f;
            Matrix4f m = pose.last().pose();
            VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
            //? if >=1.21 {
            /*groundQuads((gx, gz, alpha) -> quads.addVertex(m, gx, gy, gz).setColor(0.55f, 0.68f, 0.42f, alpha));
            *///?} else {
            groundQuads((gx, gz, alpha) -> quads.vertex(m, gx, gy, gz).color(0.55f, 0.68f, 0.42f, alpha).endVertex());
            //?}
        }

        if (!outlines.isEmpty()) {
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            for (BlockPos pos : outlines) {
                LevelRenderer.renderLineBox(pose, lines, pos.getX() - 0.002, pos.getY() - 0.002, pos.getZ() - 0.002,
                        pos.getX() + 1.002, pos.getY() + 1.002, pos.getZ() + 1.002, 1f, 1f, 1f, 1f);
            }
        }
        buffers.endBatch();
    }

    private void blit(GuiGraphics graphics) {
        drawTexture(graphics, target.getColorTextureId(), x, y, width, height);
    }

    /** Draws a render target's colour texture into a GUI rectangle. */
    public static void drawTexture(GuiGraphics graphics, int textureId, float x, float y, float width, float height) {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderTexture(0, textureId);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f m = graphics.pose().last().pose();
        //? if >=1.21 {
        /*BufferBuilder builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.addVertex(m, x, y + height, 0).setUv(0f, 0f);
        builder.addVertex(m, x + width, y + height, 0).setUv(1f, 0f);
        builder.addVertex(m, x + width, y, 0).setUv(1f, 1f);
        builder.addVertex(m, x, y, 0).setUv(0f, 1f);
        BufferUploader.drawWithShader(builder.buildOrThrow());
        *///?} else {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
        builder.vertex(m, x, y + height, 0).uv(0f, 0f).endVertex();
        builder.vertex(m, x + width, y + height, 0).uv(1f, 0f).endVertex();
        builder.vertex(m, x + width, y, 0).uv(1f, 1f).endVertex();
        builder.vertex(m, x, y, 0).uv(0f, 1f).endVertex();
        BufferUploader.drawWithShader(builder.end());
        //?}
        RenderSystem.disableBlend();
    }
    //?}

    private void updateMatrices() {
        viewMatrix.identity()
                .translate(0, 0, -distance)
                .rotateX((float) Math.toRadians(pitch))
                .rotateY((float) Math.toRadians(yaw))
                .translate(-focus.x(), -focus.y(), -focus.z());
        new Matrix4f(viewMatrix).invert().transformPosition(eye.set(0, 0, 0));

        // The depth buffer is most precise just past the near plane, so a near plane right at the
        // camera wastes it on empty space. From far out that left too little for water surfaces,
        // which sit a thousandth of a block inside their blocks, and they flickered through them.
        // So it goes just in front of the closest the structure (and its ground plane) can be.
        float near = 0.05f;
        float far = 4000f;
        if (view != null) {
            float sx = view.size().getX(), sy = view.size().getY(), sz = view.size().getZ();
            float margin = Math.max(2f, Math.min(sx, sz) * 0.15f);
            float radius = (float) Math.sqrt((sx + 2 * margin) * (sx + 2 * margin) + sy * sy + (sz + 2 * margin) * (sz + 2 * margin)) / 2f + 2f;
            float fromCentre = eye.distance(sx / 2f, sy / 2f, sz / 2f);
            near = Math.max(0.05f, (fromCentre - radius) * 0.8f);
            far = Math.max(far, fromCentre + radius * 2f);
        }
        float aspect = (float) width / Math.max(1, height);
        //? if >=26.2 {
        /*// From 26.2 the nearest depth is the greatest, so near and far swap places.
        projection.setPerspective((float) Math.toRadians(FOV), aspect, far, near, RenderSystem.getDevice().getDeviceInfo().isZZeroToOne());
        *///?} else {
        projection.setPerspective((float) Math.toRadians(FOV), aspect, near, far);
        //?}
    }

    /** Screen position of a point in structure space, or empty when it's behind the camera. */
    /** Where a point lands on screen, as {x, y, distance from the camera}, or empty if it's behind it. */
    public Optional<float[]> project(double px, double py, double pz) {
        Vector4f v = new Matrix4f(projection).mul(viewMatrix).transform(new Vector4f((float) px, (float) py, (float) pz, 1f));
        if (v.w() <= 0f) {
            return Optional.empty();
        }
        float sx = x + (v.x() / v.w() * 0.5f + 0.5f) * width;
        float sy = y + (1f - (v.y() / v.w() * 0.5f + 0.5f)) * height;
        return Optional.of(new float[]{sx, sy, v.w()});
    }

    /** How tall a block looks at the point the camera turns around. Only zooming changes it. */
    public float pixelsPerBlock() {
        return height / (2f * distance * (float) Math.tan(Math.toRadians(FOV / 2f)));
    }

    /** What's under the mouse: an entity if one is closer than the first block hit. */
    public Optional<Hit> pick(double mouseX, double mouseY) {
        if (view == null || !contains(mouseX, mouseY)) {
            return Optional.empty();
        }
        Matrix4f inverse = new Matrix4f(projection).mul(viewMatrix).invert();
        float ndcX = (float) ((mouseX - x) / width * 2.0 - 1.0);
        float ndcY = (float) (1.0 - (mouseY - y) / height * 2.0);
        //? if >=26.2 {
        /*// From 26.2 the near plane is at the top of the depth range and the far one at the bottom.
        float nearZ = 1f;
        float farZ = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne() ? 0f : -1f;
        *///?} else {
        float nearZ = -1f;
        float farZ = 1f;
        //?}
        Vector4f near = inverse.transform(new Vector4f(ndcX, ndcY, nearZ, 1f));
        Vector4f far = inverse.transform(new Vector4f(ndcX, ndcY, farZ, 1f));
        Vec3 from = new Vec3(near.x() / near.w(), near.y() / near.w(), near.z() / near.w());
        Vec3 to = new Vec3(far.x() / far.w(), far.y() / far.w(), far.z() / far.w());

        BlockHitResult blockHit = BlockGetter.traverseBlocks(from, to, (Object) null, (ctx, pos) -> {
            BlockState state = view.getBlockState(pos);
            if (state.isAir()) {
                return null;
            }
            return view.clipWithInteractionOverride(from, to, pos, state.getShape(view, pos), state);
        }, ctx -> null);
        double blockDistance = blockHit == null ? Double.MAX_VALUE : blockHit.getLocation().distanceToSqr(from);

        Entity nearest = null;
        double entityDistance = blockDistance;
        for (Entity entity : view.entities()) {
            if (entity.getY() >= view.sliceY()) {
                continue;
            }
            Optional<Vec3> clip = entity.getBoundingBox().clip(from, to);
            if (clip.isPresent() && clip.get().distanceToSqr(from) < entityDistance) {
                entityDistance = clip.get().distanceToSqr(from);
                nearest = entity;
            }
        }
        if (nearest != null) {
            return Optional.of(new Hit(nearest.blockPosition(), null, nearest));
        }
        if (blockHit != null) {
            BlockPos pos = blockHit.getBlockPos();
            return Optional.of(new Hit(pos, view.getBlockState(pos), null));
        }
        return Optional.empty();
    }

    @Override
    public void close() {
        if (mesh != null) {
            mesh.close();
            mesh = null;
        }
        if (target != null) {
            target.destroyBuffers();
            target = null;
        }
        //? if >=26.1 {
        /*if (projectionBuffer != null) {
            projectionBuffer.close();
            projectionBuffer = null;
        }
        *///?}
        view = null;
    }

    /** A block ({@code state} set) or an entity ({@code entity} set) under the cursor. */
    public record Hit(BlockPos pos, BlockState state, Entity entity) {
    }
}
