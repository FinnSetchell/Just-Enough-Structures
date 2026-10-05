package com.finndog.justenoughstructures.gametest;

import com.finndog.justenoughstructures.Ids;
import com.finndog.justenoughstructures.capture.CaptureResult;
import com.finndog.justenoughstructures.capture.StructureSnapshot;
import com.finndog.justenoughstructures.client.screen.JesScreen;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.AccessibilityOnboardingScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.registries.Registries;
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

    private enum Step { START, WAIT_WORLD, WAIT_CATALOG, WAIT_IDLE, SETTLE, SHOTS, DONE }

    /** One more screenshot of a structure: what to open first, how long to let it settle, and what to close after. */
    private record Shot(String suffix, Runnable open, int settle, Runnable close) {
    }

    private Step step = Step.START;
    private int stepTicks;
    private int index;
    private JesScreen screen;
    private final Deque<Shot> shots = new ArrayDeque<>();
    private Shot shot;
    private final List<ResourceLocation> structures;
    private final Path out;
    private final boolean hide;
    private final JsonArray summary = new JsonArray();

    private Gallery(Path out) {
        String list = System.getProperty("jes.autoshot.structures", "");
        structures = (list.isBlank() ? DEFAULT_STRUCTURES : Arrays.asList(list.split(","))).stream()
                .map(String::trim).filter(s -> !s.isEmpty()).map(Ids::parse).toList();
        hide = Boolean.parseBoolean(System.getProperty("jes.autoshot.hidden", "true"));
        this.out = out;
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
                if (ready(mc)) {
                    startWorld(mc, hide, false);
                    go(Step.WAIT_WORLD);
                }
            }
            case WAIT_WORLD -> {
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
                    if (result != null && result.succeeded()) {
                        planShots(id, result.snapshot());
                    }
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

    /** The closer looks at a structure, after its first screenshot. */
    private void planShots(ResourceLocation id, StructureSnapshot snapshot) {
        if (OPEN_CHEST.contains(id.toString())) {
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
            }
        }
        summary.add(row);
    }

    private void finish(Minecraft mc) {
        go(Step.DONE);
        try {
            Files.createDirectories(out);
            Files.writeString(out.resolve("summary.json"), new GsonBuilder().setPrettyPrinting().create().toJson(summary), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Couldn't write the autoshot summary", e);
        }
        mc.stop();
    }

    /** Whether the game has finished starting up, with nothing left loading over the title screen. */
    public static boolean ready(Minecraft mc) {
        // Wait for the loading overlay too: creating the world mid-reload renders chunks before shaders exist.
        return mc.getOverlay() == null && (mc.screen instanceof TitleScreen || mc.screen instanceof AccessibilityOnboardingScreen);
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
