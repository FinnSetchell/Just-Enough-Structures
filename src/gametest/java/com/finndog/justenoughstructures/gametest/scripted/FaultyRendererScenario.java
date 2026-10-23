package com.finndog.justenoughstructures.gametest.scripted;

import static com.finndog.justenoughstructures.gametest.scripted.Director.pause;
import static com.finndog.justenoughstructures.gametest.scripted.Director.pressBrowserKey;
import static com.finndog.justenoughstructures.gametest.scripted.Director.run;
import static com.finndog.justenoughstructures.gametest.scripted.Director.shoot;
import static com.finndog.justenoughstructures.gametest.scripted.Director.until;
import static com.finndog.justenoughstructures.gametest.scripted.Screens.browser;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.mojang.blaze3d.vertex.PoseStack;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.joml.Matrix4f;
//? if >=26.1 {
/*import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.phys.Vec3;
*///?} else {
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
//?}

/**
 * A block entity renderer that fails while a preview draws it, as another mod's can. Suspicious
 * sand's renderer is replaced with one that leaves poses pushed (the top one far off) and half a
 * vertex written, then throws or runs out of stack, or one that only leaves the poses, so a wrong
 * pose shows on its own. The desert pyramid is shown for a while: the game must keep going, only the
 * sand may be left out, and no chest may be drawn where the sand's renderer left the pose. Until
 * 26.1 the half vertex lands in the chests' buffer. From 26.1 it's in custom geometry the game draws
 * later, and running out of stack happens during extraction, which the game doesn't wrap.
 */
final class FaultyRendererScenario {
    /** How far off the faulty renderer moves the poses it leaves pushed. */
    private static final float FAR = 100_000f;
    //? if >=26.2 {
    /*private static final BlockEntityType<?> SAND = net.minecraft.world.level.block.entity.BlockEntityTypes.BRUSHABLE_BLOCK;
    private static final BlockEntityType<?> CHEST = net.minecraft.world.level.block.entity.BlockEntityTypes.CHEST;
    *///?} else {
    private static final BlockEntityType<?> SAND = BlockEntityType.BRUSHABLE_BLOCK;
    private static final BlockEntityType<?> CHEST = BlockEntityType.CHEST;
    //?}
    /** Chests the preview has drawn, and how many of them were far off. */
    private static int chestsDrawn;
    private static int chestsFarOff;

    private FaultyRendererScenario() {
    }

    /** How the faulty renderer goes wrong. */
    enum Fault {
        /** Leaves poses pushed and half a vertex written, then throws. */
        HALF_VERTEX,
        /** Runs out of stack, after leaving poses pushed and half a vertex until 26.1. */
        OUT_OF_STACK,
        /** Only leaves poses pushed, then throws, so a pose left wrong shows on its own. */
        POSES
    }

    static Director build(Minecraft mc, Fault fault) {
        Object[] kept = new Object[1];
        JesScreen.startOn(Ids.parse("desert_pyramid"));
        Director d = new Director(mc);
        d.then(run(() -> kept[0] = install(mc, fault)))
                .then(pressBrowserKey())
                .then(until(() -> browser(mc) != null, 40))
                .then(until(() -> browser(mc).idle(), 600))
                // Many frames, each drawing the suspicious sand with the faulty renderer until the preview gives up on it.
                .then(pause(200))
                .then(shoot(switch (fault) {
                    case HALF_VERTEX -> "r01_after_renderer_half_vertex";
                    case OUT_OF_STACK -> "r02_after_renderer_error";
                    case POSES -> "r03_after_renderer_poses";
                }))
                .then(run(() -> {
                    restore(mc, kept[0]);
                    check(mc);
                }));
        return d;
    }

    private static void check(Minecraft mc) {
        List<Object> leftOut = browser(mc).leftOut();
        if (leftOut.isEmpty()) {
            throw new IllegalStateException("The suspicious sand was never left out, so its faulty renderer never ran");
        }
        for (Object part : leftOut) {
            if (!(part instanceof BlockEntity be) || be.getType() != SAND) {
                throw new IllegalStateException("Left out along with the suspicious sand: " + part);
            }
        }
        if (chestsDrawn == 0) {
            throw new IllegalStateException("No chest was drawn");
        }
        if (chestsFarOff > 0) {
            throw new IllegalStateException(chestsFarOff + " of " + chestsDrawn + " chests were drawn where the faulty renderer left the pose");
        }
    }

    /** Throws as the faulty renderer does: a StackOverflowError if error, else an IllegalStateException. */
    private static void fail(boolean error) {
        if (error) {
            throw new StackOverflowError("a renderer that never stops calling itself, standing in for another mod's");
        }
        throw new IllegalStateException("a renderer that fails partway through a vertex, standing in for another mod's");
    }

