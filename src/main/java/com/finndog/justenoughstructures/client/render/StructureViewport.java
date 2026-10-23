package com.finndog.justenoughstructures.client.render;

import com.finndog.justenoughstructures.JesLog;
import com.google.common.collect.Iterables;
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
import java.util.function.Consumer;
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
import net.minecraft.world.phys.shapes.Shapes;
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
     * How far from the view's centre a fitted structure may reach, as a fraction of the way to the
     * edge, and how many yaw angles are checked (every 15 degrees) so it stays in view while spinning.
     */
    private static final float FIT_EDGE = 0.9f;
    private static final int FIT_ANGLES = 24;
    /** Opacity of the ground under the structure. It fades to zero across a margin around it. */
    private static final float GROUND_ALPHA = 0.35f;
    /** Minimum and maximum time per frame for building the mesh, while there's some left to build. */
    private static final long MIN_BUILD_NANOS = 6_000_000L;
    private static final long MAX_BUILD_NANOS = 25_000_000L;
    /** Time per frame for drawing block entities and entities that haven't been drawn before. */
    private static final long FIRST_DRAWS_NANOS = 4_000_000L;

    private final Minecraft minecraft = Minecraft.getInstance();
    private SnapshotView view;
    private SnapshotMesh mesh;
    private TextureTarget target;
    /**
     * Block entities and entities in this view whose renderer threw, and blocks that failed when asked
     * for a shape. Skipped from then on, as they would throw every frame.
     */
    private final Set<Object> failed = Collections.newSetFromMap(new IdentityHashMap<>());
    /** How many of the view's block entities, then its entities, have been drawn at least once. */
    private int firstDrawn;
    /** When the last frame started, and how long it spent building the mesh. */
    private long lastRender;
    private long lastBuild;

    private float yaw;
    private float pitch;
    private float distance;
    private final Vector3f focus = new Vector3f();
    /** The glide's target camera, or null, and when the glide started and last stepped. */
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
    /*// Preview positions aren't in the real world, so everything is drawn at full brightness,
    // like the blocks.
    private static final int FULL_BRIGHT = 15728880;
    private static final VoxelShape OUTLINE = Shapes.create(-0.002, -0.002, -0.002, 1.002, 1.002, 1.002);
    // Fog that never starts, so a big structure seen from far away isn't hidden by it.
    private static GpuBuffer noFog;
    // Created on the first draw and again after close(): the browser closes its preview when another
    // screen opens over it, and draws it again when it returns.
    private ProjectionMatrixBuffer projectionBuffer;
    *///?}
    //? if >=26.2 {
    /*// The queue of the preview's chests, mobs, ground and outlines waiting to be drawn.
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

    /** Sets the height of the see-through ground plane. A negative {@code localY} hides it. */
    public void setGround(int localY) {
        groundY = localY;
    }

    /** Receives the ground's corners in order, four per quad, each with its opacity. */
    @FunctionalInterface
    private interface GroundCorner {
        void at(float x, float z, float alpha);
    }

    /**
     * Passes the ground to {@code corner} as quads: at {@link #GROUND_ALPHA} under the structure,
     * fading to zero across a margin around it, so the preview's edge doesn't cut it off sharply.
     * Only the part under the structure is kept in view when the camera fits the structure.
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

    /** The block entities, mobs and blocks this preview left out because drawing or querying them failed. Test hook for the scripted scenarios. */
    public List<Object> leftOut() {
        return List.copyOf(failed);
    }

    /** Whether this preview left {@code entity} out after it failed. A left-out entity can't be pointed at. */
    public boolean leftOut(Entity entity) {
        return failed.contains(entity);
    }

    /** How many things this preview has left out. Only grows until the preview shows another view. */
    public int leftOutCount() {
        return failed.size();
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

    /** A camera position, saved so it can be restored later. */
    public record Camera(float yaw, float pitch, float distance, float focusX, float focusY, float focusZ) {
    }

    public Camera camera() {
        return new Camera(yaw, pitch, distance, focus.x, focus.y, focus.z);
    }

    /**
     * Whether a solid block below {@code slice} lies between {@code camera}'s eye and its focus,
     * ignoring the block at {@code target}, which is the thing being looked at. For example, a
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
            return solid(view.rawState(pos.getX(), pos.getY(), pos.getZ()), pos) ? Boolean.TRUE : null;
        }, ctx -> null);
        return hit != null;
    }

    // Before 1.21.2 isSolidRender takes the level, and another mod's block can fail when given the
    // preview's stand-in level. A block that fails counts as not in the way.
    private boolean solid(BlockState state, BlockPos pos) {
        if (failed.contains(state.getBlock())) {
            return false;
        }
        try {
            //? if >=1.21.2 {
            /*return state.isSolidRender();
            *///?} else {
            return state.isSolidRender(view, pos);
            //?}
        } catch (Throwable e) {
            RenderFailures.rethrowIfFatal(e);
            failed.add(state.getBlock());
            RenderFailures.shapeFailed(state.getBlock(), e);
            return false;
        }
    }

    /** Moves the camera smoothly to {@code to} over a fraction of a second. */
    public void glideTo(Camera to) {
        glide = to;
        glideStarted = glidedAt = System.nanoTime();
    }

    /** Restores a position from {@link #camera()}, and skips fitting the structure to the view. */
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
     * Moves the camera as close as it can while the structure stays in view at every angle it spins
     * through, so flat structures like villages fill the view. Uses the blocks, not the bounding box,
     * whose corners are often empty, and keeps the ground under the structure in view too.
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

    /** Whether every point stays within {@link #FIT_EDGE} of the centre at distance {@code at}, at every checked angle. */
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

    /** The structure's fit points, plus the corners of the ground under it when the ground shows. */
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
        float factor = (float) Math.pow(0.88, amount);
        if (glide != null) {
            // During a glide, zooming changes the glide's target distance, so it still ends centred on its target.
            glide = new Camera(glide.yaw(), glide.pitch(), zoomed(glide.distance(), factor), glide.focusX(), glide.focusY(), glide.focusZ());
            return;
        }
        distance = zoomed(distance, factor);
    }

    private float zoomed(float from, float factor) {
        return Math.max(2f, Math.min(homeDistance * 4f, from * factor));
    }

    /** The camera's distance from its focus, or the glide's target distance during a glide. */
    public float targetDistance() {
        return glide != null ? glide.distance() : distance;
    }

    /**
     * Steps a glide by one frame, at the same pace at any frame rate: most of the way in about a fifth
     * of a second. It ends after half a second, since spinning would keep the yaw from ever arriving.
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
            // Build for half the time the rest of the last frame took, kept between 6 and 25 ms.
            // A slow frame, like one full of chests, still gives the mesh a fair share.
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

    /^* Returns false if it failed, which can leave {@code pose} pushed or popped. ^/
    @SuppressWarnings({"unchecked", "rawtypes"})
    private boolean submitBlockEntity(BlockEntity be, int slice, float partialTick, PoseStack pose, SubmitNodeStorage nodes, CameraRenderState camera) {
        BlockPos pos = be.getBlockPos();
        if (pos.getY() >= slice || failed.contains(be)) {
            return true;
        }
        BlockEntityRenderDispatcher dispatcher = minecraft.getBlockEntityRenderDispatcher();
        BlockEntityRenderer renderer = dispatcher.getRenderer(be);
        if (renderer == null) {
            return true;
        }
        pose.pushPose();
        pose.translate(pos.getX(), pos.getY(), pos.getZ());
        try {
            BlockEntityRenderState state = (BlockEntityRenderState) renderer.createRenderState();
            renderer.extractRenderState(be, state, partialTick, camera.pos, null);
            state.lightCoords = FULL_BRIGHT;
            dispatcher.submit(state, pose, nodes, camera);
        } catch (Throwable e) {
            RenderFailures.rethrowIfFatal(e);
            failed.add(be);
            RenderFailures.failed("Drawing a block entity", be.getBlockState().getBlock(), e);
            return false;
        }
        pose.popPose();
        return true;
    }

    /^* Returns false if it failed, which can leave {@code pose} pushed or popped. ^/
    private boolean submitEntity(Entity entity, int slice, EntityRenderDispatcher entities, PoseStack pose, SubmitNodeStorage nodes, CameraRenderState camera) {
        if (entity.getY() >= slice || failed.contains(entity)) {
            return true;
        }
        try {
            // Partial tick 1, so they're drawn at their current pose. They never tick, so their
            // previous-tick rotations keep their loaded values (0 for a mob's head and body), and
            // interpolating towards those by a different amount each frame makes them shake.
            EntityRenderState state = entities.extractEntity(entity, 1f);
            state.lightCoords = FULL_BRIGHT;
            // A shadow would be cast on the real world's blocks at these positions.
            state.shadowPieces.clear();
            entities.submit(state, camera, entity.getX(), entity.getY(), entity.getZ(), pose, nodes);
        } catch (Throwable e) {
            RenderFailures.rethrowIfFatal(e);
            failed.add(entity);
            RenderFailures.failed("Drawing an entity", EntityType.getKey(entity.getType()), e);
            return false;
        }
        return true;
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
            // Fabulous graphics sends see-through blocks and items to the world's framebuffers and
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

    /**
     * Returns false if it failed, which can leave {@code pose} pushed or popped. The frame's buffers
     * are then discarded, since the renderer may have stopped partway through a vertex.
     */
    private boolean drawBlockEntity(BlockEntity be, int slice, float partialTick, PoseStack pose) {
        BlockPos pos = be.getBlockPos();
        if (pos.getY() >= slice || failed.contains(be)) {
            return true;
        }
        BlockEntityRenderer<BlockEntity> renderer = minecraft.getBlockEntityRenderDispatcher().getRenderer(be);
        if (renderer == null) {
            return true;
        }
        pose.pushPose();
        pose.translate(pos.getX(), pos.getY(), pos.getZ());
        try {
            renderer.render(be, partialTick, pose, PreviewBuffers.source(), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        } catch (Throwable e) {
            RenderFailures.rethrowIfFatal(e);
            failed.add(be);
            PreviewBuffers.discard();
            RenderFailures.failed("Drawing a block entity", be.getBlockState().getBlock(), e);
            return false;
        }
        pose.popPose();
        return true;
    }

    /** As {@link #drawBlockEntity}, for a mob or other entity. */
    private boolean drawEntity(Entity entity, int slice, EntityRenderDispatcher entities, PoseStack pose) {
        if (entity.getY() >= slice || failed.contains(entity)) {
            return true;
        }
        try {
            // Partial tick 1, so they're drawn at their current pose. They never tick, so their
            // previous-tick rotations keep their loaded values (0 for a mob's head and body), and
            // interpolating towards those by a different amount each frame makes them shake.
            entities.render(entity, entity.getX(), entity.getY(), entity.getZ(), entity.getYRot(), 1f, pose, PreviewBuffers.source(), LightTexture.FULL_BRIGHT);
        } catch (Throwable e) {
            RenderFailures.rethrowIfFatal(e);
            failed.add(entity);
            PreviewBuffers.discard();
            RenderFailures.failed("Drawing an entity", EntityType.getKey(entity.getType()), e);
            return false;
        }
        return true;
    }
    //?}

    //? if >=26.2 {
    /*// From 26.2 depth is reversed, with the nearest depth the largest, so depth is cleared to 0.
    private static void clear(TextureTarget into) {
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(into.getColorTexture(), new Vector4f(0f, 0f, 0f, 0f), into.getDepthTexture(), 0.0);
    }

    private void drawDynamic(GpuTextureView color, GpuTextureView depth, float partialTick, Collection<BlockPos> outlines, boolean everything) {
        // The camera is in the model-view matrix, so these are drawn where they are in the structure.
        // A renderer that fails can leave its poses pushed or popped, so the rest start from a new stack.
        PoseStack[] partPose = {new PoseStack()};
        minecraft.gameRenderer.lighting().setupFor(Lighting.Entry.LEVEL);
        FeatureRenderDispatcher features = minecraft.gameRenderer.featureRenderDispatcher();
        CameraRenderState camera = cameraState();
        int slice = view.sliceY();
        EntityRenderDispatcher entities = minecraft.getEntityRenderDispatcher();
        drawParts(everything, be -> {
            if (!submitBlockEntity(be, slice, partialTick, partPose[0], nodes, camera)) {
                partPose[0] = new PoseStack();
            }
        }, entity -> {
            if (!submitEntity(entity, slice, entities, partPose[0], nodes, camera)) {
                partPose[0] = new PoseStack();
            }
        });

        PoseStack pose = new PoseStack();
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
        } catch (Throwable e) {
            RenderFailures.rethrowIfFatal(e);
            // A part failed while drawing. Start a new queue, so what it left in the old one is dropped.
            nodes = new SubmitNodeStorage();
            // Drop the half-written vertex first, or closing the frame could fail on it and leave it open.
            dropSpoiltVertex(features);
            endFrame(features);
            RenderFailures.failed("Drawing block entities and mobs", "a preview", e);
        }
    }

    // The feature dispatcher's staged vertex buffer, which it keeps between frames. A renderer that
    // stops partway through a vertex leaves it there, and every later frame, the world's included,
    // would fail on it. The fields are private, so they're read by reflection.
    private static final java.lang.reflect.Field STAGED = field(FeatureRenderDispatcher.class, "stagedVertexBuffer");
    private static final java.lang.reflect.Field STAGED_BUILDER = field(net.minecraft.client.renderer.StagedVertexBuffer.class, "lastVertexBuilder");
    private static final java.lang.reflect.Field STAGED_DRAW = field(net.minecraft.client.renderer.StagedVertexBuffer.class, "lastBuildingDraw");
    private static final java.lang.reflect.Field STAGED_MEMORY = field(net.minecraft.client.renderer.StagedVertexBuffer.class, "stagingBuffer");

    private static java.lang.reflect.Field field(Class<?> owner, String name) {
        try {
            java.lang.reflect.Field field = owner.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            JesLog.warnOnce("staged-vertices", "Couldn't find where the game's feature drawing keeps its vertices, so a renderer that fails part way through one in a preview could end the game: {}",
                    e.toString());
            return null;
        }
    }

    /^*
     * Resets the staged vertex buffer after a preview's frame failed. Nothing in it was going to be
     * drawn, and it may end partway through a vertex. The memory it wrote is released too, or it would
     * be drawn with the next batch.
     ^/
    private static void dropSpoiltVertex(FeatureRenderDispatcher features) {
        if (STAGED == null || STAGED_BUILDER == null || STAGED_DRAW == null || STAGED_MEMORY == null) {
            return;
        }
        try {
            Object staged = STAGED.get(features);
            if (STAGED_BUILDER.get(staged) == null) {
                return;
            }
            STAGED_BUILDER.set(staged, null);
            STAGED_DRAW.set(staged, null);
            com.mojang.blaze3d.vertex.ByteBufferBuilder.Result left = ((com.mojang.blaze3d.vertex.ByteBufferBuilder) STAGED_MEMORY.get(staged)).build();
            if (left != null) {
                left.close();
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            JesLog.warnOnce("staged-vertices-drop", "Couldn't let go of what a failed renderer left in the game's feature drawing: {}", e.toString());
        }
    }

    // A failure while the feature dispatcher prepares a frame leaves that frame open, and it won't
    // prepare another until it's closed, so the world could no longer be drawn. The field is private,
    // so it's read by reflection.
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
            // Already closed: the failure came after the frame was ready, and drawing closed it.
        }
    }
    *///?} else if >=26.1 {
    /*private static void clear(TextureTarget into) {
        RenderSystem.getDevice().createCommandEncoder().clearColorAndDepthTextures(into.getColorTexture(), 0, into.getDepthTexture(), 1.0);
    }

    private void drawDynamic(GpuTextureView color, GpuTextureView depth, float partialTick, Collection<BlockPos> outlines, boolean everything) {
        // The camera is in the model-view matrix, so these are drawn where they are in the structure.
        // A renderer that fails can leave its poses pushed or popped, so the rest start from a new stack.
        PoseStack[] partPose = {new PoseStack()};
        minecraft.gameRenderer.getLighting().setupFor(Lighting.Entry.LEVEL);
        FeatureRenderDispatcher features = minecraft.gameRenderer.getFeatureRenderDispatcher();
        SubmitNodeStorage nodes = features.getSubmitNodeStorage();
        CameraRenderState camera = cameraState();
        int slice = view.sliceY();
        EntityRenderDispatcher entities = minecraft.getEntityRenderDispatcher();
        drawParts(everything, be -> {
            if (!submitBlockEntity(be, slice, partialTick, partPose[0], nodes, camera)) {
                partPose[0] = new PoseStack();
            }
        }, entity -> {
            if (!submitEntity(entity, slice, entities, partPose[0], nodes, camera)) {
                partPose[0] = new PoseStack();
            }
        });
        // Send what the game draws, like chests and mobs, into the preview's textures.
        GpuTextureView keepColor = RenderSystem.outputColorTextureOverride;
        GpuTextureView keepDepth = RenderSystem.outputDepthTextureOverride;
        RenderSystem.outputColorTextureOverride = color;
        RenderSystem.outputDepthTextureOverride = depth;
        try {
            try {
                features.renderAllFeatures();
            } catch (Throwable e) {
                RenderFailures.rethrowIfFatal(e);
                // A part failed while drawing. Clear what it left, so it isn't drawn in the world: queued
                // parts not drawn yet, and half a vertex in the game's buffers.
                features.clearSubmitNodes();
                dropSpoiltBatches(minecraft.renderBuffers().bufferSource());
                RenderFailures.failed("Drawing block entities and mobs", "a preview", e);
            }

            PoseStack pose = new PoseStack();
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

    // The byte buffers behind the game's buffer source, read by reflection to release what a failed
    // batch wrote.
    private static final java.lang.reflect.Field SHARED_MEMORY = bufferMemory("sharedBuffer");
    private static final java.lang.reflect.Field FIXED_MEMORY = bufferMemory("fixedBuffers");

    private static java.lang.reflect.Field bufferMemory(String name) {
        try {
            java.lang.reflect.Field field = MultiBufferSource.BufferSource.class.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException | RuntimeException e) {
            JesLog.warnOnce("buffer-memory", "Couldn't find the memory under the game's buffers, so a renderer that fails in a preview can leave something drawn wrong for a frame: {}",
                    e.toString());
            return null;
        }
    }

    /^*
     * Flushes the game's buffers after a renderer failed while the game drew a preview's parts. A batch
     * left partway through a vertex fails to draw, which removes it, so endBatch is retried until
     * nothing fails (at most 64 times). What such a batch wrote stays in the byte buffers behind them
     * and would be drawn with the next batch, so that's released too.
     ^/
    @SuppressWarnings("unchecked")
    private static void dropSpoiltBatches(MultiBufferSource.BufferSource buffers) {
        for (int tries = 0; tries < 64; tries++) {
            try {
                buffers.endBatch();
                break;
            } catch (Throwable e) {
                RenderFailures.rethrowIfFatal(e);
            }
        }
        if (SHARED_MEMORY == null || FIXED_MEMORY == null) {
            return;
        }
        try {
            java.util.List<com.mojang.blaze3d.vertex.ByteBufferBuilder> memory =
                    new java.util.ArrayList<>(((java.util.Map<?, com.mojang.blaze3d.vertex.ByteBufferBuilder>) FIXED_MEMORY.get(buffers)).values());
            memory.add((com.mojang.blaze3d.vertex.ByteBufferBuilder) SHARED_MEMORY.get(buffers));
            for (com.mojang.blaze3d.vertex.ByteBufferBuilder written : memory) {
                com.mojang.blaze3d.vertex.ByteBufferBuilder.Result left = written.build();
                if (left != null) {
                    left.close();
                }
            }
        } catch (ReflectiveOperationException | RuntimeException e) {
            JesLog.warnOnce("buffer-memory-read", "Couldn't let go of what a failed renderer left in the game's buffers: {}", e.toString());
        }
    }
    *///?}

    //? if >=26.3 {
    /*// From 26.3 the game draws the features into a render pass it's given.
    private void renderFeatures(FeatureRenderDispatcher features, GpuTextureView color, GpuTextureView depth) {
        try (FeatureRenderDispatcher.PreparedFrame frame = features.prepareFrame(nodes);
             RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                     .createRenderPass(() -> "Just Enough Structures preview", color, Optional.empty(), depth, OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            FeatureRenderDispatcher.renderAllFeatures(pass, frame);
        }
    }
    *///?} else if >=26.2 {
    /*// Point the game's output textures at the preview while it draws the features.
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
     * Draws the view's block entities with {@code blockEntity}, then its entities with {@code entity}.
     * Drawing something for the first time can load its textures and models, and doing that for every
     * chest, banner and mob of a big structure in one frame stalls the game. So unless
     * {@code everything} is set, new ones are added within {@link #FIRST_DRAWS_NANOS} per frame, in the
     * same order each frame.
     */
    private void drawParts(boolean everything, Consumer<BlockEntity> blockEntity, Consumer<Entity> entity) {
        long firstDraws = 0;
        int index = 0;
        for (Object part : Iterables.concat(view.blockEntities().values(), view.entities())) {
            boolean first = !everything && index++ >= firstDrawn;
            if (first && firstDraws > FIRST_DRAWS_NANOS) {
                return;
            }
            long started = first ? System.nanoTime() : 0;
            if (part instanceof BlockEntity be) {
                blockEntity.accept(be);
            } else {
                entity.accept((Entity) part);
            }
            if (first) {
                firstDraws += System.nanoTime() - started;
                firstDrawn++;
            }
        }
    }

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
        //? if >=1.21 {
        /*Lighting.setupLevel();
        *///?} else {
        Lighting.setupLevel(new Matrix4f(viewMatrix));
        //?}
        // A renderer that fails can leave its poses pushed or popped, so the rest start from a new stack.
        PoseStack[] partPose = {cameraPose()};
        int slice = view.sliceY();
        EntityRenderDispatcher entities = minecraft.getEntityRenderDispatcher();
        drawParts(everything, be -> {
            if (!drawBlockEntity(be, slice, partialTick, partPose[0])) {
                partPose[0] = cameraPose();
            }
        }, entity -> {
            entities.setRenderShadow(false);
            try {
                if (!drawEntity(entity, slice, entities, partPose[0])) {
                    partPose[0] = cameraPose();
                }
            } finally {
                entities.setRenderShadow(true);
            }
        });

        PoseStack pose = cameraPose();
        MultiBufferSource.BufferSource buffers = PreviewBuffers.source();
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
        endBatch(buffers);
        // Some renderers draw into the game's buffers instead of the ones they're given. Flush those
        // into the preview too.
        endBatch(minecraft.renderBuffers().bufferSource());
    }

    /**
     * Draws what's waiting in {@code buffers}. Another mod's render type can fail to be set up or
     * drawn; this frame's block entities and mobs are then dropped.
     */
    private static void endBatch(MultiBufferSource.BufferSource buffers) {
        try {
            buffers.endBatch();
        } catch (Throwable e) {
            RenderFailures.rethrowIfFatal(e);
            PreviewBuffers.discard();
            RenderFailures.failed("Drawing block entities and mobs", "a preview", e);
        }
    }

    /** A pose stack holding the camera's view matrix, for drawing block entities and mobs. */
    private PoseStack cameraPose() {
        PoseStack pose = new PoseStack();
        //? if >=1.21 {
        /*pose.mulPose(viewMatrix);
        *///?} else {
        pose.mulPoseMatrix(viewMatrix);
        //?}
        return pose;
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
        // camera wastes it on empty space. From far away that leaves too little for water surfaces,
        // which sit a thousandth of a block inside their blocks, and they flicker. So the near plane
        // goes just in front of the closest point the structure and its ground can be.
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
        /*// From 26.2 depth is reversed, with the nearest depth the largest, so near and far swap.
        projection.setPerspective((float) Math.toRadians(FOV), aspect, far, near, RenderSystem.getDevice().getDeviceInfo().isZZeroToOne());
        *///?} else {
        projection.setPerspective((float) Math.toRadians(FOV), aspect, near, far);
        //?}
    }

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

    /** On-screen height of one block at the orbit focus, in GUI pixels. Depends on camera distance and preview height. */
    public float pixelsPerBlock() {
        return height / (2f * distance * (float) Math.tan(Math.toRadians(FOV / 2f)));
    }

    /**
     * Returns what's under the mouse: an entity if one is closer than the first block hit. Entities
     * the preview left out are skipped, so the mouse finds what's behind them.
     */
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
            return clip(from, to, pos, state);
        }, ctx -> null);
        double blockDistance = blockHit == null ? Double.MAX_VALUE : blockHit.getLocation().distanceToSqr(from);

        Entity nearest = null;
        double entityDistance = blockDistance;
        for (Entity entity : view.entities()) {
            if (entity.getY() >= view.sliceY() || failed.contains(entity)) {
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

    /**
     * Where the mouse ray meets a block. The block's shape is read with the preview's stand-in level,
     * which another mod's block can fail on. This runs while the screen draws, where a failure would
     * crash the game, so a block that fails is treated as a full cube from then on.
     */
    private BlockHitResult clip(Vec3 from, Vec3 to, BlockPos pos, BlockState state) {
        if (!failed.contains(state.getBlock())) {
            try {
                return view.clipWithInteractionOverride(from, to, pos, state.getShape(view, pos), state);
            } catch (Throwable e) {
                RenderFailures.rethrowIfFatal(e);
                failed.add(state.getBlock());
                RenderFailures.shapeFailed(state.getBlock(), e);
            }
        }
        return Shapes.block().clip(from, to, pos);
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
