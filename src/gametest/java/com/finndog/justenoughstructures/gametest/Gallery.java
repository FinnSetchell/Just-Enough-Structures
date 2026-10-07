package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.JustEnoughStructures;
import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.catalog.StructureCatalog;
import com.finndog.justenoughstructures.client.ClientRequests;
import com.finndog.justenoughstructures.client.ClientState;
import com.finndog.justenoughstructures.client.render.StructureViewport;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import org.lwjgl.glfw.GLFW;
//? if >=26.1 {
/*import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.gamerules.GameRules;
*///?} else {
import net.minecraft.world.level.GameRules;
//?}

/**
 * Dev-only screenshot run, the same on every loader: makes a superflat world, opens the browser on
 * each structure in turn and saves a screenshot of each, and of a chest and the Loot tab, a mob or a
 * spawner for a few. Writes a summary and quits. Each loader calls {@link #tick} at the end of every
 * client tick; enabled with {@code -Djes.autoshot=<folder>}.
 */
public final class Gallery {
    private static final List<String> DEFAULT_STRUCTURES = List.of(
            "minecraft:village_plains", "minecraft:desert_pyramid", "minecraft:jungle_pyramid", "minecraft:shipwreck",
            "minecraft:igloo", "minecraft:pillager_outpost", "minecraft:swamp_hut", "minecraft:ocean_ruin_warm",
            "minecraft:ruined_portal", "minecraft:bastion_remnant", "minecraft:fortress", "minecraft:end_city",
            "minecraft:trail_ruins", "minecraft:ancient_city", "minecraft:mansion", "minecraft:stronghold");
    private static final Set<String> OPEN_CHEST = Set.of("minecraft:desert_pyramid", "minecraft:shipwreck", "minecraft:pillager_outpost");
    // Mobs and spawners draw their own little models, which are easy to break and hard to see from afar.
    private static final Set<String> OPEN_MOB = Set.of("minecraft:village_plains", "minecraft:swamp_hut", "minecraft:end_city",
            "minecraft:mansion");
    private static final Set<String> OPEN_SPAWNER = Set.of("minecraft:fortress", "minecraft:stronghold");
    private static final int TIMEOUT_TICKS = 20 * 90;

    private enum Step { START, WAIT_WORLD, WAIT_CATALOG, WAIT_IDLE, SETTLE, REEL, SHOTS, DONE }

    /** One more screenshot of a structure: what to open first, how long to let it settle, and what to close after. */
    private record Shot(String suffix, Runnable open, int settle, Runnable close) {
    }

    private Step step = Step.START;
    private int stepTicks;
    /** How long another screen has kept the browser from opening once in the world, for {@link #closeJoinScreen}. */
    private int blockedTicks;
    private int index;
    private JesScreen screen;
    private final Deque<Shot> shots = new ArrayDeque<>();
    private Shot shot;
    private List<ResourceLocation> structures;
    private final Path out;
    private final boolean hide;
    private final JsonArray summary = new JsonArray();
    /** "*" asks for every structure the browser lists, which is only known once the server sends them. */
    private final boolean everything;
    private CompletableFuture<List<StructureCatalog.Entry>> catalog;
    /** How many structures, spread through the list, also get a chest and the Loot tab, and a turning reel. */
    private final int closeups;
    private final int reelCount;
    private final int reelTicks;
    /** Degrees the reel turns each tick. */
    private final float reelTurn;
    /** With "*", how many structures from each mod to shoot, or every one when 0. */
    private final int perMod;
    private Set<Integer> closeupAt = Set.of();
    private Set<Integer> reelAt = Set.of();
    private int reelFrames;
    private StructureViewport.Camera reelFrom;
    private final Set<ResourceLocation> retried = new HashSet<>();

    private Gallery(Path out) {
        String list = System.getProperty("jes.autoshot.structures", "");
        everything = list.trim().equals("*");
        structures = everything ? new ArrayList<>() : new ArrayList<>((list.isBlank() ? DEFAULT_STRUCTURES : Arrays.asList(list.split(","))).stream()
                .map(String::trim).filter(s -> !s.isEmpty()).map(Ids::parse).toList());
        hide = Boolean.parseBoolean(System.getProperty("jes.autoshot.hidden", "true"));
        closeups = Integer.getInteger("jes.autoshot.closeups", 0);
        reelCount = Integer.getInteger("jes.autoshot.reel.count", 0);
        reelTicks = Integer.getInteger("jes.autoshot.reel", 60);
        reelTurn = Float.parseFloat(System.getProperty("jes.autoshot.reel.turn", "1.5"));
        perMod = Integer.getInteger("jes.autoshot.per.mod", 0);
        this.out = out;
        spread();
    }