    /** Pushes poses and leaves them, the top one far off, as a renderer that fails part way through does. */
    private static void leavePosesPushed(PoseStack pose) {
        pose.pushPose();
        pose.translate(FAR, 0f, 0f);
        pose.pushPose();
    }

    private static void note(Matrix4f chest) {
        chestsDrawn++;
        if (Math.abs(chest.m30()) > FAR / 10 || Math.abs(chest.m31()) > FAR / 10 || Math.abs(chest.m32()) > FAR / 10) {
            chestsFarOff++;
        }
    }

    /**
     * Swaps in the faulty renderer for suspicious sand and wraps the chest renderer in a probe. Chests
     * don't get the faulty one: until 26.1 a chest item is drawn with its block entity renderer, so the
     * browser's unguarded icons would call it. Returns the previous renderers, to restore.
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object install(Minecraft mc, Fault fault) {
        chestsDrawn = 0;
        chestsFarOff = 0;
        Object dispatcher = mc.getBlockEntityRenderDispatcher();
        Object before = Reflect.get(dispatcher, "renderers");
        Map<Object, Object> renderers = new HashMap<>((Map<Object, Object>) before);
        renderers.put(SAND, new Faulty(fault));
        renderers.put(CHEST, new Probe((BlockEntityRenderer) renderers.get(CHEST)));
        Reflect.set(dispatcher, "renderers", renderers);
        return before;
    }

    private static void restore(Minecraft mc, Object before) {
        Reflect.set(mc.getBlockEntityRenderDispatcher(), "renderers", before);
    }

    //? if <1.21 {
    private record Faulty(Fault fault) implements BlockEntityRenderer<BlockEntity> {
        @Override
        public void render(BlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            leavePosesPushed(pose);
            if (fault != Fault.POSES) {
                buffers.getBuffer(Sheets.chestSheet()).vertex(pose.last().pose(), 0f, 0f, 0f);
            }
            fail(fault == Fault.OUT_OF_STACK);
        }
    }
    //?} else if <26.1 {
    /*private record Faulty(Fault fault) implements BlockEntityRenderer<BlockEntity> {
        @Override
        public void render(BlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            leavePosesPushed(pose);
            if (fault != Fault.POSES) {
                buffers.getBuffer(Sheets.chestSheet()).addVertex(pose.last().pose(), 0f, 0f, 0f);
            }
            fail(fault == Fault.OUT_OF_STACK);
        }
    }
    *///?} else {
    /*private record Faulty(Fault fault) implements BlockEntityRenderer<BlockEntity, BlockEntityRenderState> {
        @Override
        public BlockEntityRenderState createRenderState() {
            return new BlockEntityRenderState();
        }

        @Override
        public void extractRenderState(BlockEntity be, BlockEntityRenderState state, float partialTick, Vec3 camera,
                                       ModelFeatureRenderer.CrumblingOverlay crumbling) {
            BlockEntityRenderState.extractBase(be, state, crumbling);
            if (fault == Fault.OUT_OF_STACK) {
                fail(true);
            }
        }

        @Override
        public void submit(BlockEntityRenderState state, PoseStack pose, SubmitNodeCollector nodes, CameraRenderState camera) {
            leavePosesPushed(pose);
            if (fault == Fault.HALF_VERTEX) {
                nodes.submitCustomGeometry(pose, RenderTypes.debugQuads(), (at, consumer) -> {
                    consumer.addVertex(at.pose(), 0f, 0f, 0f);
                    fail(false);
                });
            }
            fail(false);
        }
    }
    *///?}

    //? if <26.1 {
    /** Notes where each chest is drawn, then draws it as usual. */
    private record Probe(BlockEntityRenderer<BlockEntity> real) implements BlockEntityRenderer<BlockEntity> {
        @Override
        public void render(BlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            note(pose.last().pose());
            real.render(be, partialTick, pose, buffers, light, overlay);
        }
    }
    //?} else {
    /*/^* Notes where each chest is drawn, then draws it as usual. ^/
    @SuppressWarnings({"unchecked", "rawtypes"})
    private record Probe(BlockEntityRenderer real) implements BlockEntityRenderer<BlockEntity, BlockEntityRenderState> {
        @Override
        public BlockEntityRenderState createRenderState() {
            return (BlockEntityRenderState) real.createRenderState();
        }

        @Override
        public void extractRenderState(BlockEntity be, BlockEntityRenderState state, float partialTick, Vec3 camera,
                                       ModelFeatureRenderer.CrumblingOverlay crumbling) {
            real.extractRenderState(be, state, partialTick, camera, crumbling);
        }

        @Override
        public void submit(BlockEntityRenderState state, PoseStack pose, SubmitNodeCollector nodes, CameraRenderState camera) {
            note(pose.last().pose());
            real.submit(state, pose, nodes, camera);
        }
    }
    *///?}
}
