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
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
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
 *
 * <p>With {@code GAME_BUFFERS} (until 26.1, where the game has buffers a renderer can reach) the
 * renderer writes its half vertex into the game's own buffers instead of the ones it's given, as some
 * mods' renderers do. Once the preview has given up on it, the game's buffers must hold nothing it
 * wrote, which the next thing drawn with that render type would otherwise draw too. Then chests are
 * put all round the player and the world drawn for a while without the browser: it must go on.
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
        POSES,
        /** Writes half a vertex into the game's own buffers, not the ones it's given, then throws. */
        GAME_BUFFERS
    }

    /** How many times the faulty renderer has written into the game's buffers. */
    private static int gameBufferWrites;

    static Director build(Minecraft mc, Fault fault) {
        Object[] kept = new Object[1];
        JesScreen.startOn(Ids.parse("desert_pyramid"));
        Director d = new Director(mc);
        if (fault == Fault.GAME_BUFFERS) {
            //? if >=26.1 {
            /*throw new UnsupportedOperationException("from 26.1 the game has no buffers a block entity renderer can reach");
            *///?} else {
            d.then(run(() -> {
                String left = leftInGameBuffers(mc);
                if (!left.isEmpty()) {
                    throw new IllegalStateException("The game's buffers held something before the faulty renderer ran: " + left);
                }
            }));
            //?}
        }
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
                    case GAME_BUFFERS -> "r04_after_renderer_game_buffers";
                }))
                .then(run(() -> {
                    restore(mc, kept[0]);
                    check(mc);
                }));
        if (fault == Fault.GAME_BUFFERS) {
            d.then(run(() -> {
                        if (gameBufferWrites == 0) {
                            throw new IllegalStateException("The faulty renderer never wrote into the game's buffers");
                        }
                        String left = leftInGameBuffers(mc);
                        if (!left.isEmpty()) {
                            throw new IllegalStateException("After the preview gave up on the faulty renderer, the game's buffers still hold what it wrote: " + left);
                        }
                        mc.setScreen(null);
                    }))
                    // Chests all round the player, so the world draws the game's chest buffer, for a while.
                    .then(run(() -> command(mc, "fill ~-4 ~-1 ~-4 ~4 ~3 ~4 minecraft:chest outline")))
                    .then(pause(100))
                    .then(shoot("r05_world_after_game_buffers"))
                    .then(run(() -> command(mc, "fill ~-4 ~-1 ~-4 ~4 ~3 ~4 minecraft:air outline")));
        }
        return d;
    }

    //? if <1.21 {
    /**
     * What's left in the game's buffers between frames, by render type, when nothing should be: a
     * buffer still being written. Read by field type, as the fields' names differ between loaders.
     */
    private static String leftInGameBuffers(Minecraft mc) {
        MultiBufferSource.BufferSource source = mc.renderBuffers().bufferSource();
        java.util.List<String> left = new java.util.ArrayList<>();
        try {
            for (java.lang.reflect.Field field : MultiBufferSource.BufferSource.class.getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(source);
                if (value instanceof com.mojang.blaze3d.vertex.BufferBuilder builder && builder.building()) {
                    left.add("the shared buffer");
                } else if (value instanceof Map<?, ?> buffers) {
                    for (Map.Entry<?, ?> entry : buffers.entrySet()) {
                        if (entry.getValue() instanceof com.mojang.blaze3d.vertex.BufferBuilder builder && builder.building()) {
                            left.add(String.valueOf(entry.getKey()));
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("couldn't read the game's buffers", e);
        }
        return String.join(", ", left);
    }
    //?} else if <26.1 {
    /*/^*
     * What's left in the game's buffers between frames, by render type, when nothing should be: data
     * written but not drawn, which the next batch in that buffer would draw too, and batches still
     * being written. Reading it lets it go, as drawing would. Read by field type, as the fields' names
     * differ between loaders.
     ^/
    private static String leftInGameBuffers(Minecraft mc) {
        MultiBufferSource.BufferSource source = mc.renderBuffers().bufferSource();
        java.util.List<String> left = new java.util.ArrayList<>();
        try {
            for (java.lang.reflect.Field field : MultiBufferSource.BufferSource.class.getDeclaredFields()) {
                field.setAccessible(true);
                Object value = field.get(source);
                if (value instanceof com.mojang.blaze3d.vertex.ByteBufferBuilder memory) {
                    if (undrawn(memory)) {
                        left.add("the shared buffer");
                    }
                } else if (value instanceof Map<?, ?> buffers) {
                    for (Map.Entry<?, ?> entry : buffers.entrySet()) {
                        if (entry.getValue() instanceof com.mojang.blaze3d.vertex.ByteBufferBuilder memory && undrawn(memory)) {
                            left.add(String.valueOf(entry.getKey()));
                        } else if (entry.getValue() instanceof com.mojang.blaze3d.vertex.BufferBuilder) {
                            left.add(entry.getKey() + ", still being written");
                        }
                    }
                }
            }
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("couldn't read the game's buffers", e);
        }
        return String.join(", ", left);
    }

    private static boolean undrawn(com.mojang.blaze3d.vertex.ByteBufferBuilder memory) {
        com.mojang.blaze3d.vertex.ByteBufferBuilder.Result result = memory.build();
        if (result == null) {
            return false;
        }
        result.close();
        return true;
    }
    *///?} else {
    /*/^* From 26.1 the game has no buffers a block entity renderer can reach, so nothing can be left in them. ^/
    private static String leftInGameBuffers(Minecraft mc) {
        return "";
    }
    *///?}

    /** Runs a command as the player, on the server. */
    private static void command(Minecraft mc, String command) {
        net.minecraft.server.MinecraftServer server = mc.getSingleplayerServer();
        java.util.UUID id = mc.player.getUUID();
        server.execute(() -> {
            net.minecraft.server.level.ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                server.getCommands().performPrefixedCommand(player.createCommandSourceStack(), command);
            }
        });
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
        gameBufferWrites = 0;
        Object before = renderers(mc);
        Map<Object, Object> renderers = new HashMap<>((Map<Object, Object>) before);
        renderers.put(SAND, new Faulty(fault));
        renderers.put(CHEST, new Probe((BlockEntityRenderer) renderers.get(CHEST)));
        setRenderers(mc, renderers);
        return before;
    }

    private static void restore(Minecraft mc, Object before) {
        setRenderers(mc, before);
    }

    private static void setRenderers(Minecraft mc, Object renderers) {
        try {
            renderersField().set(mc.getBlockEntityRenderDispatcher(), renderers);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Object renderers(Minecraft mc) {
        try {
            return renderersField().get(mc.getBlockEntityRenderDispatcher());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * The dispatcher's renderer for each kind of block entity, found as its only map: a real game on
     * Fabric, or on Forge 1.20.1, doesn't know the field as "renderers". On every build and loader it's
     * the dispatcher's one map (read with javap on 24 November 2026).
     */
    private static Field renderersField() {
        return Reflect.only(BlockEntityRenderDispatcher.class, Map.class);
    }

    //? if <1.21 {
    private record Faulty(Fault fault) implements BlockEntityRenderer<BlockEntity> {
        @Override
        public void render(BlockEntity be, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
            leavePosesPushed(pose);
            if (fault == Fault.GAME_BUFFERS) {
                gameBufferWrites++;
                Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(Sheets.chestSheet()).vertex(pose.last().pose(), 0f, 0f, 0f);
            } else if (fault != Fault.POSES) {
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
            if (fault == Fault.GAME_BUFFERS) {
                gameBufferWrites++;
                Minecraft.getInstance().renderBuffers().bufferSource().getBuffer(Sheets.chestSheet()).addVertex(pose.last().pose(), 0f, 0f, 0f);
            } else if (fault != Fault.POSES) {
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