    private static boolean lowOnMemory(CaptureResult result) {
        return result.reason() != null && result.reason().getContents() instanceof TranslatableContents t && t.getKey().endsWith("error.low_memory");
    }

    /** Picks which structures get close-ups and a reel, spread evenly through the list. */
    private void spread() {
        closeupAt = spread(structures.size(), closeups);
        reelAt = spread(structures.size(), reelCount);
    }

    private static Set<Integer> spread(int size, int wanted) {
        Set<Integer> at = new HashSet<>();
        for (int k = 0; k < Math.min(size, wanted); k++) {
            at.add((int) ((k + 0.5) * size / Math.min(size, wanted)));
        }
        return at;
    }

    /** With "*", whether the server's said which structures there are yet. */
    private boolean listed() {
        if (!everything || !structures.isEmpty()) {
            return true;
        }
        if (catalog == null) {
            catalog = ClientRequests.catalog();
        }
        if (!catalog.isDone()) {
            return false;
        }
        List<ResourceLocation> all = catalog.isCompletedExceptionally() ? List.of()
                : catalog.join().stream().map(StructureCatalog.Entry::id).toList();
        structures = new ArrayList<>(perMod > 0 ? perMod(all, perMod) : all);
        JustEnoughStructures.LOGGER.info("Gallery: {} structures, of {} listed", structures.size(), all.size());
        spread();
        return true;
    }

    /** Up to {@code each} structures from every mod, spread through that mod's part of the list. */
    private static List<ResourceLocation> perMod(List<ResourceLocation> all, int each) {
        Map<String, List<ResourceLocation>> byMod = new LinkedHashMap<>();
        for (ResourceLocation id : all) {
            byMod.computeIfAbsent(id.getNamespace(), k -> new ArrayList<>()).add(id);
        }
        List<ResourceLocation> out = new ArrayList<>();
        for (List<ResourceLocation> ids : byMod.values()) {
            for (int i : new TreeSet<>(spread(ids.size(), each))) {
                out.add(ids.get(i));
            }
        }
        return out;
    }

    /**
     * Closes a screen a pack opens on joining, like a welcome page or a quest book, once it has kept a
     * run waiting ten seconds in the world, as nothing is going to click it. Takes and returns how
     * long it has been open.
     */
    public static int closeJoinScreen(Minecraft mc, int blockedTicks) {
        if (mc.player == null || mc.level == null || mc.screen == null) {
            return 0;
        }
        if (blockedTicks + 1 > 200) {
            JustEnoughStructures.LOGGER.info("Autoshot closed {} to carry on", mc.screen.getClass().getName());
            mc.setScreen(null);
            return 0;
        }
        return blockedTicks + 1;
    }

    /** The gallery the run's settings ask for, or null when this isn't a screenshot run. */
    public static Gallery fromProperties() {
        String folder = System.getProperty("jes.autoshot");
        return folder == null || folder.isBlank() ? null : new Gallery(Path.of(folder));
    }

    public void tick(Minecraft mc) {
        stepTicks++;
        switch (step) {
            case START -> {
                if (hide && stepTicks == 1) {
                    hideWindow(mc);
                }
                blockedTicks = menuWait(mc, blockedTicks);
                if (canStart(mc, blockedTicks)) {
                    blockedTicks = 0;
                    startWorld(mc, hide, false);
                    go(Step.WAIT_WORLD);
                }
            }
            case WAIT_WORLD -> {
                blockedTicks = closeJoinScreen(mc, blockedTicks);
                if (mc.player != null && mc.level != null && mc.screen == null && stepTicks > 40) {
                    //? if >=26.1 {
                    /*stopTheSun(mc);
                    *///?}
                    screen = new JesScreen();
                    mc.setScreen(screen);
                    go(Step.WAIT_CATALOG);
                }
            }
            case WAIT_CATALOG -> {
                keepScreen(mc);
                if (!listed()) {
                    return;
                }
                if (index >= structures.size()) {
                    finish(mc);
                } else if (screen.select(structures.get(index))) {
                    go(Step.WAIT_IDLE);
                } else if (stepTicks > TIMEOUT_TICKS) {
                    record(structures.get(index), null, "never listed");
                    next();
                }
            }
            case WAIT_IDLE -> {
                keepScreen(mc);
                if (screen.idle()) {
                    go(Step.SETTLE);
                } else if (stepTicks > TIMEOUT_TICKS) {
                    record(structures.get(index), null, "timed out");
                    next();
                }
            }
            case SETTLE -> {
                keepScreen(mc);
                if (stepTicks >= 10) {
                    ResourceLocation id = structures.get(index);
                    shoot(mc, name(id));
                    CaptureResult result = screen.result();
                    record(id, result, null);
                    if (result != null && lowOnMemory(result) && retried.add(id)) {
                        // The server turned it away while short on memory, so it's tried again at the end, once.
                        structures.add(id);
                    }
                    if (result != null && result.succeeded()) {
                        planShots(id, result.snapshot());
                        if (reelAt.contains(index)) {
                            startReel(mc);
                            return;
                        }
                    }
                    nextShot();
                }
            }
            case REEL -> {
                keepScreen(mc);
                reelFrame(mc);
                if (stepTicks >= reelTicks) {
                    ClientState.spin = true;
                    nextShot();
                }
            }
            case SHOTS -> {
                keepScreen(mc);
                if (stepTicks >= shot.settle()) {
                    shoot(mc, name(structures.get(index)) + "_" + shot.suffix());
                    shot.close().run();
                    nextShot();
                }
            }
            case DONE -> {
            }
        }
    }

    /**
     * A few seconds of the structure turning, a frame a tick, into screenshots/reel, numbered on from
     * the last structure's so they make one video. The camera's turned here rather than left to the
     * preview's own spin, which stops whenever the window says the cursor is over it.
     */
    private void startReel(Minecraft mc) {
        new File(mc.gameDirectory, "screenshots/reel").mkdirs();
        ClientState.spin = false;
        reelFrom = viewport().camera();
        go(Step.REEL);
    }

    private void reelFrame(Minecraft mc) {
        StructureViewport.Camera c = reelFrom;
        viewport().setCamera(new StructureViewport.Camera(c.yaw() + stepTicks * reelTurn, c.pitch(), c.distance(), c.focusX(), c.focusY(), c.focusZ()));
        shoot(mc, String.format("reel/f_%05d", reelFrames++));
    }

    private StructureViewport viewport() {
        try {
            Field field = JesScreen.class.getDeclaredField("viewport");
            field.setAccessible(true);
            return (StructureViewport) field.get(screen);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("No preview to turn", e);
        }
    }

    /** The closer looks at a structure, after its first screenshot. */
    private void planShots(ResourceLocation id, StructureSnapshot snapshot) {
        if (OPEN_CHEST.contains(id.toString()) || closeupAt.contains(index)) {
            snapshot.containers().stream().filter(c -> c.lootTable() != null).findFirst().ifPresent(chest -> {
                shots.add(new Shot("chest", () -> screen.openContainer(chest), 30, () -> screen.closeContainer()));
                shots.add(new Shot("loot", () -> screen.showLootTab(), 30, () -> screen.showInfoTab()));
            });
        }
        if (OPEN_MOB.contains(id.toString())) {
            List<Entity> mobs = screen.placedMobs();
            if (!mobs.isEmpty()) {
                // Long enough for the camera to fly over to it.
                shots.add(new Shot("mob", () -> screen.openMobPopup(mobs.get(0)), 40, () -> screen.closeContainer()));
            }
        }
        if (OPEN_SPAWNER.contains(id.toString())) {
            List<StructureSnapshot.Spawner> spawners = snapshot.spawners();
            if (!spawners.isEmpty()) {
                shots.add(new Shot("spawner", () -> screen.openSpawnerPopup(spawners.get(0)), 30, () -> screen.closeContainer()));
            }
        }
    }

    private void nextShot() {
        shot = shots.poll();
        if (shot == null) {
            next();
        } else {
            shot.open().run();
            go(Step.SHOTS);
        }
    }

    private void next() {
        index++;
        go(Step.WAIT_CATALOG);
    }

    private void go(Step next) {
        step = next;
        stepTicks = 0;
    }

    private void keepScreen(Minecraft mc) {
        if (mc.screen != screen) {
            mc.setScreen(screen);
        }
    }

    private static String name(ResourceLocation id) {
        return id.getNamespace() + "_" + id.getPath().replace('/', '_');
    }

    private static void shoot(Minecraft mc, String file) {
        //? if >=26.1 {
        /*Screenshot.grab(mc.gameDirectory, file + ".png", mc.getMainRenderTarget(), 1, message -> {
        });
        *///?} else {
        Screenshot.grab(mc.gameDirectory, file + ".png", mc.getMainRenderTarget(), message -> {
        });
        //?}
    }

    private void record(ResourceLocation id, CaptureResult result, String problem) {
        JsonObject row = new JsonObject();
        row.addProperty("structure", id.toString());
        if (result == null) {
            row.addProperty("ok", false);
            row.addProperty("error", problem);
        } else {
            row.addProperty("ok", result.succeeded());
            row.addProperty("millis", result.millis());
            if (result.succeeded()) {
                StructureSnapshot s = result.snapshot();
                row.addProperty("blocks", s.blockCount());
                row.addProperty("size", s.size().getX() + "x" + s.size().getY() + "x" + s.size().getZ());
                row.addProperty("containers", s.containers().size());
                row.addProperty("entities", s.entities().size());
                row.addProperty("terrain", s.terrain().name());
            } else {
                row.addProperty("error", result.error());
                // What each terrain said, which explains a structure that wouldn't generate anywhere.
                JsonArray attempts = new JsonArray();
                result.attempts().forEach(a -> attempts.add(a.getString()));
                row.add("attempts", attempts);
            }
        }
        summary.add(row);
        // Written as it goes, so a long run that dies part way still says how far it got.
        writeSummary();
    }

    private void writeSummary() {
        try {
            Files.createDirectories(out);
            Files.writeString(out.resolve("summary.json"), new GsonBuilder().setPrettyPrinting().create().toJson(summary), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't write the autoshot summary", e);
        }
    }

    private void finish(Minecraft mc) {
        go(Step.DONE);
        writeSummary();
        mc.stop();
    }

    /** Whether the game has finished starting up, with nothing left loading over the title screen. */
    public static boolean ready(Minecraft mc) {
        // Wait for the loading overlay too: creating the world mid-reload renders chunks before shaders exist.
        return mc.getOverlay() == null && (mc.screen instanceof TitleScreen || mc.screen instanceof AccessibilityOnboardingScreen);
    }

    /**
     * Whether a run can start its world: from the title screen, or after ten seconds of a pack's own
     * screen in front of it, like a welcome page, which nothing is going to click. Takes and returns
     * how long that screen has been there.
     */
    public static int menuWait(Minecraft mc, int ticks) {
        if (ready(mc) || mc.getOverlay() != null || mc.level != null || mc.screen == null) {
            return 0;
        }
        if (ticks == 200) {
            JustEnoughStructures.LOGGER.info("Autoshot starting the world from behind {}", mc.screen.getClass().getName());
        }
        return ticks + 1;
    }

    public static boolean canStart(Minecraft mc, int menuTicks) {
        return ready(mc) || menuTicks > 200;
    }

    //? if >=26.1 {
    /*// Stops the sun moving, so the world behind the screen stays still between shots. From 26.1 a new
    // world's rules can only be changed once it's open.
    private static void stopTheSun(Minecraft mc) {
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            server.execute(() -> server.getGameRules().set(GameRules.ADVANCE_TIME, false, server));
        }
    }
    *///?}

    /**
     * Hides the game's window. Done on the first tick, as well as when the world's made, so a run in a
     * launcher instance barely shows while the game loads, and doesn't get in the way of whoever's at
     * the computer.
     */
    public static void hideWindow(Minecraft mc) {
        //? if >=26.1 {
        /*GLFW.glfwHideWindow(mc.getWindow().handle());
        *///?} else {
        GLFW.glfwHideWindow(mc.getWindow().getWindow());
        //?}
    }

    /** Sets the window up and makes a superflat world; with {@code structures} it gets villages, for trying locate and teleport. */
    public static void startWorld(Minecraft mc, boolean hide, boolean structures) {
        //? if >=26.1 {
        /*if (hide) {
            GLFW.glfwHideWindow(mc.getWindow().handle());
        }
        mc.options.guiScale().set(Integer.getInteger("jes.autoshot.gui", 2));
        mc.resizeGui();
        // The sun is stopped once the world is open, as its rules can't be set before.
        LevelSettings settings = new LevelSettings("JES autoshot", GameType.CREATIVE,
                new LevelSettings.DifficultySettings(Difficulty.PEACEFUL, false, false), true, WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel("jes-autoshot", settings, new WorldOptions(0L, structures, false),
                registries -> registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
        *///?} else {
        if (hide) {
            GLFW.glfwHideWindow(mc.getWindow().getWindow());
        }
        mc.options.guiScale().set(Integer.getInteger("jes.autoshot.gui", 2));
        mc.resizeDisplay();
        // Stop the sun moving, so the world behind the screen stays still between shots.
        GameRules rules = new GameRules();
        rules.getRule(GameRules.RULE_DAYLIGHT).set(false, null);
        LevelSettings settings = new LevelSettings("JES autoshot", GameType.CREATIVE, false, Difficulty.PEACEFUL, true,
                rules, WorldDataConfiguration.DEFAULT);
        //?}
        //? if >=26.1 {
        /*// Made above.
        *///?} else if >=1.21 {
        /*mc.createWorldOpenFlows().createFreshLevel("jes-autoshot", settings, new WorldOptions(0L, structures, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions(), mc.screen);
        *///?} else {
        mc.createWorldOpenFlows().createFreshLevel("jes-autoshot", settings, new WorldOptions(0L, structures, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET).getHolderOrThrow(WorldPresets.FLAT).value().createWorldDimensions());
        //?}
    }
}
